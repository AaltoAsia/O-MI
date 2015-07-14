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

  ###
  my.set =
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
    ###

  my.loadParams = (omiRequestObject) ->



  # @param fastforward: Boolean Whether to also send the request and update odfTree also
  my.readAll = (fastForward) ->
    WebOmi.formLogic.setRequest my.xmls.readAll
    if fastForward
      WebOmi.formLogic.send(WebOmi.formLogic.buildOdfTreeStr)


  # path: String "Objects/path/to/node"
  my.addPathToRequest = (path) ->
    # imports
    o = WebOmi.omi
    fl = WebOmi.formLogic

    odfTreeNode = $ jqesc path
    
    fl.modifyRequestOdfs (currentObjectsHead) ->

      if currentObjectsHead?
        # TODO: user edit conflict check
        my.addPathToOdf odfTreeNode, currentObjectsHead
      else
        objects = o.createOdfObjects xmlTree
        my.addPathToOdf odfTreeNode, objects
        msg.appendChild objects
    

  # path: String "Objects/path/to/node"
  my.removePathFromRequest = (path) ->
    # imports
    o = WebOmi.omi
    fl = WebOmi.formLogic

    odfTreeNode = $ jqesc path
    fl.modifyRequestOdfs (odfObjects) ->
      my.removePathFromOdf odfTreeNode, odfObjects

  my.removePathFromOdf = (odfTreeNode, odfObjects) ->
    # imports
    o = WebOmi.omi

    nodeElems = $.makeArray odfTreeNode.parentsUntil "#Objects", "li"
    nodeElems.reverse()
    nodeElems.push odfTreeNode

    lastOdfElem = odfObjects
    allOdfElems = for node in nodeElems

      id = $(node).children("a").text()

      maybeChild = o.getOdfChild(id, lastOdfElem)
      if maybeChild?
        lastOdfElem = maybeChild
      maybeChild

    # remove requested
    lastOdfElem.parentElement.removeChild lastOdfElem
    allOdfElems.pop()

    # remove empty parents
    allOdfElems.reverse()
    for elem in allOdfElems
      if not o.hasOdfChildren elem
        elem.parentElement.removeChild elem

    odfObjects




  # Adds odf elems to given Objects node from the path using the odfTree
  # odfTreeNode: jquery object; some li object from the tree containing the path in the id
  # odfObjects: XML Dom; the odf Objects node, will be updated in-place accordingly
  my.addPathToOdf = (odfTreeNode, odfObjects) ->
    o = WebOmi.omi
    odfDoc = odfObjects.ownerDocument || odfObjects

    nodeElems = $.makeArray odfTreeNode.parentsUntil "#Objects", "li"
    nodeElems.reverse()
    nodeElems.push odfTreeNode

    currentOdfNode = odfObjects

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

    odfObjects



  WebOmi # export module

# extend WebOmi
window.WebOmi = requestsExt(window.WebOmi || {})
