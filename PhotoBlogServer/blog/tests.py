"""포토 블로그 Server API 자동 테스트

실행:  python manage.py test blog
커버리지: coverage run manage.py test blog && coverage html  (htmlcov/index.html)
"""
import io
import json
import shutil
import tempfile

from django.contrib.auth.models import User
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import LiveServerTestCase, TestCase, override_settings
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.authtoken.models import Token
from rest_framework.test import APIClient

from . import api_engine
from .models import Post

API_URL = '/api_root/Post/'
MEDIA_ROOT = tempfile.mkdtemp()


def make_image(name='test.jpg', color=(255, 148, 0)):
    buf = io.BytesIO()
    Image.new('RGB', (40, 30), color).save(buf, 'JPEG')
    return SimpleUploadedFile(name, buf.getvalue(), content_type='image/jpeg')


class MediaTestMixin:
    """업로드 이미지는 임시 폴더에 저장하고 테스트가 끝나면 지운다."""

    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(MEDIA_ROOT, ignore_errors=True)


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class PostModelTest(MediaTestMixin, TestCase):
    def test_str_and_publish(self):
        user = User.objects.create_user('writer', password='pw-12345')
        post = Post.objects.create(author=user, title='제목', text='본문')
        self.assertEqual(str(post), '제목')
        self.assertIsNone(post.published_date)
        post.publish()
        self.assertIsNotNone(Post.objects.get(pk=post.pk).published_date)

    def test_default_image(self):
        user = User.objects.create_user('writer', password='pw-12345')
        post = Post.objects.create(author=user, title='t', text='x')
        self.assertEqual(post.image.name, 'images/default_error.png')


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class PostApiTest(MediaTestMixin, TestCase):
    """강의자료 6~7p: GET(목록, JSON Array) / POST(Multipart: Text + Image)"""

    def setUp(self):
        self.user = User.objects.create_user('kiok', password='pw-12345')
        self.other = User.objects.create_user('other', password='pw-12345')
        self.token = Token.objects.create(user=self.user)
        now = timezone.now()
        self.p1 = Post.objects.create(author=self.user, title='서진이', text='서진이가 그림을 그린다',
                                      published_date=now - timezone.timedelta(days=1),
                                      image=make_image('a.jpg'))
        self.p2 = Post.objects.create(author=self.other, title='우주이야기', text='제임스웹이 보내온 성운',
                                      published_date=now, image=make_image('b.jpg'))
        self.client = APIClient()

    def auth(self, keyword='Token'):
        self.client.credentials(HTTP_AUTHORIZATION=f'{keyword} {self.token.key}')

    # ---- GET ----
    def test_get_list_is_json_array(self):
        res = self.client.get(API_URL)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(api_engine.validate_post_list(res.content.decode()), [])
        data = res.json()
        self.assertEqual([p['title'] for p in data], ['우주이야기', '서진이'])  # 최신 글 먼저
        self.assertTrue(data[0]['image'].startswith('http://testserver/media/images/'))
        self.assertEqual(data[1]['author_name'], 'kiok')

    def test_allowed_methods(self):
        res = self.client.options(API_URL)
        for method in ('GET', 'POST', 'HEAD', 'OPTIONS'):
            self.assertIn(method, res['Allow'])

    def test_get_detail(self):
        res = self.client.get(f'{API_URL}{self.p1.pk}/')
        self.assertEqual(res.status_code, 200)
        self.assertEqual(api_engine.validate_post(res.json()), [])

    def test_search(self):
        res = self.client.get(API_URL, {'search': '성운'})
        self.assertEqual([p['title'] for p in res.json()], ['우주이야기'])
        res = self.client.get(API_URL, {'search': '없는단어'})
        self.assertEqual(res.json(), [])

    # ---- POST ----
    def test_post_requires_auth(self):
        res = self.client.post(API_URL, {'title': 't', 'text': 'x', 'image': make_image()},
                               format='multipart')
        self.assertEqual(res.status_code, 401)

    def test_post_with_token_multipart(self):
        for keyword in ('Token', 'JWT'):
            self.auth(keyword)
            res = self.client.post(API_URL, {'title': f'업로드 {keyword}', 'text': '앱에서 올린 글',
                                             'image': make_image()}, format='multipart')
            self.assertEqual(res.status_code, 201, res.content)
            body = res.json()
            self.assertEqual(body['author'], self.user.pk)
            self.assertIsNotNone(body['published_date'])  # 게시일 자동 지정
            self.assertIn('/media/images/', body['image'])
        self.assertEqual(Post.objects.filter(title__startswith='업로드').count(), 2)

    def test_post_keeps_given_published_date(self):
        self.auth()
        res = self.client.post(API_URL, {'title': 't', 'text': 'x', 'image': make_image(),
                                         'published_date': '2024-10-07T18:05:00+09:00'},
                               format='multipart')
        self.assertEqual(res.status_code, 201)
        self.assertTrue(res.json()['published_date'].startswith('2024-10-07T18:05:00'))

    def test_post_validation_error(self):
        self.auth()
        res = self.client.post(API_URL, {'text': '제목 없음'}, format='multipart')
        self.assertEqual(res.status_code, 400)
        self.assertIn('title', res.json())

    def test_token_issue(self):
        res = self.client.post('/api-token-auth/', {'username': 'kiok', 'password': 'pw-12345'})
        self.assertEqual(res.json()['token'], self.token.key)

    # ---- 수정/삭제 권한 (작성자만) ----
    def test_author_can_delete(self):
        self.auth()
        res = self.client.delete(f'{API_URL}{self.p1.pk}/')
        self.assertEqual(res.status_code, 204)
        self.assertFalse(Post.objects.filter(pk=self.p1.pk).exists())

    def test_other_user_cannot_delete_or_edit(self):
        self.auth()
        self.assertEqual(self.client.delete(f'{API_URL}{self.p2.pk}/').status_code, 403)
        self.assertEqual(self.client.patch(f'{API_URL}{self.p2.pk}/', {'title': 'x'}).status_code, 403)
        self.assertTrue(Post.objects.filter(pk=self.p2.pk).exists())

    def test_staff_can_delete_any(self):
        admin = User.objects.create_superuser('admin2', password='pw-12345')
        self.client.force_authenticate(admin)
        self.assertEqual(self.client.delete(f'{API_URL}{self.p2.pk}/').status_code, 204)


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class WebViewTest(MediaTestMixin, TestCase):
    """서버 브라우저 화면(웹 블로그)"""

    def setUp(self):
        self.user = User.objects.create_user('kiok', password='pw-12345')
        self.post = Post.objects.create(author=self.user, title='웹 글', text='본문',
                                        published_date=timezone.now())

    def test_list_and_detail(self):
        res = self.client.get(reverse('post_list'))
        self.assertContains(res, 'Photo blog')
        self.assertContains(res, '웹 글')
        res = self.client.get(reverse('post_detail', kwargs={'pk': self.post.pk}))
        self.assertContains(res, '본문')

    def test_new_requires_login(self):
        res = self.client.get(reverse('post_new'))
        self.assertEqual(res.status_code, 302)
        self.assertIn('/admin/login/', res['Location'])

    def test_new_and_edit(self):
        self.client.force_login(self.user)
        self.assertEqual(self.client.get(reverse('post_new')).status_code, 200)
        res = self.client.post(reverse('post_new'), {'title': '새 글', 'text': '내용',
                                                     'image': make_image()})
        new = Post.objects.get(title='새 글')
        self.assertRedirects(res, reverse('post_detail', kwargs={'pk': new.pk}))
        url = reverse('post_edit', kwargs={'pk': new.pk})
        self.assertEqual(self.client.get(url).status_code, 200)
        self.client.post(url, {'title': '수정된 글', 'text': '내용'})
        self.assertEqual(Post.objects.get(pk=new.pk).title, '수정된 글')

    def test_js_test_page(self):
        self.assertEqual(self.client.get(reverse('js_test')).status_code, 200)


