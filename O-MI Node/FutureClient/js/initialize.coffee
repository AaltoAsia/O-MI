
# extend module webOmi; public vars
constsExt = ($, parent) ->

  # Module WebOmi constants
  my = parent.consts = {}

  my.codeMirrorSettings =
    mode: "text/xml"
    lineNumbers: true
    lineWrapping: true

  # private, [functions]
  afterWaits = []

  # use afterJquery for things that depend on const module
  my.afterJquery = (fn) -> afterWaits.push fn

  $ ->
    responseCMSettings = $.extend(
      readOnly : true
      , my.codeMirrorSettings
    )
    
    # initialize UI
    my.requestCodeMirror  = CodeMirror.fromTextArea $("#requestArea" )[0], my.codeMirrorSettings
    my.responseCodeMirror = CodeMirror.fromTextArea $("#responseArea")[0], responseCMSettings
    
    my.serverUrl  = $ '#targetService'
    my.odfTreeDom = $ '#nodetree'
    my.requestSel = $ '.requesttree'
    my.readAllBtn = $ '#readall'
    my.sendBtn    = $ '#send'

    my.odfTreeDom
      .jstree
        plugins : ["checkbox", "types"]
        types :
          default :
            icon : "odf-objects glyphicon glyphicon-tree-deciduous"
          object :
            icon : "odf-object glyphicon glyphicon-folder-open"
          objects :
            icon : "odf-objects glyphicon glyphicon-tree-deciduous"
          infoitem :
            icon : "odf-infoitem glyphicon glyphicon-apple"
        checkbox :
          three_state : false
          keep_selected_style : true # Consider false
          cascade : "up+undetermined"
          tie_selection : true
      .on "changed.jstree", (_, data) ->
        console.log data.node

    my.odfTree = my.odfTreeDom.jstree()


    my.requestSel
      .jstree
        core :
          themes :
            icons : false
          multiple : false
      .on "changed.jstree", (_, data) ->
        console.log data.node.id

    my.afterJquery = (fn) -> fn()

    fn() for fn in afterWaits


  parent # export module

# extend WebOmi
window.WebOmi = constsExt($, window.WebOmi || {})

# extend String
String.prototype.trim = String.prototype.trim || ->
  String(this).replace /^\s+|\s+$/g, ''

