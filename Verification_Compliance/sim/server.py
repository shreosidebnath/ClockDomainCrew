import socket, struct

HOST = "127.0.0.1"
PORT = 9000

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(1)
    print(f"Waiting for connection on {HOST}:{PORT}...")
    conn, addr = s.accept()
    print("Connected by", addr)

    try:
        while True:
            data = conn.recv(8)
            if not data:
                print("Connection closed by Verilog.")
                break
            val = int.from_bytes(data, "little")
            print(f"Received 0x{val:016X}")

            # echo back same data + 1
            echo_val = val + 1
            echo = echo_val.to_bytes(8, "little")
            try:
                conn.sendall(echo)
            except BrokenPipeError:
                print("Verilog closed socket, stopping.")
                break

    except KeyboardInterrupt:
        print("Server stopped manually.")
