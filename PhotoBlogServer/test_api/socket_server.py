# socket_server.py — 강의자료 ch05 24p "Socket Server를 이용한 Client Request Protocol 검증"
# Django 서버 대신 8000 포트에서 대기하다가 Android 앱(PhotoViewer)의 [동기화] 요청 메시지를
# 그대로 request.txt 에 저장하고, 빈 JSON Array 로 응답한다.
# 사용법: (Django runserver 를 끈 상태에서) python test_api/socket_server.py [포트]
import socket
import sys
from datetime import datetime
from pathlib import Path

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
OUT = Path(__file__).resolve().parent / 'request.txt'

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('0.0.0.0', PORT))
    server.listen(1)
    print(f'Socket Server 대기 중 (port {PORT}) ...')
    conn, addr = server.accept()
    with conn:
        conn.settimeout(2)
        data = b''
        try:
            while b'\r\n\r\n' not in data:
                chunk = conn.recv(4096)
                if not chunk:
                    break
                data += chunk
        except socket.timeout:
            pass
        text = data.decode('utf-8', errors='replace')
        print(f'{datetime.now():%Y-%m-%d %H:%M:%S} {addr[0]}:{addr[1]} 에서 수신\n')
        print(text)
        OUT.write_text(text, encoding='utf-8', newline='')
        body = b'[]'
        conn.sendall(b'HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n'
                     b'Content-Length: ' + str(len(body)).encode() + b'\r\n'
                     b'Connection: close\r\n\r\n' + body)
    print(f'수신 메시지를 {OUT} 에 저장했습니다.')
