package com.example.photoviewer;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ImageAdapter.OnPostClickListener {

    // [편의기능] 서버 선택: 0 = 로컬(에뮬레이터 → PC), 1 = PythonAnywhere 클라우드
    private static final String[] SERVER_NAMES = {"로컬", "클라우드"};
    private static final String[] SERVER_URLS = {BuildConfig.LOCAL_URL, BuildConfig.REMOTE_URL};
    private static final String[] SERVER_TOKENS = {BuildConfig.LOCAL_TOKEN, BuildConfig.REMOTE_TOKEN};
    private static final String PREF_SERVER = "server";

    TextView textView;
    String site_url = SERVER_URLS[0];
    String token = SERVER_TOKENS[0];
    CloadImage taskDownload;
    PutPost taskUpload;

    private final List<Post> posts = new ArrayList<>();
    private ImageAdapter adapter;
    private EditText editSearch;
    private SharedPreferences prefs;

    // Upload 화면에서 선택한 이미지
    private Uri selectedImageUri;
    private ImageView uploadPreview;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        editSearch = findViewById(R.id.editSearch);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ImageAdapter(posts, this);
        recyclerView.setAdapter(adapter);

        // 이미지 선택 (Android Photo Picker, 저장소 권한 없이 사용 가능)
        pickImage = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                if (uploadPreview != null) {
                    uploadPreview.setImageURI(uri);
                }
            }
        });

        setupServerSpinner();

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                onClickSearch(v);
                return true;
            }
            return false;
        });
    }

    private void setupServerSpinner() {
        prefs = getSharedPreferences("photoviewer", MODE_PRIVATE);
        Spinner spinner = findViewById(R.id.spinnerServer);
        ArrayAdapter<String> serverAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SERVER_NAMES);
        serverAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(serverAdapter);
        spinner.setSelection(prefs.getInt(PREF_SERVER, 0));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (TextUtils.isEmpty(SERVER_URLS[position])) {
                    Toast.makeText(MainActivity.this, "secrets.properties 에 서버 주소가 없습니다.",
                            Toast.LENGTH_SHORT).show();
                    spinner.setSelection(0);
                    return;
                }
                boolean changed = !SERVER_URLS[position].equals(site_url);
                site_url = SERVER_URLS[position];
                token = SERVER_TOKENS[position];
                prefs.edit().putInt(PREF_SERVER, position).apply();
                if (changed) {
                    posts.clear();
                    adapter.notifyDataSetChanged();
                    textView.setText("서버: " + site_url);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ------------------------------------------------------------------ 동기화 (Download)

    public void onClickDownload(View v) {
        editSearch.setText("");
        startDownload(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    // [편의기능] 검색: GET /api_root/Post/?search=키워드
    public void onClickSearch(View v) {
        String keyword = editSearch.getText().toString().trim();
        hideKeyboard();
        String url = site_url + "/api_root/Post/";
        if (!keyword.isEmpty()) {
            url += "?search=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        }
        startDownload(url);
    }

    private void startDownload(String url) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        textView.setText("동기화 중... " + url);
        taskDownload = new CloadImage();
        taskDownload.execute(url);
    }

    private HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (!TextUtils.isEmpty(token)) {
            conn.setRequestProperty("Authorization", "Token " + token);
        }
        conn.setRequestMethod(method);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        return conn;
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        is.close();
        return out.toByteArray();
    }

    /** 화면 폭에 맞게 줄여서 디코딩 (큰 사진으로 인한 메모리 부족 방지) */
    private static Bitmap decodeScaled(byte[] data, int maxSize) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        int sample = 1;
        while (Math.max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSize) {
            sample *= 2;
        }
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Post>> {
        private String error;

        @Override
        protected List<Post> doInBackground(String... urls) {
            List<Post> result = new ArrayList<>();
            try {
                HttpURLConnection conn = open(urls[0], "GET");
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    error = "서버 응답 " + responseCode;
                    return result;
                }
                String strJson = new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
                JSONArray aryJson = new JSONArray(strJson);
                // 배열 내 모든 이미지 다운로드
                for (int i = 0; i < aryJson.length() && !isCancelled(); i++) {
                    Post post = new Post(aryJson.getJSONObject(i));
                    if (!post.imageUrl.isEmpty()) {
                        HttpURLConnection imgConn = (HttpURLConnection) new URL(post.imageUrl).openConnection();
                        imgConn.setConnectTimeout(3000);
                        imgConn.setReadTimeout(5000);
                        if (imgConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            post.bitmap = decodeScaled(readAll(imgConn.getInputStream()), 1080);
                        }
                    }
                    result.add(post); // 이미지 리스트에 추가
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<Post> result) {
            posts.clear();
            posts.addAll(result);
            adapter.notifyDataSetChanged();
            if (error != null) {
                textView.setText("동기화 실패 - " + error);
            } else if (result.isEmpty()) {
                textView.setText("불러올 이미지가 없습니다.");
            } else {
                textView.setText("이미지 로드 성공! (" + result.size() + "개)");
            }
        }
    }

    // ------------------------------------------------------------------ 새로운 이미지 게시 (Upload)

    /** Upload (Post 에 따른 UI 제공): Post 의 title / text / image 를 입력받아 게시 */
    public void onClickUpload(View v) {
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, "업로드에는 토큰이 필요합니다 (secrets.properties)", Toast.LENGTH_LONG).show();
            return;
        }
        View form = getLayoutInflater().inflate(R.layout.dialog_upload, null);
        EditText editTitle = form.findViewById(R.id.editTitle);
        EditText editText = form.findViewById(R.id.editText);
        Button btnPick = form.findViewById(R.id.btnPickImage);
        uploadPreview = form.findViewById(R.id.imagePreview);
        selectedImageUri = null;
        btnPick.setOnClickListener(b -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("새로운 이미지 게시")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("게시", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String title = editTitle.getText().toString().trim();
            String text = editText.getText().toString().trim();
            if (title.isEmpty()) {
                editTitle.setError("제목을 입력하세요");
                return;
            }
            if (selectedImageUri == null) {
                Toast.makeText(this, "이미지를 선택하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            taskUpload = new PutPost(selectedImageUri);
            taskUpload.execute(title, text);
            dialog.dismiss();
        }));
        dialog.show();
        Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
    }

    /** POST /api_root/Post/  (Multipart: Text + Image, 강의자료 7p) */
    private class PutPost extends AsyncTask<String, Void, String> {
        private static final String BOUNDARY = "----PhotoViewerBoundary7MA4YWxk";
        private final Uri imageUri;
        private int responseCode;

        PutPost(Uri imageUri) {
            this.imageUri = imageUri;
        }

        @Override
        protected void onPreExecute() {
            textView.setText("업로드 중...");
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                // 선택한 이미지를 JPEG 로 다시 압축 (최대 1600px)
                byte[] original = readAll(getContentResolver().openInputStream(imageUri));
                Bitmap bitmap = decodeScaled(original, 1600);
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);

                HttpURLConnection conn = open(site_url + "/api_root/Post/", "POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
                try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                    writeField(out, "title", params[0]);
                    writeField(out, "text", params[1]);
                    String fileName = "android_" + System.currentTimeMillis() + ".jpg";
                    out.write(("--" + BOUNDARY + "\r\n"
                            + "Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"\r\n"
                            + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(jpeg.toByteArray());
                    out.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                responseCode = conn.getResponseCode();
                InputStream body = responseCode < 400 ? conn.getInputStream() : conn.getErrorStream();
                return body == null ? "" : new String(readAll(body), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
                return e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        private void writeField(DataOutputStream out, String name, String value) throws IOException {
            out.write(("--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected void onPostExecute(String result) {
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                String title = "";
                try {
                    title = new JSONObject(result).optString("title");
                } catch (JSONException ignored) {
                }
                Toast.makeText(MainActivity.this, "게시 완료: " + title, Toast.LENGTH_LONG).show();
                onClickDownload(null); // 목록 다시 동기화
            } else {
                textView.setText("업로드 실패 (" + responseCode + ") " + result);
            }
        }
    }

    // ------------------------------------------------------------------ [편의기능] 상세보기 / 삭제

    @Override
    public void onClick(Post post) {
        View view = getLayoutInflater().inflate(R.layout.dialog_detail, null);
        ((ImageView) view.findViewById(R.id.imageDetail)).setImageBitmap(post.bitmap);
        ((TextView) view.findViewById(R.id.textDetailMeta)).setText(post.meta());
        ((TextView) view.findViewById(R.id.textDetailBody)).setText(post.text);
        new AlertDialog.Builder(this)
                .setTitle(post.title)
                .setView(view)
                .setPositiveButton("닫기", null)
                .show();
    }

    @Override
    public void onLongClick(Post post) {
        new AlertDialog.Builder(this)
                .setTitle("게시물 삭제")
                .setMessage("'" + post.title + "' 게시물을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (d, w) -> new DeletePost().execute(post.id))
                .show();
    }

    /** DELETE /api_root/Post/{id}/ — 서버는 작성자 본인만 허용 */
    private class DeletePost extends AsyncTask<Integer, Void, Integer> {
        @Override
        protected Integer doInBackground(Integer... ids) {
            try {
                HttpURLConnection conn = open(site_url + "/api_root/Post/" + ids[0] + "/", "DELETE");
                return conn.getResponseCode();
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }

        @Override
        protected void onPostExecute(Integer code) {
            if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                Toast.makeText(MainActivity.this, "삭제했습니다", Toast.LENGTH_SHORT).show();
                onClickDownload(null);
            } else if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                Toast.makeText(MainActivity.this, "작성자만 삭제할 수 있습니다 (403)", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "삭제 실패 (" + code + ")", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
    }
}
