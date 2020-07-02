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


###############
# UTILITY FUNCTIONS
# extend module webOmi; public helper functions, TODO: move to other file?
utilExt = ($, parent) ->

  my = parent.util = {}

  #private, https+http + "0"-callback
  my.urlmatch = /^(0$|(https?|wss?):\/\/((((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:)*@)?(((\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5]))|((([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.)+(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.?)|(localhost))(:\d*)?)(\/((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)+(\/(([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)*)*)?)?(\?((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|[\uE000-\uF8FF]|\/|\?)*)?(\#((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|\/|\?)*)?(?:<|"|\s|$))/i

  #save the old version just in case
  #/^(https?|ftp):\/\/(((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:)*@)?(((\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])\.(\d|[1-9]\d|1\d\d|2[0-4]\d|25[0-5]))|((([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|\d|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.)+(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])*([a-z]|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])))\.?)(:\d*)?)(\/((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)+(\/(([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)*)*)?)?(\?((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|[\uE000-\uF8FF]|\/|\?)*)?(\#((([a-z]|\d|-|\.|_|~|[\u00A0-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF])|(%[\da-f]{2})|[!\$&'\(\)\*\+,;=]|:|@)|\/|\?)*)?(?:<|"|\s|$)/i


  ## do syntax in coffee! :D
  # ma.bind (a) ->
  #   mb.bind (b) ->
  #     mc.bind (c) ->
  #       ret a+b+c
  #
  #do [
  #  bind a : ma,
  #  bind b : mb,
  #  bind c : mc,
  #  (x) -> ret x.a + x.b + x.c
  #]
  # not yet implemented

  my.validators = validators = {}

  # Validators,
  # minimal Maybe/Option operations
  # return null if invalid else the extracted value

  # in: string, out: string
  validators.nonEmpty = (s) ->
    if s != "" then s else null

  # in: string, out: number
  validators.number   = (s) ->
    if not s? then return s
    # special user experience enchancement:
    # convert ',' -> '.'
    a = s.replace(',', '.')
    if $.isNumeric a then parseFloat a else null

  # in: number, out: number
  validators.integer  = (x) ->
    if x? and x % 1 == 0 then x else null

  # in: number, out: number
  validators.greaterThan = (y) -> (x) ->
    if x? and x > y then x else null

  # in: number, out: number
  validators.greaterThanEq = (y) -> (x) ->
    if x? and x >= y then x else null

  # in: any, out: any
  validators.equals = (y) -> (x) ->
    if x? and x == y then x else null

  # in: t->t, t->t, ... ; out: t->t
  # returns function that tests its input with all the arguments given to this function
  validators.or = (vs...) -> (c) ->
    if vs.length == 0 then return null
    for v in vs
      res = v c
      if res? then return res
    null

  validators.url = (s) ->
    if my.urlmatch.test s
      s
    else null


  # Clone the element above and empty its input fields 
  # callback type: (clonedDom) -> void
  my.cloneAbove = (target, callback) ->
    my.cloneElem target.prev(), callback
      
  my.cloneElem = (target, callback) ->

    cloned = target.clone()
    cloned.find("input").val ""  # empty all cloned inputs
    cloned.hide()  # make unvisible for animation

    target.after cloned  # insert after the target

    callback? cloned

  # Yellow flash for a second
  my.flash = (jqueryElem) ->
    jqueryElem.addClass 'flash'
    window.setTimeout (
      -> jqueryElem.removeClass 'flash'
    ), 1000

  my.animatedShowRow = (jqueryElem, insertFunction=null, callback=null) ->
    if jqueryElem.length is 0 then return

    ## SKIP
    insertFunction?()
    callback?()
    return
  #  # FIXME: SLIDEDOWN() BREAKS ALL OPEN CODEMIRRORS BELOW

  #  # table rows cannot be animated but td > div can be
  #  tds = jqueryElem.find 'td,th'
  #    .wrapInner '<div style="display: none;" />'

  #  # insert elements now as they are hidden
  #  insertFunction?()

  #  # animate padding also
  #  padding = $(tds[0]).css ['padding-top', 'padding-bottom']
  #  tds
  #    .css 'padding-top', 0
  #    .css 'padding-bottom', 0
  #    .animate padding , duration: 800

  #  # callback should be called once only
  #  callMade = false

  #  jqueryElem.parent()
  #    .find 'td > div, th > div'
  #    .slideDown 800, ->
  #      set = $ this
  #      # Remove extra divs
  #      set.replaceWith set.contents()
  #      if not callMade
  #        callback?()
  #        callMade = true

  parent

# extend module webOmi; public vars
constsExt = ($, parent, util) ->


  # Module WebOmi constants
  my = parent.consts = {}

  my.codeMirrorSettings =
    mode: "text/xml"
    lineNumbers: true
    lineWrapping: true
    viewportMargin: 150


  my.icon =
    objects  : "glyphicon glyphicon-tree-deciduous"
    object   : "glyphicon glyphicon-folder-open"
    method   : "glyphicon glyphicon-flash"
    infoitem : "glyphicon glyphicon-apple"
    metadata : "glyphicon glyphicon-info-sign"
    description : "glyphicon glyphicon-info-sign"

  my.addOdfTreeNode = (parent, path, name, treeTypeName, callback=null) ->
    tree = WebOmi.consts.odfTree
    tree.create_node parent,
      id   : path
      text : name
      type : treeTypeName
    , "first"
    , (node) ->
      tree.open_node parent, null, 500
      callback? node


  # private
  openOdfContextmenu = (target) ->
    createNode = (particle, odfName, treeTypeName, defaultId) ->
      label : "Add #{particle} #{odfName}"
      icon  : my.icon[treeTypeName]

      _disabled : # if not exists in valid_children 
        my.odfTree.settings.types[target.type].valid_children.indexOf(treeTypeName) == -1

      action : (data) -> # element, item, reference
        tree = WebOmi.consts.odfTree
        parent = tree.get_node data.reference
        name =
          if defaultId?
            window.prompt "Enter a name for the new #{odfName}:", defaultId
          else
            odfName
        idName = idesc name
        path = "#{parent.id}/#{idName}"

        if $(jqesc path).length > 0
          tree.select_node path
          return # already exists
        else
          my.addOdfTreeNode parent, path, name, treeTypeName



    helptxt :
      label : "For write request:"
      icon  : "glyphicon glyphicon-pencil"
      # _disabled : true
      action : -> my.ui.request.set "write", false
      separator_after : true
    add_info :
      $.extend (createNode "an", "InfoItem", "infoitem", "MyInfoItem"),
        action : (data) -> # override action
          # TODO: inform about wrong request type (should be write)
          # the data is stored for switching but might confuse the user

          tree = WebOmi.consts.odfTree
          parent = tree.get_node data.reference
          $ '#infoItemParent'
            .val parent.id
          WebOmi.consts.infoItemDialog.modal "show"

    add_obj :
      createNode "an", "Object", "object", "MyObject"
    add_metadata :
      createNode "a", "MetaData", "metadata", null
    add_decsription :
      createNode "a", "description", "description", null

  my.odfTreeSettings =
    plugins : ["checkbox", "types", "contextmenu", "sort"]
    core :
      error : (msg) -> WebOmi.debug msg
      force_text : true
      check_callback : true
      data : (node, callback) ->
        if node.id == '#' # root
          callback.call this, [
            id: "Objects"
            text: "Objects"
            state: {opened : false}
            type: "objects"
            parent: "#"
            children: true
          ]
          return
            
        that = this
        tree = WebOmi.consts.odfTreeDom.jstree()
        node = tree.get_node node, true # get dom
        parents = $.makeArray node.parentsUntil WebOmi.consts.odfTreeDom, "li"
        parents.reverse()
        parents.push node
        parents = (encodeURIComponent tree.get_node(node).text for node in parents)
        path = parents.join '/'
        serverUrl = my.serverUrl.val()
        serverUrl = serverUrl
          .replace /^wss:/, "https:"
          .replace /^ws:/, "http:"
        
        xhr = $.get
          url : serverUrl + path
          dataType : "xml"
          cache : false
          success : ((parentPath) -> (xml) ->
            data = WebOmi.formLogic.OdfToJstree xml.documentElement, path
            children = for child in data.children
              switch child.type
                when "objects", "object"
                  child.children = true  # tell jstree that there can be children
              child
            callback.call that, data.children
          ) path
        
        xhr.fail (xhr, msg, err) ->
          WebOmi.debug ["O-DF GET fail", xhr, msg, err]
          alert "Failed to get Object(s): #{msg}; #{err}\n Depending on the error, check internet and server connection (to the url in the Server box) or data format might be incompatible."
    
    types :
      default :
        icon : "odf-objects " + my.icon.objects
        valid_children : ["object"]
      object :
        icon : "odf-object " + my.icon.object
        valid_children : ["object", "infoitem", "description"]
      objects :
        icon : "odf-objects " + my.icon.objects
        valid_children : ["object"]
      infoitem :
        icon : "odf-infoitem " + my.icon.infoitem
        valid_children : ["metadata", "description"]
      method :
        icon : "odf-method " + my.icon.method
        valid_children : ["metadata", "description"]
      metadata :
        icon : "odf-metadata " + my.icon.metadata
        valid_children : []
      description :
        icon : "odf-description " + my.icon.description
        valid_children : []

    checkbox :
      three_state : false
      keep_selected_style : true # Consider false
      cascade : "up+undetermined"
      tie_selection : true
    contextmenu :
      show_at_node : true
      items : openOdfContextmenu

  # private, [functions]
  afterWaits = []
  # use afterJquery(<callback>) for things that depend on const module
  my.afterJquery = (fn) -> afterWaits.push fn

  # private; url matcher for response codemirror links
  my.URLHighlightOverlay =
    token: (stream, state) ->
      if stream.match(util.urlmatch)
        stream.backUp(1)
        return "link"
      
      while stream.next()? and not stream.match(util.urlmatch,false)
        null
      return null

  # All of jquery initiliazation code is here
  # $ ->
  fallback.ready ['jQuery', 'CodeMirror', 'codeMirrorExtension'], ->

    my.responseCMSettings = $.extend(
      readOnly : true
      , my.codeMirrorSettings
    )
   
    
    # initialize UI
    my.requestCodeMirror  = CodeMirror.fromTextArea $("#requestArea" )[0], my.codeMirrorSettings
    my.responseCodeMirror = CodeMirror.fromTextArea $("#responseArea")[0], my.responseCMSettings
    my.responseDiv        = $ '.response .CodeMirror'
    my.responseDiv.hide()
    
    my.responseCodeMirror.addOverlay my.URLHighlightOverlay

    # click events for codemirror url links
    $ '.well.response'
      .delegate ".cm-link", "click", (event) ->
        url = $(event.target).text()
        window.open url, '_blank' # new tab
     

    my.serverUrl    = $ '#targetService'
    my.odfTreeDom   = $ '#nodetree'
    my.requestSelDom= $ '.requesttree'
    my.readAllBtn   = $ '#readall'
    my.sendBtn      = $ '#send'
    my.resetAllBtn  = $ '#resetall'
    my.progressBar  = $ '.response .progress-bar'
    my.sortOdfTreeCheckbox = $ '#sortOdfTree'
    my.convertXmlCheckbox = $ '#convertXml'

    loc = window.location
    proto = if loc.protocol == "https:" then "wss:" else "ws:"
    my.serverUrl.val proto + "//" + loc.host + loc.pathname.substr 0, loc.pathname.indexOf "html/"


    # Odf tree is using jstree; The requested odf nodes are selected from this tree
    my.odfTreeDom
      .jstree my.odfTreeSettings
                


    my.odfTree = my.odfTreeDom.jstree()
    my.odfTree.set_type 'Objects', 'objects'
    # Commented: done in html instead
    #my.odfTree.get_node('#Objects', true).addClass('jstree-closed')

    my.requestSelDom
      .jstree
        core :
          themes :
            icons : false
          multiple : false

    my.requestSel = my.requestSelDom.jstree()


    # tooltips & popovers
    $('[data-toggle="tooltip"]').tooltip
      container : 'body'

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
    requestTip "#delete", "Delete an O-DF subtree or a single InfoItem."




    # private; Constructs some functions for basic input fields (see my.ui below)
    basicInput = (selector, validator=util.validators.nonEmpty) ->
      ref : $ selector
      get :       -> @ref.val()
      set : (val) -> @ref.val val
      validate : ->  # returns value if successfully validated, null otherwise
          val = @get()
          validationContainer = @ref.closest ".form-group"

          validatedVal = validator val #:: null or the result

          if validatedVal? or @ref.prop "disabled"
            validationContainer
              .removeClass "has-error"
              .addClass "has-success"
          else
            validationContainer
              .removeClass "has-success"
              .addClass "has-error"

          validatedVal
      bindTo : (callback) -> # bind function on input(change) event + validation
        @ref.on "input", =>  # =>: preserving this
          callback @validate()
      

    # shortcut
    v = util.validators


    # refs, setters, getters for the parameters
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
        basicInput '#ttl', (a) ->
          (v.or (v.greaterThanEq 0), (v.equals -1)) v.number v.nonEmpty a

      callback : # Maybe string
        basicInput '#callback', v.url

      requestID: # Maybe int
        basicInput '#requestID', (a) ->
          v.integer v.number v.nonEmpty a

      odf      : # Array String paths
        ref : my.odfTreeDom
        get : -> my.odfTree.get_selected()
        set : (vals, preventEvent=true) ->
          my.odfTree.deselect_all true
          if vals? and vals.length > 0
            my.odfTree.select_node node, preventEvent, false for node in vals

      interval : # Maybe number
        basicInput '#interval', (a) ->
          (v.or (v.greaterThanEq 0), (v.equals -1), (v.equals -2)) v.number v.nonEmpty a

      newest   : # Maybe int
        basicInput '#newest', (a) ->
          (v.greaterThan 0) v.integer v.number v.nonEmpty a

      oldest   : # Maybe int
        basicInput '#oldest', (a) ->
          (v.greaterThan 0) v.integer v.number v.nonEmpty a

      begin    : # Maybe Date # TODO: merge duplicate datepicker code with the "end" below
        $.extend (basicInput '#begin'),
          set    : (val) ->
            @ref.data "DateTimePicker"
              .date(val)
          get    : ->
            mementoTime = @ref.data "DateTimePicker"
              .date()
            if mementoTime? then mementoTime.toISOString() else null
          bindTo : (callback) ->
            @ref.on "dp.change", =>
              callback @validate()

      end      : # Maybe Date
        $.extend (basicInput '#end'),
          set    : (val) ->
            @ref.data "DateTimePicker"
              .date(val)
          get    : ->
            mementoTime = @ref.data "DateTimePicker"
              .date()
            if mementoTime? then mementoTime.toISOString() else null
          bindTo : (callback) ->
            @ref.on "dp.change", =>
              callback @validate()

      requestDoc: # Maybe xml dom document
        ref : my.requestCodeMirror
        get :       -> WebOmi.formLogic.getRequest()
        set : (val) -> WebOmi.formLogic.setRequest val

    language = window.navigator.userLanguage || window.navigator.language
    if !moment.localeData(language)
      language = "en"

    
    # TODO: Cleanup some duplicate code:
    my.ui.end.ref.datetimepicker
      locale: language
    my.ui.begin.ref.datetimepicker
      locale: language

    my.ui.begin.ref.on "dp.change", (e) ->
      my.ui.end.ref.data "DateTimePicker"
        .minDate e.date
    my.ui.end.ref.on "dp.change", (e) ->
      my.ui.begin.ref.data "DateTimePicker"
        .maxDate e.date

    # FIXME: doesn't work (clicking on the input-addon calendar icon)
    my.ui.begin.ref.closest "a.tooltip"
      .on 'click', ->
        my.ui.begin.ref.data "DateTimePicker"
          .toggle()
    my.ui.end.ref.closest "a.tooltip"
      .on 'click', ->
        my.ui.end.ref.data "DateTimePicker"
          .toggle()

    # callbacks
    my.afterJquery = (fn) -> fn()
    fn() for fn in afterWaits

  # end of jquery init




  parent # export module


# extend WebOmi
window.WebOmi = utilExt($, window.WebOmi || {})

# extend WebOmi
window.WebOmi = constsExt($, window.WebOmi, window.WebOmi.util)

window.WebOmi.error = (msgs...) -> alert msgs.join ", "
window.WebOmi.debug = (msgs...) -> console.log msgs... # TODO: remove console.log

# escaped jquery identifier
# adds one # in the beginning and \\ in front of every special symbol and spaces to underscore
window.jqesc = (mySel) -> '#' + mySel.replace( /(\[|\]|!|"|#|\$|%|&|\'|\(|\)|\*|\+|\,|\.|\/|\:|\;|\?|@)/g, "\\$1" ).replace( /( )/g, "_" )
# make a valid id, convert space to underscore
window.idesc = (myId) -> myId.replace( /( )/g, "_" )

# extend String (FIXME), this is fairly safe because
# some future trim possibly is similar
String.prototype.trim = String.prototype.trim || ->
  String(this).replace /^\s+|\s+$/g, ''

window.WebOmi.consts.afterJquery -> window.Initialize = "ready"

