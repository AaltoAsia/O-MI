
# extend module webOmi; public vars
WebOmiExt = ($, parent) ->

    # Module WebOmi constants
    #my = parent.consts = {}
    parent.consts = ( ->
        my = {}
        my.codeMirrorSettings =
            mode: "text/html"
            lineNumbers: true
            lineWrapping: true

        afterWaits = []

        # use afterJquery for things that depend on const module
        my.afterJquery = (fn) -> afterWaits.push fn

        $ ->
            # initialize UI
            my.requestCodeMirror  = CodeMirror.fromTextArea($("#requestArea" )[0], my.codeMirrorSettings)
            my.responseCodeMirror = CodeMirror.fromTextArea($("#responseArea")[0], my.codeMirrorSettings)
            
            my.odfTree    = $ '#nodetree'
            my.requestSel = $ '.requesttree'
            my.readAllBtn = $ '#readall'

            my.odfTree
                .jstree
                  plugins : ["checkbox"]
                .on "changed.jstree", (_, data) ->
                  console.log data.node


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
        
        my
    )()
    parent # export module

# extend WebOmi
window.WebOmi = WebOmiExt($, window.WebOmi || {})
