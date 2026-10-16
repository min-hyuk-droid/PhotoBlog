# 모바일/웹서비스 프로젝트 공통평가 01 — 포토 블로그

강의자료 ch05 「모바일 앱으로 사용하는 기본 포토블로그」 구현 및 테스트 결과.

```
ROOT
├─ PhotoBlogServer/   Django Server  (Django 6.1 + Django REST framework)
├─ PhotoViewer/       Android Client (Java, minSdk 24 / targetSdk 35)
└─ 모바일웹서비스 프로젝트_공통평가 01_수행 결과 보고서.docx
```

| 폴더 | 내용 |
|---|---|
| [PhotoBlogServer](PhotoBlogServer) | 포토 블로그 웹 + REST API(`/api_root/Post/`), API 검증 엔진·자동 테스트(커버리지 100%), Socket Server |
| [PhotoViewer](PhotoViewer) | 동기화(이미지 목록), 새로운 이미지 게시(Post 에 따른 UI), 실시간 검색·상세보기·삭제·자동 동기화·당겨서 새로고침·카메라 촬영 게시·서버 선택 |

- 클라우드 서버: https://smh0927.pythonanywhere.com ([배포 기록](PhotoBlogServer/DEPLOY_PYTHONANYWHERE.md))
- 평가 항목별 캡처는 보고서(.docx)에 정리했다.

| ID | 평가 항목 | 구현/증빙 |
|---|---|---|
| 1, 2 | 폴더 구조, GitHub push | 이 레포 |
| 3 | 포토 블로그 – API (POST) | DRF API 화면에서 multipart POST → 201 |
| 4 | API 검증 (2/3) | telnet 원시 HTTP 요청/응답 |
| 5 | API 검증 (3/3) | codebeautify JSON Viewer |
| 6 | API 검증 엔진 자동테스트 | [`api_engine.py`](PhotoBlogServer/blog/api_engine.py), [`tests.py`](PhotoBlogServer/blog/tests.py) — 29 tests, coverage 100% |
| 7 | Socket Server 요청 메시지 | [`socket_server/`](PhotoBlogServer/socket_server) (ch03-02 30p 뼈대) |
| 8–10 | Server/Client 연동 (localhost) | 웹 화면, 앱 시작/동기화 화면 |
| 11 | MainActivity Upload | **Post 에 따른 UI 제공(상)** — 제목·내용·이미지 입력 → multipart POST |
| 12, 13 | PythonAnywhere 동작 | 클라우드 웹 화면, 앱 클라우드 동기화·업로드 |
| 14 | 편의 기능 5개 | 검색(서버 API + 앱 실시간 검색) / 작성자만 수정·삭제 / 상세보기·길게 눌러 삭제 / 자동 동기화·당겨서 새로고침 / 카메라로 찍어서 바로 게시 |
