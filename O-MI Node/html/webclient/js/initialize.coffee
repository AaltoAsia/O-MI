
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
    my.responseDiv        = $ '.response .CodeMirror'
    my.responseDiv.hide()
    
    my.serverUrl    = $ '#targetService'
    my.odfTreeDom   = $ '#nodetree'
    my.requestSelDom= $ '.requesttree'
    my.readAllBtn   = $ '#readall'
    my.sendBtn      = $ '#send'
    my.resetAllBtn  = $ '#resetall'

    loc = window.location.href
    my.serverUrl.val loc.substr 0, loc.indexOf "html/"

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

    my.requestSelDom
      .jstree
        core :
          themes :
            icons : false
          multiple : false

    
    basicInput = (selector, validator= (a) -> a != "") ->
      ref : $ selector
      get :       -> @ref.val()
      set : (val) -> @ref.val val
      bindTo : (callback) ->
        @ref.on "input", =>
          val = @get()
          if validator val
            callback val
          else
            callback null
      

    # refs, setters, getters
    my.ui =
      request  : # String
        ref : my.requestSelDom
        set : (reqName) -> # Maybe string (request tag name)
          tree = @ref.jstree()
          if not tree.is_selected reqName
            tree.deselect_all()
            tree.select_node reqName, true, false
        get : ->
          @ref.jstree().get_selected[0]

      ttl      : # double
        basicInput '#ttl'
      callback : # Maybe string
        basicInput '#callback'
      requestID: # Maybe int
        basicInput '#requestID'
      odf      : # Array String paths
        ref : my.odfTreeDom
        get :        -> my.odfTree.get_selected()
        set : (vals) ->
          my.odfTree.deselect_all true
          if vals? and vals.length > 0
            my.odfTree.select_node node, true, false for node in vals
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

