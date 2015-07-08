
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

$ ->
    $('.optional-parameters .panel-heading a')
      .on 'click', () ->
        console.log this
        glyph = $(this).children('span')
        if glyph.hasClass('glyphicon-menu-right')
          glyph.removeClass('glyphicon-menu-right')
          glyph.addClass('glyphicon-menu-down')
        else
          glyph.removeClass('glyphicon-menu-down')
          glyph.addClass('glyphicon-menu-right')

