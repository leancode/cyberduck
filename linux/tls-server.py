#!/usr/bin/env python3
"""HTTPS server for the smoke tests. Answers every request with 405 so a WebDAV client fails quickly.

Usage: tls-server.py <port> <certificate.pem> <key.pem>
Listens on IPv6 and IPv4 so that both addresses of localhost work.
"""
import http.server
import socket
import ssl
import sys


class Handler(http.server.BaseHTTPRequestHandler):
    def respond(self):
        body = b"Not a WebDAV server\n"
        self.send_response(405)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    do_GET = do_HEAD = do_OPTIONS = do_PROPFIND = do_PUT = do_DELETE = do_POST = respond

    def log_message(self, *args):
        pass


class DualStackServer(http.server.ThreadingHTTPServer):
    address_family = socket.AF_INET6

    def server_bind(self):
        self.socket.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 0)
        super().server_bind()


def main():
    port, certificate, key = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    try:
        server = DualStackServer(("::", port), Handler)
    except OSError:
        server = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(certificate, key)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
