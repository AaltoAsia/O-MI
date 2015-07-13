# import WebOmi, add submodule
requestsExt = (WebOmi) ->
  # Sub module for containing all request type templates 
  my = WebOmi.requests = {}

  my.xmls =
    readAll :
      """
      <?xml version="1.0"?>
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd"
          version="1.0" ttl="0">
        <omi:read msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <Objects></Objects>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope> 
      """
    templateMsg :
      """
      <?xml version="1.0"?>
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd"
          version="1.0" ttl="0">
        <omi:read msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope> 

      """
    template :
      """
      <?xml version="1.0"?>
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd"
          version="1.0" ttl="0">
        <omi:read msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope> 

      """

  my.defaults = {}
  my.defaults.empty = ->
    request  : null  # Maybe string (request tag name)
    ttl      : 0     # double
    callback : null  # Maybe string
    requestID: null  # Maybe int
    odf      : null  # Maybe xml
    interval : null  # Maybe number
    newest   : null  # Maybe int
    oldest   : null  # Maybe int
    begin    : null  # Maybe Date
    end      : null  # Maybe Date
    resultDoc: null  # Maybe xml dom document
    msg      : true  # Boolean Is message included

  my.defaults.readAll = ->
    res = $.extend {}, my.defaults.empty(),
      request : "read"
      resultDoc: WebOmi.omi.parseXml(my.xmls.readAll)
    res.odf = res.resultDoc.createElement("Objects")

  my.defaults.readOnce = ->
    $.extend {}, my.defaults.empty(),
      request : "read"

  my.defaults.subscription = ->
    $.extend {}, my.defaults.empty(),
      request : "read"
      interval: 5
      ttl     : 60

  my.defaults.poll = ->
    $.extend {}, my.defaults.empty(),
      request : "read"
      requestID : 1

  my.defaults.write = ->
    doc = WebOmi.omi.parseXml(my.xmls.templateMsg) # TODO
    $.extend {}, my.defaults.empty(),
      request : "write"
      odf     : doc.createElement("Objects")
      
  my.defaults.cancel = ->
    $.extend {}, my.defaults.empty(),
      request : "cancel"
      requestID : 1
      odf     : null
      msg     : false


  # private
  lastParameters = my.defaults



  # @param fastforward: Boolean Whether to also send the request and update odfTree also
  my.readAll = (fastForward) ->
    WebOmi.formLogic.setRequest my.xmls.readAll
    if fastForward
      WebOmi.formLogic.send(WebOmi.formLogic.buildOdfTreeStr)


  # path: String "Objects/path/to/node"
  my.addPathToRequest = (path) ->
    o = WebOmi.omi
    reqCM = WebOmi.consts.requestCodeMirror
    xmlTree = o.parseXml(reqCM.getValue) # FIXME get
    
    for msg in o.evaluateXPath(xmlTree, '//omi:msg')
      currentObjectsHead = o.evaluateXPath(msg, '/odf:Objects')[0]

      if currentObjectsHead?
        # TODO: user edit conflict check
        my.addPathToOdf path, currentObjectsHead
      else
        objects = xmlTree.createElementNS(o.ns.odf, "Objects")
        my.addPathToOdf path, objects
        msg.appendChild objects

  # Creates Objects along the path and then the elementName to the `path`
  # path: String; "Objects/path/to/node" (relative to the odfXmlTree as root)
  # odfXmlTree: XML Dom; the Objects node or other root corresponding to the path
  # elementName: String; One of odf elements, "InfoItem" "Object" TODO: MetaData etc.
  my.addPathToOdf = (path, odfXmlTree, elementName) ->
    # for odf:Object
    setObjectId = (createdElement, id) ->
      idElem = odfXmlTree.createElementNS(WebOmi.omi.ns.odf, "id")
      textElem = odfXmlTree.createTextNode(path)
      idElem.appendChild textElem
      createdElement.appendChild idElem
      createdElement

    headIdx = path.indexOf "/"
    switch headIdx
      when 0  then my.addPathToOdf (path.substr 1), odfXmlTree, elementName# drop 1
      when -1
        createdElement = odfXmlTree.createElementNS(WebOmi.omi.ns.odf, elementName)

        # set name or id
        switch elementName
          when "odf:InfoItem" then createdElement.setAttribute("name", path)
          when "odf:Object"   then setObjectId createdElement, path
          else alert "error in addPathToOdf"

        # finally append the element
        odfXmlTree.appendChild(createdElement)

      else
        head = path.substr(0, headIdx)
        tail = path.substr(headIdx+1)

        child = WebOmi.omi.evaluateXPath(odfXmlTree, "./odf:Object")
        # TODO TODO
        # create Object
        object = odfXmlTree.createElementNS(WebOmi.omi.ns.odf, "Object")
        setObjectId object, head
        # replace newChild, oldChild
        # currentObjectsHead.parent.replaceChild currentObjectsHead

        my.addPathToOdf object tail



    

  my.read = ->

          

  WebOmi # export module

# extend WebOmi
window.WebOmi = requestsExt(window.WebOmi || {})
