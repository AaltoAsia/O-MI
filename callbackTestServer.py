#!/usr/bin/python3

from http.server import *
from xml.dom.minidom import parseString

# Use colors if available
try:
    from colorama import init, Fore, Style
    init()
except ImportError:
    class Noop:
        def __getattr__(s, key):
            return ""
    class Fore(Noop): pass
    class Style(Noop): pass

responseNum = 1

def seperator():
    return Style.BRIGHT +\
    Fore.BLUE + "=" * 20 +\
    Fore.YELLOW + " # {:02} # ".format(responseNum) +\
    Fore.BLUE + "=" * 20 + Style.RESET_ALL

import re
emptyLinePattern = re.compile(u'(?imu)^\s*\n')
def prettyXml(string):
    xml = parseString(string).toprettyxml("  ")
    return emptyLinePattern.sub(u'', xml)
           







# Simple Handler for HTTP POST callbacks
#
class CallbackHandler(BaseHTTPRequestHandler):
    def do_POST(s):
        s.send_response(200)
        s.end_headers()

        content_len = int(s.headers['content-length'])
        post_body = s.rfile.read(content_len)
        msg = post_body.decode("UTF-8")

        print(seperator())
        print(prettyXml(msg))
        print(seperator() + "\n")
        print(Fore.GREEN + Style.BRIGHT)

        global responseNum
        responseNum += 1

server_address = ('', 5432)
httpd = HTTPServer(server_address, CallbackHandler)
httpd.serve_forever()
