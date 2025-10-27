extern "C" {
#include "svdpi.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>

static int sock = -1;
static struct sockaddr_in serv_addr;

void socket_init(const char* ip, int port) {
    sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("socket creation failed");
        exit(1);
    }
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(port);
    inet_pton(AF_INET, ip, &serv_addr.sin_addr);

    if (connect(sock, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("connect failed");
        exit(1);
    }
    printf("[DPI] Connected to %s:%d\n", ip, port);
}

void socket_send(const svBitVecVal* data, int len) {
    printf("[DPI] Sending %d bytes\n", len);
    send(sock, data, len, 0);
}

int socket_recv(svBitVecVal* data, int len) {
    return recv(sock, data, len, MSG_DONTWAIT);
}

void socket_close() {
    if (sock >= 0) close(sock);
}
} // extern "C"
