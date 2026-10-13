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
| [PhotoBlogServer](PhotoBlogServer) | 포토 블로그 웹 + REST API(`/api_root/Post/`), API 검증 엔진·자동 테스트(커버리지 98%), Socket Server |
| [PhotoViewer](PhotoViewer) | 동기화(이미지 목록), 새로운 이미지 게시(Post 에 따른 UI), 검색·상세보기·삭제·서버 선택 |

평가 항목별 캡처는 보고서(.docx)에 정리했다.
