
# loose augmentation
WebOmi = (($, my) ->

    # Module webOmi constants
    #
    my.codeMirrorSettings =
        mode: "text/html"
        lineNumbers: true
        lineWrapping: true


    my
)($, WebOmi || {})



# initialize
$ ->
    # extend module webOmi; public vars
    WebOmi = (($, my) ->
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

        my.readAllBtn
            .on 'click', my.requests.readAll(true)


    )($, WebOmi)

