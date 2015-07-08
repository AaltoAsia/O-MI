
# Intialize events, import
((consts, requests) ->
    consts.afterJquery ->
        consts.readAllBtn
            .on 'click', -> requests.readAll(true)
)(WebOmi.consts, WebOmi.requests)

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
