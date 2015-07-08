
# loose augmentation
webOmi = (($, my) ->

    # Module webOmi constants
    #
    my.codeMirrorSettings =
        mode: "text/html"
        lineNumbers: true
        lineWrapping: true


    my
)($, webOmi || {})

# initialize
$ ->
    # extend module webOmi; public vars
    webOmi = (($, my) ->
        my.requestCodeMirror  = CodeMirror.fromTextArea($("#requestArea" )[0], my.codeMirrorSettings)
        my.responseCodeMirror = CodeMirror.fromTextArea($("#responseArea")[0], my.codeMirrorSettings)
        
        my.odfTree    = $ '#nodetree'
        my.requestSel = $ '#requesttree'
        my.readAllBtn = $ '#readall'

        webOmi.odfTree
          .jstree
            plugins : ["checkbox"]
          .on "changed.jstree", (_, data) ->
            console.log data.id


        webOmi.requestSel
          .jstree
            core :
              themes :
                icons : false
          .on "changed.jstree", (_, data) ->
            console.log data

    )($, webOmi)

