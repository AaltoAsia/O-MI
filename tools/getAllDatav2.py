#!/usr/bin/python3
import sys, os, argparse, re, functools, pathlib, gzip
from requests import Session
from copy import deepcopy
#import websocket

def eprint(*args, **kwargs): print(*args, file=sys.stderr, **kwargs)
def noprint(*args, **kwargs): pass

try:
    from lxml import etree
except ImportError:
    eprint("ERROR: missing lxml library; Try `pip3 install lxml` and then run again.")
    sys.exit(2)


parser = argparse.ArgumentParser(description=
        '''Get All Data from target server, including descriptions, metadata and historical values.
        The results are saved in files separated by each infoitem.''')
    
parser.add_argument('--output', '-o', dest='output', default=None, help='Output files directory. If --single-file argument is used, the output value is interprented as file name.')

parser.add_argument('--odf-path', '--path', '-p', dest='odfPath', default="", type=str, #nargs="+",
        help="Select only InfoItems under some O-DF paths rather than read all.")

parser.add_argument('--max-newest', '-n', dest='nMax', type=int, default=1000, 
        help='Max value for newest parameter, if n is small, multiple request may be neccessary to query all the data from a single InfoItem. Some servers might limit the newest parameter.')

parser.add_argument('--ssl-client-cert', dest='cCert', default=None, 
        help='File containing the client certificate as well as any number of CA certificates needed to establish the authenticity of the certificate')

parser.add_argument('--ssl-client-key', dest='cKey', default=None, 
        help='File containing the private key. If omitted the key is taken from the cert file as well.')

parser.add_argument('--odf-xmlns', dest='odfVersion', default="http://www.opengroup.org/xsd/odf/1.0/",
        help='Xml namespace for the O-DF. Default: http://www.opengroup.org/xsd/odf/1.0/')

parser.add_argument('--omi-xmlns', dest='omiVersion', default="http://www.opengroup.org/xsd/omi/1.0/",
        help='Xml namespace for the O-MI. Default: http://www.opengroup.org/xsd/omi/1.0/')

parser.add_argument('--omi-version', dest='version', default=None,
        help='Value of omiEnvelope version attribute. If empty, attempts to parse from namespace or defaults to 1.0 if parsing fails.')

parser.add_argument('--sort', dest='sort', default=False, action='store_true',
        help='Sorts received values, required if the target server does not sort values for the response messages. Latest value must be at top.')

parser.add_argument('--single-file', dest='single_file', default=False, action='store_true',
        help='Store the results in a single file instead of a file strucure.')

parser.add_argument('--compression', '-c', dest='compression', type=int, default=0, choices=range(0,10), 
        help='If compression level is more than 0, gzip is used to compress the output files and .gz suffix is added to the output file.')

parser.add_argument('--chunk-size', dest='chunk_size', type=int, default=1024,
        help='Size of the response chunks written to file in bytes. Increasing this value might increase memory consumption. Default 1024')

parser.add_argument('--location', '-L', dest='location', action='store_true', help='Follow redirects.')

parser.add_argument('--verbose', '-v', dest='debug',
        action='store_const',const=eprint, default=noprint, 
        help="Print extra information during processing")

parser.add_argument('--pretty-print', dest='pretty_print', default=False, action='store_true',
        help='Pretty prints the output xml')

parser.add_argument('--version', action='version', version='getAllData v2.0')

parser.add_argument('url', metavar='URL')


args = parser.parse_args()

debug = args.debug

chunk_size = args.chunk_size

omiVersion = args.omiVersion
odfVersion = args.odfVersion

ns = {'odf':odfVersion, 'omi':omiVersion}

#version attribute of the request
if args.version:
    debug("setting version attribute to", args.version)
    version = args.version
else:
    #match for something that looks like version from the namespace string
    p = re.compile(r'/(?P<version>\d+\.\d+]*)/')
    m = p.search(omiVersion)
    v = m.group('version')
    if v:
        debug("version attribute set to", v)
        version=v
    else:
        debug("version attribute set to 1.0")
        version=1.0


#create output directory or exit if it is a file
fileName = "Objects.xml"
if not args.single_file:
    if args.output == None:
        args.output = ""
    if os.path.isfile(args.output):
        eprint('Output directory is a file, directory expected')
        sys.exit(3)
else:
    if args.output:
        fileName = args.output

if args.compression > 0:
    if not fileName.endswith('.gz'):
        fileName = fileName + '.gz'
