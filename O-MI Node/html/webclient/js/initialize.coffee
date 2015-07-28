
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
    my.progressBar  = $ '.response .progress-bar'

    loc = window.location.href
    my.serverUrl.val loc.substr 0, loc.indexOf "html/"

    objectsIcon  = "glyphicon glyphicon-tree-deciduous"
    objectIcon   = "glyphicon glyphicon-folder-open"
    infoItemIcon = "glyphicon glyphicon-apple"

    my.odfTreeDom
      .jstree
        plugins : ["checkbox", "types", "contextmenu"]
        core :
          error : (msg) -> console.log msg # TODO: remove debug
          force_text : true
          check_callback : true
        types :
          default :
            icon : "odf-objects " + objectsIcon
            valid_children : ["object"]
          object :
            icon : "odf-object " + objectIcon
            valid_children : ["object", "infoitem"]
          objects :
            icon : "odf-objects " + objectsIcon
            valid_children : ["object"]
          infoitem :
            icon : "odf-infoitem " + infoItemIcon
            valid_children : []
        checkbox :
          three_state : false
          keep_selected_style : true # Consider false
          cascade : "up+undetermined"
          tie_selection : true
        contextmenu :
          show_at_node : true
          items : (target) ->
            helptxt :
              label : "For write request:"
              icon  : "glyphicon glyphicon-pencil"
              # _disabled : true
              action : -> my.ui.request.set "write", false
              separator_after : true
            add_info :
              label : "Add an InfoItem"
              icon  : infoItemIcon
              _disabled :
                my.odfTree.settings.types[target.type].valid_children.indexOf("infoitem") == -1
              action : (data) -> # element, item, reference
                tree = WebOmi.consts.odfTree
                parent = tree.get_node data.reference
                name = window.prompt "Enter a name for the new InfoItem:", "MyInfoItem"
                idName = idesc name
                path = "#{parent.id}/#{idName}"

                if $(jqesc path).length > 0
                  return # already exists

                tree.create_node parent.id,
                  id   : path
                  text : name
                  type : "infoitem"
                , "first", ->
                  tree.open_node parent, null, 500
                  tree.select_node path
            add_obj :
              label : "Add an Object"
              icon  : objectIcon
              _disabled : my.odfTree.settings.types[target.type].valid_children.indexOf("object") == -1
              action : (data) -> # element, item, reference
                tree = WebOmi.consts.odfTree
                parent = tree.get_node data.reference
                name = window.prompt "Enter an identifier for the new Object:", "MyObject"
                idName = idesc name
                path = "#{parent.id}/#{idName}"

                if $(jqesc path).length > 0
                  return # already exists
                  return # already exists

                tree.create_node parent,
                  id   : path
                  text : name
                  type : "object"
                , "first", ->
                  tree.open_node parent, null, 500
                  tree.select_node path
                


    my.odfTree = my.odfTreeDom.jstree()
    my.odfTree.set_type('Objects', 'objects')

    my.requestSelDom
      .jstree
        core :
          themes :
            icons : false
          multiple : false

    my.requestSel = my.requestSelDom.jstree()


    # tooltips & popovers
    $('[data-toggle="tooltip"]').tooltip()

    # private
    requestTip = (selector, text) ->
      my.requestSelDom.find selector
        .children "a"
        .tooltip
          title : text
          placement : "right"
          container : "body"
          trigger : "hover"

    
    requestTip "#readReq", "Requests that can be used to get data from server. Use one of the below cases."
    requestTip "#read", "Single request for latest or old data with various parameters."
    requestTip "#subscription", "Create a subscription for data with given interval. Returns requestID which can be used to poll or cancel"
    requestTip "#poll", "Request and empty buffered data for callbackless subscription."
    requestTip "#cancel", "Cancel and remove an active subscription."
    requestTip "#write", "Write new data to the server. NOTE: Right click the above odf tree to create new elements."


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
        set : (reqName, preventEvent=true) -> # Maybe string (request tag name)
          tree = @ref.jstree()
          if not tree.is_selected reqName
            tree.deselect_all()
            tree.select_node reqName, preventEvent, false
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
        set : (vals, preventEvent=true) ->
          my.odfTree.deselect_all true
          if vals? and vals.length > 0
            my.odfTree.select_node node, preventEvent, false for node in vals
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
# adds one # in the beginning and \\ in front of every special symbol and spaces to underscore
window.jqesc = (mySel) -> '#' + mySel.replace( /(:|\.|\[|\]|,|\/)/g, "\\$1" ).replace( /( )/g, "_" )
# make a valid id, convert space to underscore
window.idesc = (myId) -> myId.replace( /( )/g, "_" )

# extend String (FIXME)
String.prototype.trim = String.prototype.trim || ->
  String(this).replace /^\s+|\s+$/g, ''

