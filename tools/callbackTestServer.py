#!/usr/bin/python3

from http.server import *
from xml.dom.minidom import parseString
import cgi

# Use colors if available (install module colorama)
try:
    from colorama import init, Fore, Style
    init()
except ImportError:
    class Noop:
        def __getattr__(s, key):
            return ""
    Fore = Noop()
    Style = Noop()

# Count responses for convenience
responseNum = 1

# Seperate responses with this line
def seperator():
    return Style.BRIGHT +\
    Fore.BLUE + "=" * 20 +\
    Fore.YELLOW + " # {:02} # ".format(responseNum) +\
    Fore.BLUE + "=" * 20 + Style.RESET_ALL

# Format the incoming xml and remove empty lines
import re
emptyLinePattern = re.compile("(?imu)^\s*\n")
def prettyXml(string):
    xml = parseString(string).toprettyxml("  ")
    return emptyLinePattern.sub('', xml)
           







# Simple Handler for HTTP POST callbacks
#
class CallbackHandler(BaseHTTPRequestHandler):
    def do_POST(s):

        form = cgi.FieldStorage(
            fp=s.rfile, 
            headers=s.headers,
            environ={'REQUEST_METHOD':'POST'})

        # We should tell the O-MI node that we received the message

        s.send_response(200)
        s.end_headers()

        # Read all content
        
        msgxml = form.getfirst("msg", "")
#        msgxml = msg.decode("UTF-8")


        #content_len = int(s.headers['content-length'])
        #post_body = s.rfile.read(content_len)
        #msg = post_body.decode("UTF-8")

        # Print to terminal applying all modifications and add a seperator
        print(seperator())
        print(prettyXml(msgxml))
        print(seperator() + "\n")
        print(Fore.GREEN + Style.BRIGHT)

        global responseNum
        responseNum += 1

server_address = ('localhost', 6432)
httpd = HTTPServer(server_address, CallbackHandler)
httpd.serve_forever()
