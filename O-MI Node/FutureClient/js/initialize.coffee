
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

    request  : (reqName) -> # Maybe string (request tag name)
      if not currentParams.request?
        my.loadParams my.defaults[reqName]
      else


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

    my.odfTree = my.odfTreeDom.jstree()
    my.odfTree.set_type('Objects', 'objects')

    my.requestSel
      .jstree
        core :
          themes :
            icons : false
          multiple : false
      .on "changed.jstree", (e, data) ->
        console.log data.node.id

    basicInput = (selector) ->
      ref : $ selector
      get :       -> @ref.val()
      set : (val) -> @ref.val val

    # refs, setters, getters
    my.ui =
      ttl      : # double
        basicInput '#ttl'
      callback : # Maybe string
        basicInput '#callback'
      requestID: # Maybe int
        basicInput '#requestID'
      odf      : # Array String paths
        ref : my.odfTreeDom
        get :        -> my.odfTree.get_selected()
        set : (vals) -> my.odfTree.select_node node for node in vals
      interval : # Maybe number
        basicInput '#interval'
      newest   : # Maybe int
        basicInput '#newest'
      oldest   : # Maybe int
        basicInput '#oldest'
      begin    : # Maybe Date
        basicInput '#begin'
      end      : # Maybe Date
        basicInput '#end'
      requestDoc: # Maybe xml dom document
        ref : my.requestCodeMirror
        get :       -> WebOmi.formLogic.getRequest()
        set : (val) -> WebOmi.formLogic.setRequest val


    # callbacks
    my.afterJquery = (fn) -> fn()
    fn() for fn in afterWaits
    # end of jquery init

  parent # export module

# extend WebOmi
window.WebOmi = constsExt($, window.WebOmi || {})

# escaped jquery identifier
# adds one # in the beginning and \\ in front of every special symbol
window.jqesc = (mySel) -> '#' + mySel.replace( /(:|\.|\[|\]|,|\/)/g, "\\$1" )

# extend String (FIXME)
String.prototype.trim = String.prototype.trim || ->
  String(this).replace /^\s+|\s+$/g, ''

