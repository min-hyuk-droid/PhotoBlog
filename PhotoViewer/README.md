# PhotoViewer — 포토 블로그 Android 클라이언트

강의자료 ch05 「포토블로그를 위한 모바일 앱」 — Java, minSdk 24 / targetSdk 35, AGP 8.7.3, Gradle 8.10.2

## 설정

서버 주소와 토큰은 git 에 올리지 않는 `secrets.properties` 에서 읽어 `BuildConfig` 로 들어간다.

```bash
cp secrets.properties.example secrets.properties
# LOCAL_TOKEN / REMOTE_URL / REMOTE_TOKEN 입력
# 토큰 발급(서버): python manage.py drf_create_token <username>
```

## 빌드 / 실행

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Windows 에서 프로젝트 경로에 한글이 있으면 Android Gradle Plugin 이 빌드를 거부하므로
> 영문 경로(예: `C:\dev\build\photoviewer`)로 복사해서 빌드했다.

## 기능

| 기능 | 구현 |
|---|---|
| 동기화 | `onClickDownload` → `CloadImage extends AsyncTask<String, Integer, List<Bitmap>>`: `GET /api_root/Post/` (Token 인증) → JSON Array 파싱 → 이미지 다운로드 → RecyclerView 표시 (강의자료 18~23p 구조 그대로) |
| 새로운 이미지 게시 (Upload, **Post 에 따른 UI 제공**) | `onClickUpload` → 제목(title)·내용(text) 입력 + Photo Picker 로 이미지(image) 선택 → `PutPost extends AsyncTask<String, Void, Void>` (강의자료 19p): `POST /api_root/Post/` multipart → 201 이면 목록 재동기화 |
| *(편의)* 게시물 카드 / 상세보기 | 이미지 아래 제목·작성자·게시일 표시, 탭하면 큰 이미지와 본문을 보여주는 상세 다이얼로그 |
| *(편의)* 검색 | 검색창 → `GET /api_root/Post/?search=키워드` |
| *(편의)* 길게 눌러 삭제 | 확인 다이얼로그 → `DELETE /api_root/Post/{id}/` (서버는 작성자만 허용, 403 안내) |
| *(편의)* 서버 선택 | 로컬(`http://10.0.2.2:8000`) ↔ 클라우드(PythonAnywhere) 스피너, 선택 값 저장 |

## 주요 파일

- [`MainActivity.java`](app/src/main/java/com/example/photoviewer/MainActivity.java) — 동기화/업로드/검색/삭제/서버 선택
- [`ImageAdapter.java`](app/src/main/java/com/example/photoviewer/ImageAdapter.java) — RecyclerView 어댑터
- [`Post.java`](app/src/main/java/com/example/photoviewer/Post.java) — 게시물 JSON 모델
- [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) — 인터넷 권한, 평문(HTTP) 통신 허용 (강의자료 12~13p)
- [`activity_main.xml`](app/src/main/res/layout/activity_main.xml), [`item_image.xml`](app/src/main/res/layout/item_image.xml), [`dialog_upload.xml`](app/src/main/res/layout/dialog_upload.xml)
