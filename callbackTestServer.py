#!/usr/bin/python3

from http.server import *

class CallbackHandler(BaseHTTPRequestHandler):
    def do_POST(s):
        s.send_response(200)
        s.end_headers()
        content_len = int(s.headers['content-length'])
        post_body = s.rfile.read(content_len)
        print(post_body.decode("UTF-8"))

server_address = ('', 5432)
httpd = HTTPServer(server_address, CallbackHandler)
httpd.serve_forever()