else:
    args.compression = None

if args.nMax < 2:
    eprint('Minimum newest value needs to be more than 1')
    sys.exit(5)

nMax = args.nMax
url = args.url
redirect = args.location

# 0. send readAll request to server to get hierarchy
# 1. create hierarchy structure with metadata and description tags included
# 2. create single request for each infoitem encountered with newest value set to nMax until all values read
# 3. save the output in Objects.xml file in directory structure that resembles the ODF hierarchy


# A simple function to create a O-DF/O-MI read request from O-DF path
def hierarchyRequest(s):
    if len(args.odfPath) > 0:
        # simple xml building
        hierarchyRequest = """<omiEnvelope xmlns="{omi}" version="{version}" ttl="0">
             <read msgformat="odf">
               <msg>
                <Objects xmlns="{odf}">
           """.format(omi=omiVersion, version=version, odf=odfVersion)
        pathSegments = [s for s in args.odfPath.split("/") if len(s) > 0 and s != "Objects"]
        debug(pathSegments)

        # fetch the leaf to check whether it is Object or InfoItem

        query = url + "/Objects/" + '/'.join(pathSegments)
        result = s.get(query)
        if result.status_code != 200:
            eprint('O-DF query', query, 'failed (--odf-path):', result.text)
            exit(1)
        lastIsInfo = result.text.endswith("InfoItem>")
        debug("Path", args.odfPath, "is an InfoItem" if lastIsInfo else "is an Object:")
        #debug(result.text)
        
        close = 0
        loopSegments = pathSegments[:-1] if lastIsInfo else pathSegments
        for segment in loopSegments:
            segment = segment.strip()
            hierarchyRequest += '<Object><id>{}</id>'.format(segment)
            close += 1
        if lastIsInfo: hierarchyRequest += '<InfoItem name="{}" />'.format(pathSegments[-1])
        for i in range(close):
            hierarchyRequest += '</Object>'

        hierarchyRequest += '</Objects> </msg> </read> </omiEnvelope>'
        #debug(hierarchyRequest)

        return hierarchyRequest

    else:
        return """<omiEnvelope xmlns="{omi}" version="{version}" ttl="0">
             <read msgformat="odf">
               <msg>
                 <Objects xmlns="{odf}"/>
               </msg>
             </read>
           </omiEnvelope>""".format(omi=omiVersion, version=version, odf=odfVersion)

#create request with correct newest and end values
def fullRequestBase(n,end=None):
    fullr = """<omiEnvelope xmlns="{omi}" version="{version}" ttl="0">
    <read msgformat="odf" newest="{newest}" >
    <msg>
    </msg>
    </read>
    </omiEnvelope>
    """.format(omi=omiVersion, version=version, newest=n)
    xmlbase = etree.fromstring(fullr) 
    if end:
        xmlbase.find(".//{%s}read" % ns['omi']).set("end", end)
    return xmlbase
    

   
cert = None
if args.cCert and args.cKey:
    debug("Setting both client certificate and key...")
    cert = (args.cCert, args.cKey)
elif args.cCert:
    debug("Attempting to read pem file")
    cert = args.cCert

#request headers
headers = {'Content-Type': 'application/xml'}


def update_odf(elem):
    for child in elem.getchildren():
        update_odf(child)
        tag = child.tag
        #important to go from bottom up
        if (tag == '{%s}description' % odfVersion) or (tag == '{%s}MetaData' % odfVersion):
            child.clear()
        if (tag == '{%s}InfoItem' % odfVersion):
            values = child.xpath("./odf:value", namespaces=ns)
            for ivalue in values:
                child.remove(ivalue)
            #child.remove(child.find('./{%s}value' % odfVersion))

## use method below if it is necessary to insert metadata and description tags to every element
#def update_odf(elem):
#    for child in elem.getchildren():
#        update_odf(child)
#        tag = child.tag
#        if (tag == '{%s}InfoItem' % odfVersion) or (tag == '{%s}Object' % odfVersion):
#            child.insert(1,etree.Element('{%s}description' % odfVersion))
#        if (tag == '{%s}InfoItem' % odfVersion):
#            child.remove(child.find('./{%s}value' % odfVersion))
#            child.append(etree.Element('{%s}MetaData' % odfVersion))

