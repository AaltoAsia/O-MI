
# formLogic sub module
formLogicExt = ($, WebOmi) ->
  my = WebOmi.formLogic = {}

  my.send = (callback) ->
    server  = WebOmi.consts.serverUrl.val()
    request = WebOmi.consts.requestCodeMirror.getValue()
    $.ajax
      type: "POST"
      url: server
      data: request
      contentType: "text/xml"
      processData: false
      dataType: "text"
      #success: -> true
      #error: -> true
      success: (response) ->
        WebOmi.consts.responseCodeMirror.setValue response
        WebOmi.consts.responseCodeMirror.autoFormatAll()
        callback(response) if (callback?)

  # recursively build odf jstree from the Objects xml node
  my.buildOdfTree = (objectsNode) ->
    # imports
    tree = WebOmi.consts.odfTree
    evaluateXPath = WebOmi.omi.evaluateXPath

    objChildren = (xmlNode) ->
      evaluateXPath xmlNode, './odf:InfoItem | ./odf:Object'

    # generate jstree data
    genData = (xmlNode, parentPath) ->
      switch xmlNode.nodeName
        when "Objects"
          name = xmlNode.nodeName
          id   : name
          text : name
          state : {opened : true}
          type : "objects"
          children :
            genData(child, name) for child in objChildren(xmlNode)
        when "Object"
          name = evaluateXPath(xmlNode, './odf:id')[0].textContent.trim() # FIXME: head
          path = "#{parentPath}/#{name}"
          id   : path
          text : name
          type : "object"
          children :
            genData(child, path) for child in objChildren(xmlNode)
        when "InfoItem"
          name = xmlNode.attributes.name.value # FIXME: get
          path = "#{parentPath}/#{name}"
          id   : path
          text : name
          type : "infoitem"
          children : []

    treeData = genData objectsNode
    console.log treeData
    tree.settings.core.data = [treeData]
    tree.refresh()

  # parse xml string and build odf jstree
  my.buildOdfTreeStr = (responseString) ->
    omi = WebOmi.omi

    parsed = omi.parseXmlResponse responseString

    objectsArr = omi.evaluateXPath parsed, "//odf:Objects"

    if objectsArr.length != 1
      alert "failed to get single Objects odf root"

    my.buildOdfTree objectsArr[0]




  WebOmi # export

# extend WebOmi
window.WebOmi = formLogicExt($, window.WebOmi || {})

# Intialize events, import
((consts, requests, formLogic) ->
  consts.afterJquery ->
    consts.readAllBtn
      .on 'click', -> requests.readAll(true)
    consts.sendBtn
      .on 'click', -> formLogic.send()

)(window.WebOmi.consts, window.WebOmi.requests, window.WebOmi.formLogic)

$ ->
  $('.optional-parameters .panel-heading a')
    .on 'click', () ->
      glyph = $(this).children('span')
      if glyph.hasClass('glyphicon-menu-right')
        glyph.removeClass('glyphicon-menu-right')
        glyph.addClass('glyphicon-menu-down')
      else
        glyph.removeClass('glyphicon-menu-down')
        glyph.addClass('glyphicon-menu-right')

