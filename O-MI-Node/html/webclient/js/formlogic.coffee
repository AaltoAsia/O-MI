"use strict"
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

######################
# formLogic sub module
formLogicExt = ($, WebOmi) ->
  my = WebOmi.formLogic = {}

  # Sets xml or string to request field
  my.setRequest = (xml) ->
    mirror = WebOmi.consts.requestCodeMirror
    if not xml?
      mirror.setValue ""
    else if typeof xml == "string"
      mirror.setValue xml
    else
      mirror.setValue new XMLSerializer().serializeToString xml

    mirror.autoFormatAll()

  # Gets the current request (possibly having users manual edits) as XMLDocument
  my.getRequest = () ->
    str = WebOmi.consts.requestCodeMirror.getValue()
    WebOmi.omi.parseXml str

  # Do stuff with RequestDocument and automatically write it back
  # callback: Function () -> ()
  my.modifyRequest = (callback) ->
    req = my.getRequest() # RemoveMe
    callback()
    #my.setRequest _
    WebOmi.requests.generate()

  my.getRequestOdf = () ->
    WebOmi.error "getRequestOdf is deprecated"
    str = WebOmi.consts.requestCodeMirror.getValue()
    o.evaluateXPath(str, '//odf:Objects')[0]

  # Remove current response from its CodeMirror and hide it with animation
  my.clearResponse = (doneCallback) ->
    mirror = WebOmi.consts.responseCodeMirror
    mirror.setValue ""
    WebOmi.consts.responseDiv.slideUp complete : ->
      if doneCallback? then doneCallback()

  # Sets response (as a string or xml) and handles slide animation
  my.setResponse = (xml, doneCallback) ->
    mirror = WebOmi.consts.responseCodeMirror
    if typeof xml == "string"
      mirror.setValue xml
    else
      mirror.setValue new XMLSerializer().serializeToString xml
    mirror.autoFormatAll()
    # refresh as we "resize" so more text will become visible
    WebOmi.consts.responseDiv.slideDown complete : ->
      mirror.refresh()
      if doneCallback? then doneCallback()
    mirror.refresh()
  

  ########################################################
  # SUBSCRIPTION HISTORY CODE, TODO: move to another file?
  ########################################################
  # Subscription history of WebSocket and callback=0 Features

  # List of subscriptions that uses websocket and should be watched
  # Type: {String_RequestID : {
  #   receivedCount : Number,
  #   userSeenCount : Number,
  #   selector : Jquery,   # selector for sub history list
  #   responses : [String]
  #   }}
  my.callbackSubscriptions = {}
  
  # Set true when the next response should be rendered to main response area
  my.waitingForResponse = false

  # Set true when next response with requestID should be saved to my.callbackSubscriptions
  my.waitingForRequestID = false

  # whether callbackResponseHistoryModal is opened and the user can see the new results
  my.historyOpen = false

  consts = WebOmi.consts

  consts.afterJquery ->
    consts.callbackResponseHistoryModal = $ '.callbackResponseHistory'
    consts.callbackResponseHistoryModal
      .on 'shown.bs.modal', ->
        my.historyOpen = true
        my.updateHistoryCounter true
      .on 'hide.bs.modal', ->
        my.historyOpen = false
        my.updateHistoryCounter true
        $ '.tooltip' # hotfix: tooltip hiding was broken
          .tooltip 'hide'

    consts.responseListCollection  = $ '.responseListCollection'
    consts.responseListCloneTarget = $ '.responseList.cloneTarget'
    consts.historyCounter = $ '.label.historyCounter'

  # end afterjquery

  # toZero: should counter be reset to 0
  my.updateHistoryCounter = (toZero=false) ->
    update = (sub) ->
      if toZero
        sub.userSeenCount = sub.receivedCount

    ####################################
    # TODO: historyCounter
    #if my.historyOpen
    orginal = parseInt consts.historyCounter.text()
    sum = 0
    for own requestID, sub of my.callbackSubscriptions
      update sub
      sum += sub.receivedCount - sub.userSeenCount

    if sum == 0
      consts.historyCounter
        .text sum
        .removeClass "label-warning"
        .addClass "label-default"
    else
      consts.historyCounter
        .text sum
        .removeClass "label-default"
        .addClass "label-warning"

    if sum > orginal
      WebOmi.util.flash consts.historyCounter.parent()


    

  # Called when we receive relevant websocket response
  # response: String
  # returns: true if the response was consumed, false otherwise
  my.handleSubscriptionHistory = (responseString) ->
    # imports
    omi = WebOmi.omi
    util = WebOmi.util

    response = omi.parseXml responseString

    # get requestID
    maybeRequestID = Maybe omi.evaluateXPath(response, "//omi:requestID/text()")[0] # headOption
    requestID = (maybeRequestID.bind (idNode) ->
      textId = idNode.textContent.trim()
      if textId.length > 0
        Maybe parseInt(textId)
      else
        None
    ).__v
    if (requestID?)
      cbSub = my.callbackSubscriptions[requestID]
      if cbSub?
        cbSub.receivedCount += 1
      else
        # enable listing of forgotten callback requests
        if my.waitingForRequestID or not my.waitingForResponse
          my.waitingForRequestID = false
          my.callbackSubscriptions[requestID] =
            receivedCount : 1
            userSeenCount : 0
            responses : [responseString]
        else
          return false

    else if not my.waitingForResponse
      requestID = "not given"
      my.callbackSubscriptions[requestID] =
        receivedCount : 1
        userSeenCount : 0
        responses : [responseString]
    else
      return false

    getPath = (xmlNode) ->
      id = omi.getOdfId(xmlNode)
      if id? and id != "Objects"
        init = getPath xmlNode.parentNode
        init + "/" + id
      else
        id

    #createShortenedPath = (path) ->
    #  pathParts = path.split "/"
    #  shortenedParts = (part[0] + "…" for part in pathParts)
    #  lastI = pathParts.length - 1
    #  shortenedParts[lastI] = pathParts[lastI]
    #  shortenedParts.join "/"

    pathPrefixTrie = {}
    insertToTrie = (root, string) ->
      if string.length == 0
        root
      else
        [head,tail...] = string
        root[head] ?= {}
        insertToTrie root[head], tail

    createShortenedPath = (path) ->
      prefixShorted = getShortenedPath pathPrefixTrie,path
      [shortedInit..., _] = prefixShorted.split "/"
      [_..., originalLast] = path.split "/"
      shortedInit.push originalLast
      shortedInit.join "/"

    # return longest common prefix path
    getShortenedPath = (tree, path, shortening=false) ->
      if path.length == 0
        return ""

      keys = Object.keys tree
      [key, tail...] = path

      child = tree[key]
      if not child?
        WebOmi.debug "Error: prefix tree failure: does not exist"
        return

      if key == "/"
        return "/" + getShortenedPath child, tail

      if keys.length == 1
        if shortening
          return getShortenedPath child, tail, true
        else
          return "..." + getShortenedPath child, tail, true
      else
        return key + getShortenedPath child, tail



    getPathValues = (infoitemXmlNode) ->
      valuesXml = omi.evaluateXPath(infoitemXmlNode, "./odf:value")
      path = getPath infoitemXmlNode
      insertToTrie pathPrefixTrie, path

      [pathObject..., infoItemName] = path.split "/"
      for value in valuesXml
        path: path
        pathObject: pathObject.join '/'
        infoItemName: infoItemName
        shortPath: -> createShortenedPath path
        value: value
        stringValue: value.textContent.trim()

    # Utility function; Clone the element above and empty its input fields 
    # callback type: (clonedDom) -> void
    cloneElem = (target, callback) ->
      util.cloneElem target, (cloned) ->
        cloned.slideDown null, ->  # animation, default duration
          # readjusts the position because of size change (see modal docs)
          consts.callbackResponseHistoryModal.modal 'handleUpdate'

    # Move "Latest subscription" and "Older subscriptions"
    moveHistoryHeaders = (latestDom) ->
      olderH = consts.callbackResponseHistoryModal.find '.olderSubsHeader'
      latestDom.after olderH

    createHistory = (requestID) ->
      newList = cloneElem consts.responseListCloneTarget
      moveHistoryHeaders newList
      newList
        .removeClass "cloneTarget"
      newList.find '.requestID'
        .text requestID
      newList

    # return: jquery elem
    returnStatus = ( count, returnCodes ) ->
      returnCodes = [200] if !returnCodes[0]?

      #count = $ "<th/>" .text count
      row = $ "<tr/>"
        .addClass switch Math.floor(Math.max.apply(null, returnCodes)/100)
          when 2 then "success" # 2xx
          when 3 then "warning" # 3xx
          when 4 then "danger"  # 4xx
          else "warning"
        .addClass "respRet"
        .append($ "<th/>"
          .text count)
        .append($ "<th>Received callback</th>")
        .append($ "<th/>"
          .text returnCodes.join(','))
      row.tooltip
          #container: consts.callbackResponseHistoryModal
          title: "click to show the XML"
        .on 'click', do (row) -> -> # wrap closure; Show the response xml instead of list
          if (row.data 'dataRows')?
            tmpRow = row.nextUntil '.respRet'
            tmpRow.remove()
            row.after row.data 'dataRows'

            row.removeData 'mirror'
            row.removeData 'dataRows'
            $ '.tooltip' # hotfix: tooltip hiding was broken
              .remove()
          else
            dataRows = row.nextUntil '.respRet'
            row.data 'dataRows', dataRows.clone()
            dataRows.remove()

            tmpTr = $ '<tr/>'
            codeMirrorContainer = $ '<td colspan=3/>'
            tmpTr.append codeMirrorContainer
            row.after tmpTr
              
            responseCodeMirror = CodeMirror(codeMirrorContainer[0], WebOmi.consts.responseCMSettings)
            responseCodeMirror.setValue responseString
            responseCodeMirror.autoFormatAll()
            row.data 'mirror', responseCodeMirror
          null
          
          ## Old function was to close the history and show response in the main area and flash it
          #
          #WebOmi.formLogic.setResponse responseString, ->
          #  url = window.location.href                   #Save down the URL without hash.
          #  window.location.href = "#response"           #Go to the target element.
          #  window.history.replaceState(null,null,url)   #Don't like hashes. Changing it back.
          #  WebOmi.util.flash WebOmi.consts.responseDiv
          #WebOmi.consts.callbackResponseHistoryModal.modal 'hide'
      row


    htmlformat = (pathValues) ->

      for pathValue in pathValues
        row = $ "<tr/>"
          .append $ "<td/>"
          .append($ "<td/>"
            .append($('<span class="hidden-lg hidden-md" />').text pathValue.shortPath)
            .append(
              $('<span class="hidden-xs hidden-sm" />')
              .text pathValue.pathObject + '/'
              .append($('<b/>').text pathValue.infoItemName)
            )
            .tooltip
              #container: "body"
              container: consts.callbackResponseHistoryModal
              title: pathValue.path
          )
          .append($ "<td/>"
            .tooltip
              #container: "body"
              title: pathValue.value.attributes.dateTime.value
            .append($("<code/>").text pathValue.stringValue)
          )
        row


    addHistory = (requestID, pathValues, returnCodes) ->
      # Note: existence of this is handled somewhere above
      callbackRecord = my.callbackSubscriptions[requestID]
      
      responseList =
        if callbackRecord.selector? and callbackRecord.selector.length > 0
          callbackRecord.selector
        else
          newHistory = createHistory requestID
          newHistory.slideDown()
          my.callbackSubscriptions[requestID].selector = newHistory
          newHistory

      dataTable = responseList.find ".dataTable"

      returnS = returnStatus callbackRecord.receivedCount, returnCodes
      
      pathVals = [].concat returnS, htmlformat pathValues
      pathVals = $ $(pathVals).map -> this.toArray()

      if my.historyOpen
        util.animatedShowRow pathVals, (-> dataTable.prepend pathVals)
          #pathVals.last().nextAll 'tr'
          #  .each ->
          #    if $(this).data('mirror')?.refresh?

      else
        dataTable.prepend pathVals


    infoitems = omi.evaluateXPath(response, "//odf:InfoItem")

    infoItemPathValues = ( getPathValues info for info in infoitems )
    pathValues = [].concat infoItemPathValues...

    maybeReturnCodes = omi.evaluateXPath(response, "//omi:return/@returnCode")
    trimmedCodes = (codeNode.textContent.trim() for codeNode in maybeReturnCodes)
    returnCodes = (parseInt(textCode) for textCode in trimmedCodes when textCode.length > 0)
        

    addHistory requestID, pathValues, returnCodes

    # return true if request is not needed for the main area or was found on existing
    not my.waitingForResponse or my.callbackSubscriptions[requestID]?

  

  
  my.createWebSocket = (onopen, onclose, onmessage, onerror) -> # Should socket be created automaticly for my or 
    WebOmi.debug "Creating WebSocket."
    consts = WebOmi.consts
    server = consts.serverUrl.val()
    socket = new WebSocket(server)
    socket.onopen = onopen
    socket.onclose = () -> onclose
    socket.onmessage = onmessage
    socket.onerror = onerror
    my.socket = socket
  
  # send, callback is called with response text if successful
  my.send = (callback) ->
    consts = WebOmi.consts
    my.clearResponse()
    server  = consts.serverUrl.val()
    request = consts.requestCodeMirror.getValue()
    if server.startsWith("ws://") || server.startsWith("wss://")
      my.wsSend request,callback
    else
      my.httpSend callback

  # String -> void
  my.wsCallbacks = []

  # id for canceling the keepalive scheduler
  my.keepAliveScheduler = null

  my.startKeepAlive = ->
    if !my.keepAliveScheduler?
      my.keepAliveScheduler = window.setInterval (() -> my.wsSend ""), 30000

  my.stopKeepAlive = ->
    if my.keepAliveScheduler?
      window.clearInterval my.keepAliveScheduler
      my.keepAliveScheduler = null

  my.wsSend = (request,callback) ->
    if !my.socket || my.socket.readyState != WebSocket.OPEN
      onopen = () ->
        WebOmi.debug "WebSocket connected."
        my.startKeepAlive()
        my.wsSend request,callback

      onclose = () ->
        WebOmi.debug "WebSocket disconnected."
        my.stopKeepAlive()

      onerror = (error) ->
        WebOmi.debug "WebSocket error: ",error
        my.stopKeepAlive()

      onmessage = my.handleWSMessage

      my.createWebSocket onopen, onclose, onmessage, onerror
    else
      if request == ""
        WebOmi.debug "Sending keepalive via WebSocket."
      else
        WebOmi.debug "Sending request via WebSocket."
        # Next message should be rendered to main response area
        my.waitingForResponse = true


      # Note: assume that the next response is for this request
      if callback?
        my.wsCallbacks.push callback

      # Check if request is zero callback request
      omi = WebOmi.omi
      maybeParsedXml = Maybe omi.parseXml(request)
      maybeVerbXml =
        maybeParsedXml.bind (parsedXml) ->
          verbResult = omi.evaluateXPath(parsedXml, "//omi:omiEnvelope/*")[0]
          Maybe verbResult

      maybeVerbXml.fmap (verbXml) ->
        verb = verbXml.tagName
        maybeCallback = Maybe verbXml.attributes.callback
        maybeInterval = Maybe verbXml.attributes.interval

        isSubscriptionReq = maybeCallback.exists((c) -> c.value is "0") and
          (verb == "omi:read" or verb == "read") and
          maybeInterval.isDefined

        # done by the callback parameter
        #isReadAll = verbXml.children[0].children[0].children.length == 0

        if isSubscriptionReq
          # commented because user might be waiting for some earlier response
          #y.waitingForResponse = false
          
          my.waitingForRequestID = true


      my.socket.send(request)

  my.httpSend = (callback) ->
    WebOmi.debug "Sending request with HTTP POST."
    consts = WebOmi.consts
    server  = consts.serverUrl.val()
    request = consts.requestCodeMirror.getValue()
    consts.progressBar.css "width", "50%"
    $.ajax
      type: "POST"
      url: server
      data: request
      contentType: "text/xml"
      processData: false
      dataType: "text"
      #complete: -> true
      error: (response) ->
        consts.progressBar.css "width", "100%"
        my.setResponse response.responseText
        consts.progressBar.css "width", "0%"
        consts.progressBar.hide()
        window.setTimeout (-> consts.progressBar.show()), 2000
        # TODO: Tell somewhere the "Bad Request" etc
        # response.statusText
      success: (response) ->
        consts.progressBar.css "width", "100%"
        my.setResponse response
        consts.progressBar.css "width", "0%"
        consts.progressBar.hide()
        window.setTimeout (-> consts.progressBar.show()), 2000
        callback response if callback?
  
  my.handleWSMessage = (message) ->
    consts = WebOmi.consts
    #Check if response to subscription and put into subscription response view
    response = message.data
    if response.length == 0
      return
    else if not my.handleSubscriptionHistory response
      consts.progressBar.css "width", "100%"
      my.setResponse response
      consts.progressBar.css "width", "0%"
      consts.progressBar.hide()
      window.setTimeout (-> consts.progressBar.show()), 2000
      my.waitingForResponse = false
    else
      my.updateHistoryCounter()

    cb(response) for cb in my.wsCallbacks
    my.wsCallbacks = []

    
  objChildren = WebOmi.omi.getObjectChildren

  # generate jstree data
  my.OdfToJstree = genData = (xmlNode, parentPath) ->
    switch xmlNode.nodeName
      when "Objects"
        name = xmlNode.nodeName
        id   : idesc name
        text : name
        state : {opened : true}
        type : "objects"
        children :
          genData(child, name) for child in objChildren(xmlNode)
      when "Object"
        name = WebOmi.omi.getOdfId(xmlNode) # FIXME: get
        path = "#{parentPath}/#{name}"
        id   : idesc path
        text : name
        type : "object"
        children :
          [genData {nodeName:"description"}, path].concat (genData(child, path) for child in objChildren(xmlNode))
      when "InfoItem"
        name = WebOmi.omi.getOdfId(xmlNode) # FIXME: get
        path = "#{parentPath}/#{name}"
        id   : idesc path
        text : name
        type : if xmlNode.attributes.method? then "method" else "infoitem"
        children :
          [
            (genData {nodeName:"description"}, path),
            (genData {nodeName:"MetaData"}, path)
          ]
      when "MetaData"
        path = "#{parentPath}/MetaData"
        id   : idesc path
        text : "MetaData"
        type : "metadata"
        children : []
      when "description"
        path = "#{parentPath}/description"
        id   : idesc path
        text : "description"
        type : "description"
        children : []



  # recursively build odf jstree from the Objects xml node
  my.buildOdfTree = (objectsNode) ->
    # imports
    tree = WebOmi.consts.odfTree
    	  
    treeData = genData objectsNode
    tree.settings.core.data = [treeData]
    tree.refresh()


  # parse xml string and build odf jstree
  my.buildOdfTreeStr = (responseString) ->
    omi = WebOmi.omi

    parsed = omi.parseXml responseString # FIXME: get

    objectsArr = omi.evaluateXPath parsed, "//odf:Objects"

    if objectsArr.length != 1
      WebOmi.error "failed to get single Objects odf root"
    else
      my.buildOdfTree objectsArr[0] # head, checked above


  WebOmi # export

