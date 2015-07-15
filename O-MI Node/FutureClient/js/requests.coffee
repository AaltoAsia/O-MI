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
    requestDoc: null  # Maybe xml dom document
    msg      : true  # Boolean Is message included

  my.defaults.readAll = ->
    $.extend {}, my.defaults.empty(),
      request : "read"
      odf     : ["Objects"]
      requestDoc: WebOmi.omi.parseXml(my.xmls.readAll)

  my.defaults.readOnce = ->
    $.extend {}, my.defaults.empty(),
      request : "read"
      odf     : ["Objects"]

  my.defaults.subscription = ->
    $.extend {}, my.defaults.empty(),
      request : "read"
      interval: 5
      ttl     : 60
      odf     : ["Objects"]

  my.defaults.poll = ->
    $.extend {}, my.defaults.empty(),
      request : "read"
      requestID : 1
      msg     : false

  my.defaults.write = ->
    $.extend {}, my.defaults.empty(),
      request : "write"
      odf     : ["Objects"]

  my.defaults.cancel = ->
    $.extend {}, my.defaults.empty(),
      request : "cancel"
      requestID : 1
      odf     : null
      msg     : false


  # private
  currentParams = my.defaults.empty

  # true
  my.confirmOverwrite = (oldVal, newVal) ->
    confirm "You have edited the request manually.\n
      Do you want to overwrite #{oldVal.toString} with #{newVal.toString}"

  # Req generation setters that check if user has written some own value
  # Modify request checking the current vs internal, if disagree ask user, set internal
  # Saves the result in currentParams.requestDoc
  my.update =
    request  : (reqName, userDoc) -> # Maybe string (request tag name)
      if not currentParams.request?
        my.loadParams my.defaults[reqName]
      else
        if userDoc?
        else


    ttl      : 0     # double
    callback : null  # Maybe String
    requestID: null  # Maybe Int
    odf      : null  # Maybe Array String paths
    interval : null  # Maybe Number
    newest   : null  # Maybe Int
    oldest   : null  # Maybe Int
    begin    : null  # Maybe Date
    end      : null  # Maybe Date
    msg      : true  # Boolean whether message is included

  # wrapper to update the ui with generated request
  my.generate = ->
    formLogic.setRequest cp.resultDoc


  # generate a new request from currentParams
  my.forceGenerate = ->
    o = WebOmi.omi
    cp = currentParams

    # essential parameters
    if cp.request? && cp.request.length > 0 && cp.ttl?
      cp.requestDoc = o.parseXml my.xmls.empty

    for key, updateFn of my.update
      updateFn cp[key],  # TODO: tutturuu~
      


  # force load all parameters in the omiRequestObject and
  # set them in corresponding UI elements
  my.forceLoadParams = (omiRequestObject) ->
    for key, newVal of omiRequestObject
      currentParams[key] = newVal
      WebOmi.consts.ui[key].set(newVal)
    my.forceGenerate()


  # @param fastforward: Boolean Whether to also send the request and update odfTree also
  my.readAll = (fastForward) ->
    # my.forceLoadParams defaults.readAll()
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