# Print iterations progress https://stackoverflow.com/a/34325723
def printProgressBar (iteration, total, prefix = '', suffix = '', decimals = 1, length = 100, fill = '█', printEnd = "\r"):
    ## Initial call to print 0% progress
    #printProgressBar(0, l, prefix = 'Progress:', suffix = 'Complete', length = 50)
    #for i, item in enumerate(items):
    #    # Do stuff...
    #    time.sleep(0.1)
    #    # Update Progress Bar
    #    printProgressBar(i + 1, l, prefix = 'Progress:', suffix = 'Complete', length = 50)

    """
    Call in a loop to create terminal progress bar
    @params:
        iteration   - Required  : current iteration (Int)
        total       - Required  : total iterations (Int)
        prefix      - Optional  : prefix string (Str)
        suffix      - Optional  : suffix string (Str)
        decimals    - Optional  : positive number of decimals in percent complete (Int)
        length      - Optional  : character length of bar (Int)
        fill        - Optional  : bar fill character (Str)
        printEnd    - Optional  : end character (e.g. "\r", "\r\n") (Str)
    """
    percent = ("{0:." + str(decimals) + "f}").format(100 * (iteration / float(total)))
    filledLength = int(length * iteration // total)
    bar = fill * filledLength + '-' * (length - filledLength)
    print('\r%s |%s| %s%% %s' % (prefix, bar, percent, suffix), end = printEnd)
    # Print New Line on Complete
    if iteration == total: 
        print()

#function to create Objects element from bottom up
def combineElements(first,second):
    secondTag = second.tag
    #copy the required attributes and sub elements for the request,
    secondAttribs = dict(second.attrib)
    secondIds = second.xpath('./odf:id', namespaces = ns)
    secondDesc = second.xpath('./odf:description', namespaces = ns)
    secondMeta = second.xpath('./odf:MetaData', namespaces = ns)
    
    #create copy of the element
    secondCopy = etree.Element(secondTag)
    secondCopy.attrib.update(secondAttribs)
    for sid in secondIds:
        secondCopy.append(deepcopy(sid))
    for sd in secondDesc:
        secondCopy.append(deepcopy(sd))
    for smd in secondMeta:
        secondCopy.append(deepcopy(smd))
    if first == True:
        return secondCopy
    else:
        secondCopy.append(first)
        return secondCopy 

def getKey(elem):
    if elem.tag == "{%s}value" % odfVersion:
        return float(elem.get("unixTime"))
        #to parse  from datetime use dateutils.parser.parse and convert
    else:
        return -1.0

def getIdOrName(elem):
    if elem.tag == "{%s}Object" % odfVersion:
        return elem.findtext("./{%s}id" % odfVersion)
    elif elem.tag == "{%s}InfoItem" % odfVersion:
        return elem.get("name")
    elif elem.tag == "{%s}Objects" % odfVersion:
        return "Objects"
    else:
        eprint("ERROR: invalid element:", elem.tag)
        sys.exit(4)
            
#Create request session with the certificates and headers set
if args.single_file:
    with Session() as s:
        with etree.xmlfile(fileName, encoding='utf-8', compression=args.compression) as xf: #encoding?
            s.cert = cert
            s.headers.update(headers)
            
            r = s.get(url)
            
            #hierarchy tree
            if url != r.url:
                if redirect:
                    url = r.url
                else:
                    eprint("Error: Request redirected to {}. Use -L parameter to follow redirections.".format(r.url))
                    sys.exit(6)

            
            #hierarchy tree
            r = s.post(url, data = hierarchyRequest(s)).content
            root = etree.fromstring(r)
            
            objects = root.find(".//{%s}Objects" % odfVersion)
            
            #insert description and metadata to the objects and infoitems even if they were not in the hierarchy?
            update_odf(objects)
            
            debug("request hierarchy successfully built")
    
            #update_odf removes metadata infoitems so they are not included in the leafs    
            leafs = objects.xpath("//odf:InfoItem|//odf:Object[count(./odf:InfoItem|./odf:Object) = 0]", namespaces = ns)
            
            numLeafs = len(leafs)
            debug("number of leafs found: {}".format(numLeafs))
            
            #iterate all leafs and make request for each one
            i = 0
            with xf.element('omi:omiEnvelope', {'ttl': '1.0', 'version': '1.0'}, nsmap=ns):
                with xf.element('omi:response'):
                    printProgressBar(i, numLeafs, prefix='Progress', suffix='%s/%s' % (i, numLeafs), length= 50)
                    for leaf in leafs:
                        with xf.element('omi:result', {'msgformat': 'odf'}):
                            with xf.element('omi:return', {'returnCode':'200'}):
                                pass
                            i += 1
                            #get ancestor odf elements
                            ancestors = leaf.xpath("ancestor-or-self::odf:*", namespaces=ns)
                            #build request Objects
                            requestOdf = functools.reduce(combineElements, reversed(ancestors), True)
                            ##outputPath = os.path.join(args.output, *list(map(getIdOrName, ancestors)))
                            ##pathlib.Path(outputPath).mkdir(parents=True, exist_ok=True)
                            ##output = os.path.join(outputPath, "Objects.xml")
    
                            #element to build the result in
                            robjs = None
                            #timestamp of the oldest value
                            latest = None
                            values = None
                            #infoitem element of the robjs element
                            rinfo = None
    
                            numvalues = nMax
    
                            #build odftree containing a single infoitem with all its values
                            #if memory consumption becomes bottleneck, build the odf bit by bit
                            while numvalues == nMax:
                                reqBase = fullRequestBase(nMax, latest) 
                                reqBase.find(".//{%s}msg" % ns['omi']).append(requestOdf)
                                r2 = s.post(url, data = etree.tostring(reqBase, encoding='UTF-8')).content
                                rxml = etree.fromstring(r2)
    
                                retElements = rxml.xpath("omi:response/omi:result/omi:return[@returnCode!=200]", namespaces =ns)
                                for error in retElements:
                                    eprint('Error for path: "', outputPath, '", with error: ',  etree.tostring(error,pretty_print= True, encoding='unicode'))
    
                                #first iter
                                if robjs is None:
                                    robjs = rxml.find(".//{%s}Objects" % odfVersion)
    
                                #if leaf was object element or something else, then 
                                #it doesn't contain values only desc or metadata
                                if leaf.tag != "{%s}InfoItem" % odfVersion:
                                    break
    
                                #Only one infoitem should exist that is inside an object
                                rii = rxml.find(".//{%s}Object/{%s}InfoItem" % (odfVersion, odfVersion))
    
                                #first iter
                                if rinfo is None:
                                    rinfo = rii
                                    if args.sort:
                                        rinfo[:] = sorted(rinfo, key=getKey)
                                    numvalues = len(rinfo.xpath("odf:value", namespaces = ns))
                                #successive iter
                                else:
                                    try:
                                        values = rii.xpath("odf:value[position()>1]", namespaces = ns)
                                        numvalues = len(values) + 1 #ignoring first value
                                        if numvalues == nMax:
                                            latest = rii.xpath("odf:value[last()]", namespaces = ns)[0].get("dateTime")
                                    except IndexError as e:
                                        #debug("Empty value from respose")
                                        values = []
                                        numvalues = 0
    
                                    if args.sort:
                                        for val in sorted(values, key=getKey):
                                            rinfo.append(val)
                                    else:
                                        for val in values:
                                            rinfo.append(val)
                                printProgressBar(i, numLeafs, prefix='Progress', suffix='%s/%s - %s val      ' % (i, numLeafs, len(rinfo)), length= 50)
                            with xf.element('omi:msg'):
                                xf.write(robjs,pretty_print=args.pretty_print)
                            #with open(output, 'wb') as handle:
                            #    et = etree.ElementTree(robjs)
                            #    et.write(handle, pretty_print=args.pretty_print)
    
    
    
                            #progressbar 1 request can take from 1 to 15 seconds ....
                            printProgressBar(i, numLeafs, prefix='Progress', suffix='%s/%s                 ' % (i, numLeafs), length= 50)
                    
                    #first write hierarchy to temp file
                    #with open(output + "_", 'wb') as handle:
                    #    r2 = s.post(url, data = etree.tostring(firstReqBase, encoding='UTF-8'), stream = True)
                    #    if not r2.ok:
                    #        debug("INVALID RESPONSE")
                    #    for chunk in r2.iter_content(chunk_size=chunk_size):
                    #        handle.write(chunk)

else:
    with Session() as s:
        s.cert = cert
        s.headers.update(headers)
        
        r = s.get(url)

        #hierarchy tree
        if url != r.url:
            if redirect:
                url = r.url
            else:
                eprint("Error: Request redirected to {}. Use -L parameter to follow redirections.".format(r.url))
                sys.exit(6)


        r = s.post(url, data = hierarchyRequest(s)).content
        root = etree.fromstring(r)
        
        objects = root.find(".//{%s}Objects" % odfVersion)
        
        #insert description and metadata to the objects and infoitems even if they were not in the hierarchy?
        update_odf(objects)
        
        debug("request hierarchy successfully built")
    
        #update_odf removes metadata infoitems so they are not included in the leafs    
        leafs = objects.xpath("//odf:InfoItem|//odf:Object[count(./odf:InfoItem|./odf:Object) = 0]", namespaces = ns)
        
        numLeafs = len(leafs)
        debug("number of leafs found: {}".format(numLeafs))
        
        #iterate all leafs and make request for each one
        i = 0
    
        printProgressBar(i, numLeafs, prefix='Progress', suffix='Complete', length= 50)
        for leaf in leafs:
            i += 1
            #get ancestor odf elements
            ancestors = leaf.xpath("ancestor-or-self::odf:*", namespaces=ns)
            #build request Objects
            requestOdf = functools.reduce(combineElements, reversed(ancestors), True)
            outputPath = os.path.join(args.output, *list(map(getIdOrName, ancestors)))
            pathlib.Path(outputPath).mkdir(parents=True, exist_ok=True)
            output = os.path.join(outputPath, fileName)
    
            #element to build the result in
            robjs = None
            #timestamp of the oldest value
            latest = None
            values = None
            #infoitem element of the robjs element
            rinfo = None
    
            numvalues = nMax
    
            #build odftree containing a single infoitem with all its values
            #if memory consumption becomes bottleneck, build the odf bit by bit
            while numvalues == nMax:
                reqBase = fullRequestBase(nMax, latest) 
                reqBase.find(".//{%s}msg" % ns['omi']).append(requestOdf)
                r2 = s.post(url, data = etree.tostring(reqBase, encoding='UTF-8')).content
                rxml = etree.fromstring(r2)
    
                retElements = rxml.xpath("omi:response/omi:result/omi:return[@returnCode!=200]", namespaces =ns)
                for error in retElements:
                    eprint('Error for path: "', outputPath, '", with error: ',  etree.tostring(error,pretty_print= True, encoding='unicode'))
    
                #first iter
                if robjs is None:
                    robjs = rxml.find(".//{%s}Objects" % odfVersion)
    
                #if leaf was object element or something else, then 
                #it doesn't contain values only desc or metadata
                if leaf.tag != "{%s}InfoItem" % odfVersion:
                    break
    
                #Only one infoitem should exist that is inside an object
                rii = rxml.find(".//{%s}Object/{%s}InfoItem" % (odfVersion, odfVersion))
    
                #first iter
                if rinfo is None:
                    rinfo = rii
                    if args.sort:
                        rinfo[:] = sorted(rinfo, key=getKey)
                    numvalues = len(rinfo.xpath("odf:value", namespaces = ns))
                #successive iter
                else:
                    try:
                        values = rii.xpath("odf:value[position()>1]", namespaces = ns)
                        numvalues = len(values) + 1 #ignoring first value
                        if numvalues == nMax:
                            latest = rii.xpath("odf:value[last()]", namespaces = ns)[0].get("dateTime")
                    except IndexError as e:
                        #debug("Empty value from respose")
                        values = []
                        numvalues = 0
    
                    if args.sort:
                        for val in sorted(values, key=getKey):
                            rinfo.append(val)
                    else:
                        for val in values:
                            rinfo.append(val)

                printProgressBar(i, numLeafs, prefix='Progress', suffix='%s/%s - %s val      ' % (i, numLeafs, len(rinfo)), length= 50)

            if args.compression:
                with gzip.open(output, 'wb', compresslevel = args.compression) as handle:
                    et = etree.ElementTree(robjs)
                    et.write(handle, pretty_print=args.pretty_print)
            else:
                with open(output, 'wb') as handle:
                    et = etree.ElementTree(robjs)
                    et.write(handle, pretty_print=args.pretty_print)
    
    
    
            #progressbar 1 request can take from 1 to 15 seconds ....
            printProgressBar(i, numLeafs, prefix='Progress', suffix='%s/%s                 ' % (i, numLeafs), length= 50)
        
        #first write hierarchy to temp file
        #with open(output + "_", 'wb') as handle:
        #    r2 = s.post(url, data = etree.tostring(firstReqBase, encoding='UTF-8'), stream = True)
        #    if not r2.ok:
        #        debug("INVALID RESPONSE")
        #    for chunk in r2.iter_content(chunk_size=chunk_size):
        #        handle.write(chunk)