# extend WebOmi
window.WebOmi = formLogicExt($, window.WebOmi || {})




##########################
# Intialize widgets: connect events, import
((consts, requests, formLogic) ->
  consts.afterJquery ->

    # Buttons

    consts.readAllBtn
      .on 'click', -> requests.readAll(true)
    consts.sendBtn
      .on 'click', -> formLogic.send()

    consts.resetAllBtn
      .on 'click', ->
        requests.forceLoadParams requests.defaults.empty()
        closetime = 1500 # ms to close Objects jstree
        for child in consts.odfTree.get_children_dom 'Objects'
          consts.odfTree.close_all child, closetime
        formLogic.clearResponse()
        $('.clearHistory').trigger 'click'

    consts.sortOdfTreeCheckbox
      .on 'change', ->
        tree = consts.odfTreeDom.jstree()
        if this.checked
          tree.settings.sort = (a,b) ->
            if this.get_text(a) > this.get_text(b) then 1 else -1
          root = tree.get_node $ "#Objects"
          tree.sort root, true
          tree.redraw_node root, true
        else
          tmpTree.settings.sort = (a,b) -> -1

    consts.convertXmlCheckbox
      .on 'change', ->
        if this.checked
          window.requestXml = WebOmi.consts.requestCodeMirror.getValue()
          window.responseXml = WebOmi.consts.responseCodeMirror.getValue()

          jsonRequest = WebOmi.jsonConverter.parseOmiEnvelope(WebOmi.omi.parseXml(window.requestXml))

          if not jsonRequest?
            alert("Invalid O-MI/O-DF")
          else
            WebOmi.consts.responseCodeMirror.removeOverlay WebOmi.consts.URLHighlightOverlay
            WebOmi.consts.requestCodeMirror.setOption("mode","application/json")
            WebOmi.consts.responseCodeMirror.setOption("mode","application/json")
            WebOmi.consts.requestCodeMirror.setOption("readOnly",true)

            formLogic.setRequest = (json) ->
              mirror = WebOmi.consts.requestCodeMirror
              if not json?
                mirror.setValue ""
              else if typeof json == "string"
                mirror.setValue json
              else
                window.requestXml = new XMLSerializer().serializeToString json
                mirror.setValue JSON.stringify(WebOmi.jsonConverter.parseOmiEnvelope(json), null, 2)

            formLogic.setRequest( JSON.stringify(jsonRequest, null, 2))
            
            formLogic.setResponse = (xml, doneCallback) ->
              mirror = WebOmi.consts.responseCodeMirror
              if typeof xml == "string"
                window.responseXml = xml
                mirror.setValue JSON.stringify(WebOmi.jsonConverter.parseOmiEnvelope(xml), null, 2)
              else
                window.responseXml = new XMLSerializer().serializeToString xml
                mirror.setValue JSON.stringify(WebOmi.jsonConverter.parseOmiEnvelope(xml), null, 2)
              mirror.autoFormatAll()
              # refresh as we "resize" so more text will become visible
              WebOmi.consts.responseDiv.slideDown complete : ->
                mirror.refresh()
                if doneCallback? then doneCallback()
              mirror.refresh()

            if window.responseXml?
              formLogic.setResponse(window.responseXml)


            formLogic.send = (callback) ->
              consts = WebOmi.consts
              formLogic.clearResponse()
              server  = consts.serverUrl.val()
              request = window.requestXml #consts.requestCodeMirror.getValue()
              if server.startsWith("ws://") || server.startsWith("wss://")
                formLogic.wsSend request,callback
              else
                formLogic.httpSend callback



        else
          WebOmi.consts.requestCodeMirror.setOption("mode","xml")
          WebOmi.consts.responseCodeMirror.setOption("mode","xml")
          WebOmi.consts.requestCodeMirror.setOption("readOnly", false)
          
          formLogic.setRequest = (xml) ->
            mirror = WebOmi.consts.requestCodeMirror
            if not xml?
              mirror.setValue ""
            else if typeof xml == "string"
              mirror.setValue xml
            else
              mirror.setValue new XMLSerializer().serializeToString xml
            
            mirror.autoFormatAll()

          formLogic.setRequest(window.requestXml)

          formLogic.setResponse = (xml, doneCallback) ->
            mirror = WebOmi.consts.responseCodeMirror
            if typeof xml == "string"
              mirror.setValue xml
            else
              mirror.setValue new XMLSerializer().serializeToString xml
            mirror.autoFormatAll()
            # refresh as we "resize" so more text will become visible
            WebOmi.consts.responseDiv.slideDown complete : ->
              mirror.refresh()
              if doneCallback? then doneCallback()
            mirror.refresh()

          formLogic.send = (callback) ->
            consts = WebOmi.consts
            formLogic.clearResponse()
            server  = consts.serverUrl.val()
            request = consts.requestCodeMirror.getValue()
            if server.startsWith("ws://") || server.startsWith("wss://")
              formLogic.wsSend request,callback
            else
              formLogic.httpSend callback
          
          formLogic.setResponse(window.responseXml)

          WebOmi.consts.responseCodeMirror.addOverlay WebOmi.consts.URLHighlightOverlay


    # TODO: maybe move these to centralized place consts.ui._.something
    # These widgets have a special functionality, others are in consts.ui._

    # Odf tree
    consts.ui.odf.ref
      .on "changed.jstree", (_, data) ->
        switch data.action
          when "select_node"
            odfTreePath = data.node.id
            formLogic.modifyRequest -> requests.params.odf.add odfTreePath
            true
          when "deselect_node"
            odfTreePath = data.node.id
            formLogic.modifyRequest -> requests.params.odf.remove odfTreePath
            $ jqesc odfTreePath
              .children ".jstree-children"
              .find ".jstree-node"
              .each (_, node) ->
                consts.odfTree.deselect_node node, true
          else true

    # Request select tree
    consts.ui.request.ref
      .on "select_node.jstree", (_, data) ->
        # TODO: should ^ this ^ be changed "changed.jstree" event because it can be prevented easily
        # if data.action != "select_node" then return

        reqName = data.node.id
        WebOmi.debug reqName

        # force selection to readOnce
        if reqName == "readReq"
          consts.ui.request.set "read" # should trigger a new event
        else
          # update ui enabled/disabled settings (can have <msg>, interval, newest, oldest, timeframe?)
          ui = WebOmi.consts.ui

          readReqWidgets = [ui.newest, ui.oldest, ui.begin, ui.end]
          isReadReq = switch reqName
            when "readAll", "read", "readReq" then true
            else false
          isRequestIdReq = switch reqName
            when"cancel", "poll" then true
            else false

          for input in readReqWidgets
            input.ref.prop('disabled', not isReadReq)
            input.set null
            input.ref.trigger "input"

          # TODO: better way of removing the disabled settings from the request xml
          ui.requestID.ref.prop('disabled', not isRequestIdReq)
          if not isRequestIdReq
            ui.requestID.set null
            ui.requestID.ref.trigger "input"
          isCallbackReq = reqName != "cancel"
          ui.callback.ref.prop('disabled', not isCallbackReq)
          if not isCallbackReq
            ui.callback.set null
            ui.callback.ref.trigger "input"
          if reqName == "subscription"
            if consts.serverUrl.val().startsWith("ws")
              ui.callback.set "0"
            ui.callback.ref.trigger "input"
          else
            ui.callback.set null
            ui.callback.ref.trigger "input"
          ui.requestID.ref.prop('disabled', not isRequestIdReq)
          ui.interval.ref.prop('disabled', reqName != 'subscription')
          ui.interval.set null
          if reqName == "subscription"
            ui.interval.set -1
          ui.interval.ref.trigger "input"

          formLogic.modifyRequest ->
            requests.params.name.update reqName
            # update msg status
            newHasMsg = requests.defaults[reqName]().msg
            requests.params.msg.update newHasMsg

    # for basic input fields

    makeRequestUpdater = (input) ->
      (val) ->
        formLogic.modifyRequest -> requests.params[input].update val

    for own inputVar, controls of consts.ui
      if controls.bindTo?
        controls.bindTo makeRequestUpdater inputVar

    null # no return



)(window.WebOmi.consts, window.WebOmi.requests, window.WebOmi.formLogic)

$ ->
  $('.optional-parameters > a')
    .on 'click', () ->
      glyph = $(this).find('span.glyphicon')
      if glyph.hasClass('glyphicon-menu-right')
        glyph.removeClass('glyphicon-menu-right')
        glyph.addClass('glyphicon-menu-down')
      else
        glyph.removeClass('glyphicon-menu-down')
        glyph.addClass('glyphicon-menu-right')


window.FormLogic = "ready"

