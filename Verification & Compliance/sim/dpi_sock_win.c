// Windows-only DPI socket (localhost:9090)
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "svdpi.h"

// remove the MSVC-only pragma; MinGW ignores it anyway
// #pragma comment(lib, "Ws2_32.lib")

static SOCKET srv = INVALID_SOCKET, cli = INVALID_SOCKET;
static int wsa_inited = 0;

static void set_nonblocking(SOCKET s) {
    u_long nb = 1;
    ioctlsocket(s, FIONBIO, &nb);
}

int dpi_sock_init(int listen_port) {
    if (!wsa_inited) {
        WSADATA w;
        if (WSAStartup(MAKEWORD(2,2), &w) != 0) return -1;
        wsa_inited = 1;
    }
    if (srv != INVALID_SOCKET) return 0;

    srv = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (srv == INVALID_SOCKET) return -2;

    SOCKADDR_IN addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port   = htons((u_short)listen_port);
    // use loopback without inet_pton to avoid MinGW headaches
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

    int opt = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, (char*)&opt, sizeof(opt));

    if (bind(srv, (SOCKADDR*)&addr, sizeof(addr)) == SOCKET_ERROR) return -3;
    if (listen(srv, 1) == SOCKET_ERROR) return -4;
    set_nonblocking(srv);
    return 0;
}

static void accept_once() {
    if (cli != INVALID_SOCKET) return;
    cli = accept(srv, NULL, NULL);
    if (cli != INVALID_SOCKET) set_nonblocking(cli);
}

int dpi_sock_poll_rx(const svOpenArrayHandle data, int maxlen) {
    accept_once();
    if (cli == INVALID_SOCKET) return 0;

    char* buf = (char*)svGetArrayPtr(data);
    int n = recv(cli, buf, maxlen, 0);
    if (n == SOCKET_ERROR) {
        int e = WSAGetLastError();
        if (e == WSAEWOULDBLOCK) return 0;
        closesocket(cli); cli = INVALID_SOCKET;
        return 0;
    }
    if (n == 0) { closesocket(cli); cli = INVALID_SOCKET; return 0; }
    return n;
}

int dpi_sock_send_tx(const svOpenArrayHandle data, int len) {
    accept_once();
    if (cli == INVALID_SOCKET) return 0;
    const char* buf = (const char*)svGetArrayPtr(data);
    int sent = send(cli, buf, len, 0);
    if (sent == SOCKET_ERROR) return 0;
    return sent;
}
