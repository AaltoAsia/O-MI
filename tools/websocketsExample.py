import asyncio, ssl, time
# pip install websocets lxml
try:
    import websockets
    from lxml import etree
except ImportError as e:
    print(e, ": run `pip3 install lxml websockets`")


websocketUrl = "ws://localhost:8080"

# TODO: for wss urls where client certificate is used; Change these paths and password to match your file
certificateFile = None # e.g. "client.crt"
privateKeyFile = None # e.g. "client.key"
privateKeyPassword = None # password string for the private key


if certificateFile:
    context = ssl.SSLContext(ssl.PROTOCOL_SSLv23)
    context.load_cert_chain(certificateFile, privateKeyFile, password=privateKeyPassword)
else:
    context = None


# TODO: replace with some subscription (on webclient: select some objects and O-MI Request "Subscription")
subscription = \
"""<omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="0">
  <read msgformat="odf" callback="0" interval="-1">
    <msg>
      <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
      </Objects>
    </msg>
  </read>
</omiEnvelope>
"""


ns = {
    "odf1" : "http://www.opengroup.org/xsd/odf/1.0/", "omi1": "http://www.opengroup.org/xsd/omi/1.0/",
    "odf2" : "http://www.opengroup.org/xsd/odf/2.0/", "omi2": "http://www.opengroup.org/xsd/omi/2.0/"
}
def xpath(root, query):
    return root.xpath(query, namespaces=ns)

def slashEscape(string):
    return string.replace('/', '\\/')

def getPath(elem, ending=""):
    if not elem.tag.endswith("Objects"):
        name = xpath(elem, "./@name|./odf1:id/text()|./odf2:id/text()") # name attribute or the text of <id> element
        if len(name) == 1:
            return getPath(elem.getparent(), "/" + slashEscape(name[0]) + ending)
    return ending
    

def processResponse(response):
    xml = etree.fromstring(bytes(response, "utf-8"))

    infoItems = xpath(xml, "//odf1:InfoItem|//odf2:InfoItem")
    for item in infoItems:
        name = item.get("name")
        path = getPath(item)
        values = xpath(item, "./odf1:value|./odf2:value")

        # TODO: do something with name and value(s)
        print(path, ":", [v.text for v in values])



async def subscribe():
    while True:
        try:
            async with websockets.connect(websocketUrl, ssl=context) as ws:
                await ws.send(subscription)

                while ws.open:
                    try:
                        response = await ws.recv()
                        print(response)
                        processResponse(response)

                    except websockets.exceptions.ConnectionClosedError as e:
                        print(e)

                    print()
        except OSError as e:
            print(e)
        time.sleep(10)


asyncio.get_event_loop().run_until_complete(subscribe())

