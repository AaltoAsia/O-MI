#!/usr/bin/python3

import sys, getopt, requests
from xml.etree.ElementTree import register_namespace, fromstring, ElementTree, Element, tostring

def update_odf(elem):
    for child in elem.getchildren():
        update_odf(child)
        tag = child.tag
        if (tag == '{odf.xsd}InfoItem') or (tag == '{odf.xsd}Object'):
            child.insert(1,Element('{odf.xsd}description'))
        if (tag == '{odf.xsd}InfoItem'):
            child.remove(child.find("./{odf.xsd}value"))
            child.append(Element('{odf.xsd}MetaData'))
def main(argv):
    hostn = 'http://localhost:8080' #localhost at default port
    output = 'odfdump.xml' #default file
    try:
        opts, args = getopt.getopt(argv,"ho:")
    except getopt.GetoptError:
        print('getAllData.py -o <outputfile> host')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print('getAllData.py -o <outputfile> host')
            sys.exit()
        elif opt == '-o':
            output = arg
    if len(args) >= 1:
        hostn = args[0]

    #request for odf hierarchy
    hierarchyRequest = """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" version="1.0" ttl="0">
  <omi:read msgformat="odf">
    <omi:msg>
      <Objects xmlns="odf.xsd"/>
    </omi:msg>
  </omi:read>
</omi:omiEnvelope>"""
    #request for 9000 newest values(should be enough for now) 
    fullRequest = """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" version="1.0" ttl="0">
  <omi:read msgformat="odf" newest="9000">
    <omi:msg>
    </omi:msg>
  </omi:read>
</omi:omiEnvelope>"""


    #register namespaces so that we don't get wrong namespaces in the request
    register_namespace("omi","omi.xsd")
    register_namespace("odf", "odf.xsd")
    register_namespace("", "http://www.w3.org/2001/XMLSchema-instance")
    headers = {'Content-Type': 'application/xml'}

    #current hierarchy
    r = requests.post(hostn, data = hierarchyRequest, headers = headers).text

    root = fromstring(r)

    objects = root.find(".//{odf.xsd}Objects")

    #remove values and add metadata and description tags
    update_odf(objects)

    fullRoot = fromstring(fullRequest)

    fullRoot.find(".//{omi.xsd}msg").append(objects)

    #write result to file. note: result might be big so iterate over the result
    with open(output,'wb') as handle:
        r2 = requests.post(hostn, data = tostring(fullRoot, encoding="utf-8"), headers = headers, stream = True)
        if not r2.ok:
            print("INVALID RESPONSE")
        for block in r2.iter_content(1024):
            handle.write(block)



if __name__ == "__main__":
    main(sys.argv[1:])