class ApiEngineUnitTest(TestCase):
    """검증 엔진 자체의 동작 (강의자료 9~10p 판단 기준)"""

    RAW = (b'HTTP/1.1 200 OK\r\nAllow: GET, POST, HEAD, OPTIONS\r\n'
           b'Content-Type: application/json\r\n\r\n')

    def test_build_request_matches_telnet_format(self):
        req = api_engine.build_get_request('10.0.2.2', 8000, API_URL, token='abc').decode()
        self.assertTrue(req.startswith('GET /api_root/Post/ HTTP/1.1\r\n'))
        self.assertIn('Authorization: Token abc\r\n', req)
        self.assertIn('Host: 10.0.2.2:8000\r\n', req)
        self.assertTrue(req.endswith('\r\n\r\n'))

    def test_parse_response(self):
        res = api_engine.parse_http_response(self.RAW + '[]'.encode())
        self.assertEqual((res.status, res.reason), (200, 'OK'))
        self.assertEqual(res.headers['content-type'], 'application/json')
        self.assertEqual(res.body, '[]')

    def test_parse_chunked(self):
        raw = (b'HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n'
               b'3\r\n[{}\r\n1\r\n]\r\n0\r\n\r\n')
        self.assertEqual(api_engine.parse_http_response(raw).body, '[{}]')

    def test_parse_errors(self):
        with self.assertRaises(ValueError):
            api_engine.parse_http_response(b'HTTP/1.1 200 OK\r\n')
        with self.assertRaises(ValueError):
            api_engine.parse_http_response(b'garbage\r\n\r\n')

    def test_validate_detects_problems(self):
        self.assertIn('JSON 형식 오류', api_engine.validate_post_list('[{')[0])
        self.assertIn('JSON Array 가 아닙니다', api_engine.validate_post_list('{"count": 1}')[0])
        self.assertIn('JSON Object 가 아닙니다', api_engine.validate_post_list('[1]')[0])
        bad = {'id': 1, 'author': '1', 'title': 't', 'text': 'x',
               'created_date': 'd', 'published_date': None, 'image': '/media/a.jpg'}
        errors = api_engine.validate_post_list(json.dumps([bad]))
        self.assertTrue(any('author 타입 오류' in e for e in errors))
        self.assertTrue(any('절대 URL' in e for e in errors))
        self.assertTrue(any('필드 누락' in e for e in api_engine.validate_post({'id': 1})))

    def test_check_server_requires_http(self):
        with self.assertRaises(ValueError):
            api_engine.check_server('https://example.com')


@override_settings(MEDIA_ROOT=MEDIA_ROOT)
class RawSocketLiveTest(MediaTestMixin, LiveServerTestCase):
    """실제 서버를 띄우고 telnet 과 같은 원시 소켓 요청으로 검증 (강의자료 8~10p)"""

    def setUp(self):
        self.user = User.objects.create_user('kiok', password='pw-12345')
        self.token = Token.objects.create(user=self.user)
        Post.objects.create(author=self.user, title='라이브', text='소켓 테스트',
                            published_date=timezone.now(), image=make_image())

    def test_check_server_ok(self):
        response, errors = api_engine.check_server(self.live_server_url, self.token.key)
        self.assertEqual(errors, [])
        self.assertEqual(response.status, 200)
        self.assertEqual(json.loads(response.body)[0]['title'], '라이브')

    def test_check_server_reports_wrong_endpoint(self):
        host, port = self.server_thread.host, self.server_thread.port
        raw = api_engine.send_raw_request(host, port, '/api_root/nope/')
        self.assertEqual(api_engine.parse_http_response(raw).status, 404)
