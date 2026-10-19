# load_samples.py — sample_photos/ 의 예시 사진(퍼블릭 도메인·CC0, CREDITS.md 참고)을 관리자 글로 올린다.
# 사용법 (PhotoBlogServer 에서):
#   python manage.py shell -c "exec(open('sample_photos/load_samples.py', encoding='utf-8').read())"
# posts.json 의 date 를 게시일로 쓴다. 같은 제목의 관리자 글이 이미 있으면 새로 만들지 않고 내용·게시일만 맞춘다.
import json
from datetime import datetime, timedelta
from pathlib import Path

from django.contrib.auth.models import User
from django.core.files import File
from django.utils import timezone

from blog.models import Post

PHOTOS = Path('sample_photos')
admin = User.objects.filter(is_superuser=True).order_by('id').first()

for item in json.loads((PHOTOS / 'posts.json').read_text(encoding='utf-8')):
    published = timezone.make_aware(datetime.strptime(item['date'], '%Y-%m-%d %H:%M'))
    post = Post.objects.filter(author=admin, title=item['title']).first()
    created = post is None
    if created:
        post = Post(author=admin, title=item['title'])
        with open(PHOTOS / item['file'], 'rb') as f:
            post.image.save(item['file'], File(f), save=False)
    post.text = item['text']
    post.created_date = published - timedelta(minutes=3)
    post.published_date = published
    post.save()
    print('추가' if created else '갱신', post.id, item['date'], item['title'])
