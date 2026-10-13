# PythonAnywhere 배포 기록

배포 주소: **https://smh0927.pythonanywhere.com** (Beginner 무료 계정, 2026-10-13 배포)

아래는 실제로 수행한 절차다.

## 1. Bash 콘솔 (Consoles → Bash)

```bash
git clone https://github.com/min-hyuk-droid/PhotoBlog.git
cd ~/PhotoBlog/PhotoBlogServer
python3.13 -m venv ~/.virtualenvs/photoblog-venv
source ~/.virtualenvs/photoblog-venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py collectstatic --noinput

# 앱(PhotoViewer)에서 쓸 사용자와 토큰 (비밀번호 로그인은 막아 둠)
python manage.py shell -c "from django.contrib.auth.models import User; from rest_framework.authtoken.models import Token; u,_=User.objects.get_or_create(username='admin', defaults={'is_staff': True, 'is_superuser': True}); u.set_unusable_password(); u.save(); print(Token.objects.get_or_create(user=u)[0].key)"
# 웹 관리자 로그인이 필요하면: python manage.py changepassword admin
```

## 2. Web 탭 → Add a new web app

1. 도메인 화면에서 **Next**
2. **Manual configuration** → **Python 3.13** → **Next**
3. 생성된 웹앱 설정

| 항목 | 값 |
|---|---|
| Source code | `/home/smh0927/PhotoBlog/PhotoBlogServer` |
| Virtualenv | `/home/smh0927/.virtualenvs/photoblog-venv` |
| Static files `/static/` | `/home/smh0927/PhotoBlog/PhotoBlogServer/staticfiles` |
| Static files `/media/` | `/home/smh0927/PhotoBlog/PhotoBlogServer/media` |

## 3. WSGI 설정 파일 (`/var/www/smh0927_pythonanywhere_com_wsgi.py`)

```python
import os, sys
os.environ['DJANGO_DEBUG'] = 'False'                 # 배포 서버는 오류 상세 화면을 노출하지 않음
os.environ['DJANGO_SECRET_KEY'] = '<서버에서 생성한 비밀키>'  # 아래 명령으로 생성, git 에 올리지 않음
path = '/home/smh0927/PhotoBlog/PhotoBlogServer'
if path not in sys.path:
    sys.path.insert(0, path)
os.environ['DJANGO_SETTINGS_MODULE'] = 'mysite.settings'
from django.core.wsgi import get_wsgi_application
application = get_wsgi_application()
```

비밀키는 PythonAnywhere 콘솔에서 바로 만들어 WSGI 파일에만 넣었다.

```bash
python3 -c 'import secrets; print(secrets.token_urlsafe(50))'
```

## 4. Reload → 확인

```bash
curl -i https://smh0927.pythonanywhere.com/api_root/Post/    # 200, Allow: GET, POST, HEAD, OPTIONS
```

`ALLOWED_HOSTS` 에 `.pythonanywhere.com` 이 이미 들어 있어 별도 수정은 필요 없다.

## 코드 갱신

```bash
cd ~/PhotoBlog && git pull
source ~/.virtualenvs/photoblog-venv/bin/activate
cd PhotoBlogServer && python manage.py migrate && python manage.py collectstatic --noinput
# Web 탭에서 Reload
```

> 무료 계정은 한 달에 한 번 Web 탭의 **Run until 1 month from today** 를 눌러야 사이트가 유지된다.
