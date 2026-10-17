# seed_posts.py — sample_photos/ 의 예시 사진(퍼블릭 도메인·CC0, CREDITS.md 참고)을 게시물로 올린다.
# 사용법:  BLOG_TOKEN=<관리자 토큰> python test_api/seed_posts.py [서버주소]   (기본 http://127.0.0.1:8000)
# 오래된 사진부터 올려서, 목록(최신 글 먼저)에서는 posts.json 순서대로 보인다.
import json
import os
import sys
from pathlib import Path

import requests

HOST = sys.argv[1].rstrip('/') if len(sys.argv) > 1 else 'http://127.0.0.1:8000'
PHOTOS = Path(__file__).resolve().parent.parent / 'sample_photos'
headers = {'Authorization': 'Token ' + os.environ['BLOG_TOKEN'], 'Accept': 'application/json'}

for post in reversed(json.loads((PHOTOS / 'posts.json').read_text(encoding='utf-8'))):
    with open(PHOTOS / post['file'], 'rb') as image:
        res = requests.post(HOST + '/api_root/Post/', headers=headers,
                            data={'title': post['title'], 'text': post['text']},
                            files={'image': (post['file'], image, 'image/jpeg')})
    res.raise_for_status()
    print(res.status_code, res.json()['id'], post['title'])
