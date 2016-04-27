###########################################################################
#  Copyright (c) 2015 Aalto University.
#
#  Licensed under the 4-clause BSD (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at top most directory of project.
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
##########################################################################

# import WebOmi, add submodule
requestsExt = (WebOmi) ->
  # Sub module for containing all request type templates
  my = WebOmi.requests = {}

  # These are loaded if theres no request yet,
  # otherwise the current request is just modified, never loaded again if it exists.
  # So in practice the only the omiEnvelope comes from one of these,
  # others can be created from updaters
  my.xmls =
    readAll :
      """
      <?xml version="1.0"?>
      <omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd"
          version="1.0" ttl="0">
        <omi:read msgformat="odf">
          <omi:msg>
            <Objects xmlns="odf.xsd"></Objects>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope>
      """
    template :
      """
      <?xml version="1.0"?>
      <omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd"
          version="1.0" ttl="0">
        <omi:read msgformat="odf">
          <omi:msg>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope>

      """

  # Usage of these defaults is a bit sparse,
  # they are used when the first request is selected
  # and can be used to check some parameter like request tag name
  my.defaults = {}
  my.defaults.empty = ->
    name     : "empty"
    request  : null  # Maybe string (request tag name)
    ttl      : 0     # double
    callback : null  # Maybe string
    requestID: null  # Maybe int
    odf      : null  # Maybe Array String paths
    interval : null  # Maybe number
    newest   : null  # Maybe int
    oldest   : null  # Maybe int
    begin    : null  # Maybe Date
    end      : null  # Maybe Date
    requestDoc: null  # Maybe xml dom document
    msg      : true  # Boolean Is message included

  my.defaults.readAll = ->
    $.extend {}, my.defaults.empty(),
      name    : "readAll"
      request : "read"
      odf     : ["Objects"]

  my.defaults.read = ->
    $.extend {}, my.defaults.empty(),
      name    : "readOnce"
      request : "read"
      odf     : ["Objects"]

  #my.defaults.readOnce = -> my.defaults.read()
  # rather force selection to readOnce

  my.defaults.subscription = ->
    $.extend {}, my.defaults.empty(),
      name    : "subscription"
      request : "read"
      interval: 5
      ttl     : 60
      odf     : ["Objects"]

  my.defaults.poll = ->
    $.extend {}, my.defaults.empty(),
      name    : "poll"
      request : "read"
      requestID : 1
      msg     : false

  my.defaults.write = ->
    $.extend {}, my.defaults.empty(),
      name    : "write"
      request : "write"
      odf     : ["Objects"]

  my.defaults.cancel = ->
    $.extend {}, my.defaults.empty(),
      name    : "cancel"
      request : "cancel"
      requestID : 1
      odf     : null
      msg     : false


  # private; holds current params that should be also set in resulting CodeMirror
  # This is used to check if the parameter exists or not and is it the same as new
  currentParams = my.defaults.empty()
  my.getCurrentParams = -> $.extend {}, currentParams

  # TODO: not used
  my.confirmOverwrite = (oldVal, newVal) ->
    confirm "You have edited the request manually.\n
      Do you want to overwrite #{oldVal.toString} with #{newVal.toString}"


  #private; Used to add value tag when in write request
  # "0" placeholder, otherwise xml is formatted to <value />
  # values have structure [{ value:String, valuetime:String unix-time, valuetype:String }]
  addValueWhenWrite = (odfInfoItem, values=[{value:"0"}]) ->
    if currentParams.request == 'write'
      doc = odfInfoItem.ownerDocument

      for value in values
        val = WebOmi.omi.createOdfValue doc, value.value, value.valuetype, value.valuetime
        odfInfoItem.appendChild val

  #private; Used to add value tags to all info items when in write request
  addValueToAll = (doc) ->
    infos = WebOmi.omi.evaluateXPath doc, "//odf:InfoItem"
    addValueWhenWrite info for info in infos

  #private; Used to remove value tags when not in write request
  removeValueFromAll = (doc) ->
    vals = WebOmi.omi.evaluateXPath doc, "//odf:value"
    val.parentNode.removeChild val for val in vals

  # removes the given path from odf xml,
  # removes also parents if there is no other children
  my.removePathFromOdf = (odfTreeNode, odfObjects) ->
    # imports
    o = WebOmi.omi

    # get ancestors
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
    lastOdfElem.parentNode.removeChild lastOdfElem
    allOdfElems.pop()

    # remove empty parents
    allOdfElems.reverse()
    for elem in allOdfElems
      if elem? and not o.hasOdfChildren elem
        elem.parentNode.removeChild elem

    odfObjects


  # TODO: better place for this, e.g. WebOmi.util?
  maybeInsertBefore = (parent, beforeTarget, insertElem) ->
    if beforeTarget?
      parent.insertBefore insertElem, beforeTarget
    else
      parent.appendChild insertElem


  # Adds odf elems to given Objects node from the path using the odfTree, creating parents also
  # odfTreeNode: jquery object; some li object from the tree containing the path in the id
  # odfObjects: XML Dom; the odf Objects node, will be updated in-place accordingly
  my.addPathToOdf = (odfTreeNode, odfObjects) ->
    o = WebOmi.omi
    odfDoc = odfObjects.ownerDocument || odfObjects

    if not odfTreeNode[0]? or odfTreeNode[0].id == "Objects"
      return odfObjects

    nodeElems = $.makeArray odfTreeNode.parentsUntil "#Objects", "li"
    nodeElems.reverse()
    nodeElems.push odfTreeNode

    currentOdfNode = odfObjects

    for node in nodeElems

      id = $(node).children("a").text()

      maybeChild = o.getOdfChild(id, currentOdfNode)
      if maybeChild?
        # object exists: TODO: what happens now, murder the children or no-op
        currentOdfNode = maybeChild  # no-op

      else
        # create new info or object, infos must be before objects but after <id>s
        odfElem = switch WebOmi.consts.odfTree.get_type(node)
          when "object"
            object = o.createOdfObject odfDoc, id
            currentOdfNode.appendChild object
          when "metadata"
            meta = o.createOdfMetaData odfDoc

            metas = $(node).data "metadatas"
            if metas? and currentParams.request == "write"
              for metadata in metas
                metainfo = o.createOdfInfoItem odfDoc, metadata.name, [
                  value:     metadata.value
                  vAluetype: metadata.type
                ], metadata.description
                meta.appendChild metainfo

            # find the first value and insert before it (schema restriction)
            siblingValue = o.evaluateXPath(currentOdfNode, "odf:value[1]")[0]
            maybeInsertBefore currentOdfNode, siblingValue, meta

          when "infoitem"
            info =
              if currentParams.request == "write"
                # when request is write
                maybeValues = $(node).data "values"
                maybeDesc   = $(node).data "description"
                maybeValues = if maybeValues? then maybeValues else [{value:"VALUE_PLACEHOLDER"}]
                o.createOdfInfoItem odfDoc, id, maybeValues, maybeDesc
              else
                o.createOdfInfoItem odfDoc, id


            # find the first Object and insert before it (schema restriction)
            siblingObject = o.evaluateXPath(currentOdfNode, "odf:Object[1]")[0]
            maybeInsertBefore currentOdfNode, siblingObject, info

        currentOdfNode = odfElem

    odfObjects

  # private; Creates update setter for simple omi xml attributes
  # name:            String; Attribute name
  # attrParentXPath: String(Xpath); Selector for the parent of the attribute
  updateSetterForAttr = (name, attrParentXPath) ->
    update : (newVal) ->
      o = WebOmi.omi
      doc = currentParams.requestDoc
      if currentParams[name] != newVal
        attrParents = o.evaluateXPath doc, attrParentXPath
        if not attrParents?
          WebOmi.error "Tried to update #{name}, but #{attrParentXPath} was not found in", doc
        else
          for parent in attrParents
            if newVal?
              parent.setAttribute name, newVal
            else
              parent.removeAttribute name

        currentParams[name] = newVal
        newVal



  # Req generation setters that check if user has written some own value
  # Modify request checking if its a new value, set internal
  # Saves the result in currentParams.requestDoc
  my.params =
    name :
      update : (name) ->
        if currentParams.name != name
          currentParams.name = name
          requestTag = switch name
            when "poll", "subscription", "readAll", "readReq", "readOnce", "template"
              "read"
            when "empty"
              null
            else name
          my.params.request.update requestTag

    request  :
      # selector: ()
      update  : (reqName) -> # Maybe string (request tag name)
        oldReqName = currentParams.request

        if not currentParams.requestDoc?
          my.forceLoadParams my.defaults[reqName]()

        else if reqName != oldReqName
          doc = currentParams.requestDoc
          currentReq = WebOmi.omi.evaluateXPath(
            doc, "omi:omiEnvelope/*")[0] # FIXME: head

          newReq = WebOmi.omi.createOmi reqName, doc

          # copy attrs
          newReq.setAttribute attr.name,attr.value for attr in currentReq.attributes
          
          # copy childs
          while child = currentReq.firstChild
            newReq.appendChild child
            # firefox seems to remove the child from currentReq above,
            # but for compatibility
            if child == currentReq.firstChild
              currentReq.removeChild(child)

          # replace
          currentReq.parentNode.replaceChild newReq, currentReq

          # update internal state
          currentParams.request = reqName

          # special functionality for write request
          if reqName == "write"
            my.params.odf.update currentParams.odf
            #addValueToAll doc
          else if oldReqName == "write" # change from write
            #removeValueFromAll doc
            my.params.odf.update currentParams.odf

          reqName



    ttl      : # double
      updateSetterForAttr "ttl", "omi:omiEnvelope"
    callback : # Maybe String
      updateSetterForAttr "callback", "omi:omiEnvelope/*"
    requestID: # Maybe Int
      update : (newVal) ->
        o = WebOmi.omi
        doc = currentParams.requestDoc
        parentXPath = "omi:omiEnvelope/*"
        if currentParams.requestID != newVal
          parents = o.evaluateXPath doc, parentXPath
          if not parents?
            WebOmi.error "Tried to update requestID, but #{parentXPath} not found in", doc
          else
            existingIDs = o.evaluateXPath doc, "//omi:requestID"
            if newVal?
              if existingIDs.some((elem) -> elem.textContent.trim() == newVal.toString())
                return # exists
                # TODO multiple requestIDs
              else # add
                for parent in parents
                  id.parentNode.removeChild id for id in existingIDs
                  newId = o.createOmi "requestID", doc
                  idTxt = doc.createTextNode newVal.toString()
                  newId.appendChild idTxt
                  parent.appendChild newId
            else
              # remove
              id.parentNode.removeChild id for id in existingIDs

          currentParams.requestID = newVal
        newVal

    odf      :
      update : (paths) ->
        o = WebOmi.omi
        doc = currentParams.requestDoc
        if paths? && paths.length > 0
          obs = o.createOdfObjects doc

          for path in paths  # add
            odfTreeNode = $ jqesc path
            my.addPathToOdf odfTreeNode, obs

          if currentParams.msg
            msg = o.evaluateXPath(currentParams.requestDoc, "//omi:msg")[0]
            if not msg?
              my.params.msg.update currentParams.msg # calls odf update again
              # NOTE: dependency?, msg.update calls odf update
              return
            
            # remove old (safeguard)
            while msg.firstChild
              msg.removeChild msg.firstChild

            msg.appendChild obs

        else
          obss = WebOmi.omi.evaluateXPath(doc, "//odf:Objects")
          obs.parentNode.removeChild obs for obs in obss

        currentParams.odf = paths
        paths

      # path: String "Objects/path/to/node"
      add : (path) ->
        # imports
        o = WebOmi.omi
        fl = WebOmi.formLogic

        odfTreeNode = $ jqesc path
        req = currentParams.requestDoc

        if req?
          currentObjectsHead = o.evaluateXPath(req, '//odf:Objects')[0]
          if currentObjectsHead?
            # TODO: user edit conflict check
            my.addPathToOdf odfTreeNode, currentObjectsHead
          else if currentParams.msg
            objects = o.createOdfObjects req
            my.addPathToOdf odfTreeNode, objects
            msg = o.evaluateXPath(req, "//omi:msg")[0]
            if msg?
              msg.appendChild objects
            else
              WebOmi.error "error, msg not found: #{msg}"

        # update currentparams
        if currentParams.odf?
          currentParams.odf.push path
        else currentParams.odf = [path]

        path

      # path: String "Objects/path/to/node"
      remove : (path) ->
        # imports
        o = WebOmi.omi
        fl = WebOmi.formLogic

        req = currentParams.requestDoc
        if currentParams.msg and req?
          odfTreeNode = $ jqesc path

          odfObjects = o.evaluateXPath(req, '//odf:Objects')[0]
          if odfObjects?
            my.removePathFromOdf odfTreeNode, odfObjects

        # update currentparams
        if currentParams.odf?
          currentParams.odf = currentParams.odf.filter (p) -> p != path
        else currentParams.odf = []

        path

    interval : # Maybe Number
      updateSetterForAttr "interval", "omi:omiEnvelope/*"
    newest   : # Maybe Int
      updateSetterForAttr "newest", "omi:omiEnvelope/*"
    oldest   : # Maybe Int
      updateSetterForAttr "oldest", "omi:omiEnvelope/*"
    begin    : # Maybe Date
      updateSetterForAttr "begin", "omi:omiEnvelope/*"
    end      : # Maybe Date
      updateSetterForAttr "end", "omi:omiEnvelope/*"
    msg      : # Boolean whether message is included
      update : (hasMsg) ->
        o = WebOmi.omi
        doc = currentParams.requestDoc

        if hasMsg == currentParams.msg then return

        if hasMsg  # add
          #msgExists = o.evaluateXPath(currentParams.requestDoc, "")
          msg = o.createOmi "msg", doc

          # namespaces already set by createOmi and createOdf
          # msg.setAttribute "xmlns", "odf.xsd"

          requestElem = o.evaluateXPath(doc, "/omi:omiEnvelope/*")[0]
          if requestElem?
            requestElem.appendChild msg
            requestElem.setAttribute "msgformat", "odf"
            currentParams.msg = hasMsg
            my.params.odf.update currentParams.odf
            # FIXME dependency
          else
            WebOmi.error "ERROR: No request found"
            return # TODO: what

        else  # remove
          msg = o.evaluateXPath(doc, "/omi:omiEnvelope/*/omi:msg")
          # extra safe: remove all msgs
          m.parentNode.removeChild m for m in msg

          requestElem = o.evaluateXPath(doc, "/omi:omiEnvelope/*")[0]
          if requestElem?
            requestElem.removeAttribute "msgformat"

        currentParams.msg = hasMsg
        hasMsg

  # wrapper to update the ui with generated request
  my.generate = ->
    WebOmi.formLogic.setRequest currentParams.requestDoc



  # force load all parameters in the omiRequestObject and
  # set them in corresponding UI elements
  my.forceLoadParams = (omiRequestObject, useOldDoc=false) ->
    o = WebOmi.omi
    cp = currentParams

    for own key, newVal of omiRequestObject
      uiWidget = WebOmi.consts.ui[key]
      if uiWidget?
        uiWidget.set newVal


    # generate

    if not useOldDoc || not cp.requestDoc?
      cp.requestDoc = o.parseXml my.xmls.template

    newParams = $.extend {}, cp, omiRequestObject

    #currentParams = my.defaults.empty()

    # essential parameters
    if newParams.request? && newParams.request.length > 0 && newParams.ttl?

      # resolve update dependencies manually?
      # - No, currently they are handled directly from the relevant update functions
      for own key, thing of my.params
        thing.update newParams[key]
        WebOmi.debug "updated #{key}:", currentParams[key]
        
      my.generate()

    else if newParams.name == "empty"
      currentParams = omiRequestObject
      my.generate()
    else
      WebOmi.error(
        "tried to generate request, but missing a required parameter (name, ttl)", newParams
      )

    return null


  # @param fastforward: Boolean Whether to also send the request and update odfTree also
  my.readAll = (fastForward) ->
    my.forceLoadParams my.defaults.readAll()

    if fastForward
      WebOmi.formLogic.send(WebOmi.formLogic.buildOdfTreeStr)





  WebOmi # export module

# extend WebOmi
window.WebOmi = requestsExt(window.WebOmi || {})
