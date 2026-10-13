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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ImageAdapter.OnPostClickListener {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    ImageView imgView;
    TextView textView;
    String site_url = "http://10.0.2.2:8000";
    JSONObject post_json;
    String imageUrl = null;
    Bitmap bmImg = null;
    CloadImage taskDownload;
    PutPost taskUpload;

    // 서버 주소/토큰은 git 에 올리지 않는 secrets.properties → BuildConfig 로 주입
    String token = BuildConfig.LOCAL_TOKEN;

    // [편의기능] 서버 선택: 0 = 로컬(에뮬레이터 → PC), 1 = PythonAnywhere 클라우드
    private static final String[] SERVER_NAMES = {"로컬", "클라우드"};
    private static final String[] SERVER_URLS = {BuildConfig.LOCAL_URL, BuildConfig.REMOTE_URL};
    private static final String[] SERVER_TOKENS = {BuildConfig.LOCAL_TOKEN, BuildConfig.REMOTE_TOKEN};
    private static final String PREF_SERVER = "server";
    private SharedPreferences prefs;
    private EditText editSearch;

    // 마지막으로 동기화한 목록 ([편의기능] 상세보기/삭제에서 사용)
    private List<Bitmap> imageList = new ArrayList<>();
    private List<Post> postList = new ArrayList<>();

    // Upload 화면에서 선택한 이미지
    private Uri selectedImageUri;
    private ImageView uploadPreview;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //imgView = (ImageView) findViewById(R.id.imgView);
        textView = (TextView) findViewById(R.id.textView);
        editSearch = findViewById(R.id.editSearch);

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
                    showImages(new ArrayList<>(), new ArrayList<>());
                    textView.setText("서버: " + site_url);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ------------------------------------------------------------------ 동기화 (강의자료 20~23p)

    public void onClickDownload(View v) {
        editSearch.setText("");
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api_root/Post/");
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
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(url);
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        // [편의기능] 이미지와 같은 순서로 게시물 정보(제목·작성자·게시일·id)도 보관
        private final List<Post> loadedPosts = new ArrayList<>();

        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();
            try {
                String apiUrl = urls[0];
                URL urlAPI = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    is.close();

                    String strJson = result.toString();
                    JSONArray aryJson = new JSONArray(strJson);
                    // 배열 내 모든 이미지 다운로드
                    for (int i = 0; i < aryJson.length(); i++) {
                        post_json = (JSONObject) aryJson.get(i);
                        imageUrl = post_json.getString("image");
                        if (!imageUrl.equals("")) {
                            URL myImageUrl = new URL(imageUrl);
                            conn = (HttpURLConnection) myImageUrl.openConnection();
                            InputStream imgStream = conn.getInputStream();
                            Bitmap imageBitmap = BitmapFactory.decodeStream(imgStream);
                            bitmapList.add(imageBitmap); // 이미지 리스트에 추가
                            loadedPosts.add(new Post(post_json));
                            imgStream.close();
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            return bitmapList;
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            if (images.isEmpty()) {
                textView.setText("불러올 이미지가 없습니다.");
            } else {
                textView.setText("이미지 로드 성공!");
            }
            showImages(images, loadedPosts);
        }
    }

    private void showImages(List<Bitmap> images, List<Post> posts) {
        imageList = images;
        postList = posts;
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        ImageAdapter adapter = new ImageAdapter(images, posts, MainActivity.this);
        recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        recyclerView.setAdapter(adapter);
    }

    // ------------------------------------------------------------------ 새로운 이미지 게시 (강의자료 19p)

    /** Upload (Post 에 따른 UI 제공): Post 의 title / text / image 를 입력받아 PutPost 로 게시 */
    public void onClickUpload(View v) {
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
            if (taskUpload != null && taskUpload.getStatus() == AsyncTask.Status.RUNNING) {
                taskUpload.cancel(true);
            }
            taskUpload = new PutPost(selectedImageUri);
            taskUpload.execute(site_url + "/api_root/Post/", title, text);
            dialog.dismiss();
        }));
        dialog.show();

        Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
    }

    /** POST /api_root/Post/ — Multipart : Text + Image (강의자료 7p). params = {url, title, text} */
    private class PutPost extends AsyncTask<String, Void, Void> {
        private static final String BOUNDARY = "----PhotoViewerBoundary7MA4YWxk";
        private final Uri imageUri;
        private int responseCode;
        private String responseBody = "";

        PutPost(Uri imageUri) {
            this.imageUri = imageUri;
        }

        @Override
        protected void onPreExecute() {
            textView.setText("업로드 중...");
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                // 선택한 이미지를 JPEG 로 압축
                InputStream in = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                in.close();
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);

                URL urlAPI = new URL(params[0]);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                    writeField(out, "title", params[1]);
                    writeField(out, "text", params[2]);
                    String fileName = "android_" + System.currentTimeMillis() + ".jpg";
                    out.write(("--" + BOUNDARY + "\r\n"
                            + "Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"\r\n"
                            + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(jpeg.toByteArray());
                    out.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                responseCode = conn.getResponseCode();
                InputStream body = responseCode < 400 ? conn.getInputStream() : conn.getErrorStream();
                if (body != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    body.close();
                    responseBody = result.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
                responseBody = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            return null;
        }

        private void writeField(DataOutputStream out, String name, String value) throws IOException {
            out.write(("--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected void onPostExecute(Void unused) {
            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                String title = "";
                try {
                    title = new JSONObject(responseBody).optString("title");
                } catch (JSONException ignored) {
                }
                Toast.makeText(MainActivity.this, "게시 완료: " + title, Toast.LENGTH_LONG).show();
                onClickDownload(null); // 목록 다시 동기화
            } else {
                textView.setText("업로드 실패 (" + responseCode + ") " + responseBody);
            }
        }
    }

    // ------------------------------------------------------------------ [편의기능] 상세보기 / 삭제

    @Override
    public void onClick(int position) {
        if (position < 0 || position >= postList.size()) {
            return;
        }
        Post post = postList.get(position);
        View view = getLayoutInflater().inflate(R.layout.dialog_detail, null);
        ((ImageView) view.findViewById(R.id.imageDetail)).setImageBitmap(imageList.get(position));
        ((TextView) view.findViewById(R.id.textDetailMeta)).setText(post.meta());
        ((TextView) view.findViewById(R.id.textDetailBody)).setText(post.text);
        new AlertDialog.Builder(this)
                .setTitle(post.title)
                .setView(view)
                .setPositiveButton("닫기", null)
                .show();
    }

    @Override
    public void onLongClick(int position) {
        if (position < 0 || position >= postList.size()) {
            return;
        }
        Post post = postList.get(position);
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
                URL url = new URL(site_url + "/api_root/Post/" + ids[0] + "/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
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
