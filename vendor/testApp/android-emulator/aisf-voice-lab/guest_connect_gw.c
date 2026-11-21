/* Minimal SNI-aware HTTP CONNECT forwarder for Android guest.
 * Listens on 127.0.0.1:LISTEN_PORT, peeks TLS SNI, CONNECTs via PROXY_HOST:PROXY_PORT.
 */
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>

#define LISTEN_PORT 18744
#define PROXY_HOST "10.0.2.2"
#define PROXY_PORT 8888
#define BUF 65536

static void pipe_fd(int a, int b) {
    char buf[BUF];
    fd_set rfds;
    while (1) {
        FD_ZERO(&rfds);
        FD_SET(a, &rfds);
        FD_SET(b, &rfds);
        int m = (a > b ? a : b) + 1;
        if (select(m, &rfds, NULL, NULL, NULL) < 0) break;
        if (FD_ISSET(a, &rfds)) {
            int n = read(a, buf, sizeof buf);
            if (n <= 0) break;
            if (write(b, buf, n) != n) break;
        }
        if (FD_ISSET(b, &rfds)) {
            int n = read(b, buf, sizeof buf);
            if (n <= 0) break;
            if (write(a, buf, n) != n) break;
        }
    }
    close(a);
    close(b);
}

static int parse_sni(const unsigned char *data, int len, char *out, int outlen) {
    if (len < 43 || data[0] != 0x16) return -1;
    const unsigned char *hs = data + 5;
    int hslen = len - 5;
    if (hslen < 4 || hs[0] != 0x01) return -1;
    const unsigned char *body = hs + 4;
    int blen = hslen - 4;
    int p = 2 + 32;
    if (p >= blen) return -1;
    int sid = body[p];
    p += 1 + sid;
    if (p + 2 > blen) return -1;
    int cs = (body[p] << 8) | body[p + 1];
    p += 2 + cs;
    if (p >= blen) return -1;
    int comp = body[p];
    p += 1 + comp;
    if (p + 2 > blen) return -1;
    int elen = (body[p] << 8) | body[p + 1];
    p += 2;
    int end = p + elen;
    if (end > blen) end = blen;
    while (p + 4 <= end) {
        int et = (body[p] << 8) | body[p + 1];
        int ed = (body[p + 2] << 8) | body[p + 3];
        p += 4;
        if (et == 0 && ed >= 5 && p + ed <= end) {
            int q = p + 2;
            q += 1;
            int nlen = (body[q] << 8) | body[q + 1];
            q += 2;
            if (nlen > 0 && nlen < outlen && q + nlen <= p + ed) {
                memcpy(out, body + q, nlen);
                out[nlen] = 0;
                return 0;
            }
        }
        p += ed;
    }
    return -1;
}

static int tcp_connect(const char *host, int port) {
    char pbuf[16];
    snprintf(pbuf, sizeof pbuf, "%d", port);
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(host, pbuf, &hints, &res) != 0 || !res) return -1;
    int fd = socket(res->ai_family, res->ai_socktype, res->ai_protocol);
    if (fd < 0) {
        freeaddrinfo(res);
        return -1;
    }
    if (connect(fd, res->ai_addr, res->ai_addrlen) < 0) {
        close(fd);
        freeaddrinfo(res);
        return -1;
    }
    freeaddrinfo(res);
    return fd;
}

static void handle(int client) {
    unsigned char peek[4096];
    int got = 0;
    while (got < 5) {
        int n = recv(client, peek + got, sizeof(peek) - got, MSG_PEEK);
        if (n <= 0) {
            close(client);
            return;
        }
        got = n;
        if (got < 5) usleep(50000);
    }
    int need = 5 + ((peek[3] << 8) | peek[4]);
    if (need > (int)sizeof(peek)) need = sizeof(peek);
    while (got < need) {
        int n = recv(client, peek, sizeof(peek), MSG_PEEK);
        if (n <= got) break;
        got = n;
    }
    char sni[256];
    const char *host = "auth.dui.ai";
    if (parse_sni(peek, got, sni, sizeof(sni)) == 0) {
        host = sni;
        fprintf(stderr, "SNI %s\n", sni);
    } else {
        fprintf(stderr, "SNI missing, default auth.dui.ai\n");
    }
    int proxy = tcp_connect(PROXY_HOST, PROXY_PORT);
    if (proxy < 0) {
        fprintf(stderr, "proxy connect fail\n");
        close(client);
        return;
    }
    char req[512];
    int rlen = snprintf(req, sizeof(req),
                        "CONNECT %s:443 HTTP/1.1\r\nHost: %s:443\r\n\r\n", host, host);
    if (write(proxy, req, rlen) != rlen) {
        close(proxy);
        close(client);
        return;
    }
    char resp[1024];
    int rgot = 0;
    while (rgot < (int)sizeof(resp) - 1) {
        int n = read(proxy, resp + rgot, sizeof(resp) - 1 - rgot);
        if (n <= 0) break;
        rgot += n;
        resp[rgot] = 0;
        if (strstr(resp, "\r\n\r\n")) break;
    }
    fprintf(stderr, "CONNECT %s %.*s\n", host, rgot > 40 ? 40 : rgot, resp);
    if (!strstr(resp, "200")) {
        close(proxy);
        close(client);
        return;
    }
    pipe_fd(client, proxy);
}

int main(void) {
    int s = socket(AF_INET, SOCK_STREAM, 0);
    int yes = 1;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof yes);
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof addr);
    addr.sin_family = AF_INET;
    addr.sin_port = htons(LISTEN_PORT);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(s, (struct sockaddr *)&addr, sizeof addr) < 0) {
        perror("bind");
        return 1;
    }
    listen(s, 64);
    fprintf(stderr, "READY %d via %s:%d\n", LISTEN_PORT, PROXY_HOST, PROXY_PORT);
    while (1) {
        struct sockaddr_in caddr;
        socklen_t clen = sizeof caddr;
        int c = accept(s, (struct sockaddr *)&caddr, &clen);
        if (c < 0) continue;
        fprintf(stderr, "ACC\n");
        if (fork() == 0) {
            close(s);
            handle(c);
            _exit(0);
        }
        close(c);
    }
}
