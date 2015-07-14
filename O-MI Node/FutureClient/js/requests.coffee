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
    res.odf = res.resultDoc
    res

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
      odf     : WebOmi.omi.createOdf(doc, "Objects")
      
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
    # imports
    o = WebOmi.omi
    reqCM = WebOmi.consts.requestCodeMirror

    xmlTree = o.parseXml(reqCM.getValue()) # FIXME get
    
    for msg in o.evaluateXPath(xmlTree, '//omi:msg')
      currentObjectsHead = o.evaluateXPath(msg, './odf:Objects')[0]
      odfTreeNode = $ jqesc path

      if currentObjectsHead?
        # TODO: user edit conflict check
        my.addPathToOdf odfTreeNode, currentObjectsHead
      else
        objects = o.createOdfObjects xmlTree
        my.addPathToOdf odfTreeNode, objects
        msg.appendChild objects
    
    newRequest = new XMLSerializer().serializeToString xmlTree
    WebOmi.formLogic.setRequest newRequest

  # Adds odf elems to given Objects node from the path using the odfTree
  # odfTreeNode: jquery object; some li object from the tree containing the path in the id
  # odfXmlTree: XML Dom; the odf Objects node, will be updated in-place accordingly
  my.addPathToOdf = (odfTreeNode, odfObjectsTree) ->
    o = WebOmi.omi
    odfDoc = odfObjectsTree.ownerDocument || odfObjectsTree

    nodeElems = $.makeArray odfTreeNode.parentsUntil "#Objects", "li"
    nodeElems.reverse()
    nodeElems.push(odfTreeNode)

    currentOdfNode = odfObjectsTree

    for node in nodeElems

      id = $(node).children("a").text()

      maybeChild = o.getOdfChild(id, currentOdfNode)
      if maybeChild?
        # object exists: TODO: what happens now, murder the children or no-op 
        currentOdfNode = maybeChild

      else
        obj = switch WebOmi.consts.odfTree.get_type(node)
          when "object"   then o.createOdfObject odfDoc, id
          when "infoitem" then o.createOdfInfoItem odfDoc, id

        currentOdfNode.appendChild obj
        currentOdfNode = obj

    odfObjectsTree



  WebOmi # export module

# extend WebOmi
window.WebOmi = requestsExt(window.WebOmi || {})
