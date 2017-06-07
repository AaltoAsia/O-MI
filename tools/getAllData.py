#!/usr/bin/python3

import sys, getopt, requests
from xml.etree.ElementTree import register_namespace, fromstring, ElementTree, Element, tostring

def update_odf(elem):
    for child in elem.getchildren():
        update_odf(child)
        tag = child.tag
        if (tag == '{http://www.opengroup.org/xsd/odf/1.0/}InfoItem') or (tag == '{http://www.opengroup.org/xsd/odf/1.0/}Object'):
            child.insert(1,Element('{http://www.opengroup.org/xsd/odf/1.0/}description'))
        if (tag == '{http://www.opengroup.org/xsd/odf/1.0/}InfoItem'):
            child.remove(child.find("./{http://www.opengroup.org/xsd/odf/1.0/}value"))
            child.append(Element('{http://www.opengroup.org/xsd/odf/1.0/}MetaData'))
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
    hierarchyRequest = """<omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/"  version="1.0" ttl="0">
  <read msgformat="odf">
    <msg>
      <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/"/>
    </msg>
  </read>
</omiEnvelope>"""
    #request for 9000 newest values(should be enough for now) 
    fullRequest = """<omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="0">
  <read msgformat="odf" newest="9000">
    <msg>
    </msg>
  </read>
</omiEnvelope>"""


    #register namespaces so that we don't get wrong namespaces in the request
    register_namespace("omi","omi.xsd")
    register_namespace("odf", "odf.xsd")
    register_namespace("", "http://www.w3.org/2001/XMLSchema-instance")
    headers = {'Content-Type': 'application/xml'}

    #current hierarchy
    r = requests.post(hostn, data = hierarchyRequest, headers = headers).text

    root = fromstring(r)
    
    objects = root.find(".//{http://www.opengroup.org/xsd/odf/1.0/}Objects")
    #remove values and add metadata and description tags
    update_odf(objects)

    fullRoot = fromstring(fullRequest)

    fullRoot.find(".//{http://www.opengroup.org/xsd/omi/1.0/}msg").append(objects)

    #write result to file. note: result might be big so iterate over the result
    with open(output,'wb') as handle:
        r2 = requests.post(hostn, data = tostring(fullRoot, encoding="utf-8"), headers = headers, stream = True)
        if not r2.ok:
            print("INVALID RESPONSE")
        for block in r2.iter_content(1024):
            handle.write(block)



if __name__ == "__main__":
    main(sys.argv[1:])
