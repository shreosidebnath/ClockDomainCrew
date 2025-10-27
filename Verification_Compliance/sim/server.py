# server.py
import socket
s = socket.socket()
s.bind(('127.0.0.1', 9000))
s.listen(1)
print("Waiting for connection on 127.0.0.1:9000...")
conn, addr = s.accept()
print("Connected by", addr)
while True:
    data = conn.recv(1024)
    if not data:
        break
    print("Received:", data)
    conn.sendall(data)
conn.close()
