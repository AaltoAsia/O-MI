
# formLogic sub module
formLogicExt = ($, WebOmi) ->
    my = WebOmi.formLogic = {}

    my.send = ->
        server  = WebOmi.consts.serverUrl.val()
        request = WebOmi.consts.requestCodeMirror.getValue
        $.ajax
            type: "POST"
            url: server
            data: request
            contentType: "text/xml"
            processData: false
            dataType: "text"
            #success: -> true
            #error: -> true
            complete: (response) ->
                WebOmi.consts.responseCodeMirror.setValue response
                WebOmi.consts.responseCodeMirror.autoFormatAll
            
    WebOmi # export

# extend WebOmi
window.WebOmi = formLogicExt($, window.WebOmi || {})

# Intialize events, import
((consts, requests) ->
    consts.afterJquery ->
        consts.readAllBtn
            .on 'click', -> requests.readAll(true)

)(window.WebOmi.consts, window.WebOmi.requests)
