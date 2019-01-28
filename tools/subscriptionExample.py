#!/usr/bin/python3

# for subscription sending
from urllib.request import urlopen
import threading, time 
import xml.etree.ElementTree as ElementTree # for parsing result

# for receiving
from http.server import BaseHTTPRequestHandler, HTTPServer
import cgi


# URLs

# Target Node, where to subscribe
dataOutputNode = 'http://localhost:8080'

# Use port 8888, this needs to be open to allow connections from the subscribed node
listenAddress = ('0.0.0.0', 8888)
# the same from the perspective of the target node; usually the public address to this server
callbackAddress = "http://localhost:8888"




# Simple Handler for HTTP POST callbacks
#
class CallbackHandler(BaseHTTPRequestHandler):
    def do_POST(s):
        # Get content
        form = cgi.FieldStorage(
            fp=s.rfile, 
            headers=s.headers,
            environ={'REQUEST_METHOD':'POST'})


        # Read all content, from "msg" parameter
        omiResponseXml = form.getfirst("msg", "")


        # We should tell the O-MI node that we received the message succesfully
        s.send_response(200)
        s.end_headers()

        # Do stuff with the respones
        print()
        print(omiResponseXml)
        print()


# Send the subscription request
# It is sent periodically to automatically fix it, if it is removed from server accidentally

next_call = time.time()
period = 24*60*60

def sendSubscription():

    try:
        # Send an interval subscription to Objects/MyObject/MyItem with urlopen
        response = urlopen(dataOutputNode, data=(
        """<omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="{period}">
              <read msgformat="odf" interval="-1" callback="{callbackAddress}">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>MyObject</id>
                      <InfoItem name="MyItem"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>
        """.format(period=period, callbackAddress=callbackAddress)
        ).encode("utf8")).read().decode()

        # Parse return code
        tree = ElementTree.fromstring(response)
        ns = {'omi': 'http://www.opengroup.org/xsd/omi/1.0/'}
        ret = tree.find(".//omi:return", ns)
        if ret.attrib["returnCode"] != "200":
            raise Exception("Non-successful return code")

    except Exception as e:
        print("Response was:\n" + response)
        raise e

    global next_call
    next_call = next_call + period
    threading.Timer( next_call - time.time(), sendSubscription ).start()

sendSubscription()



# Setup http server
httpd = HTTPServer(listenAddress, CallbackHandler)

# mainloop
httpd.serve_forever()

