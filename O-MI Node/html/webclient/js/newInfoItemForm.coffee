# Code for new InfoItem modal popup,
# which is found with right click in the odf tree and selecting new InfoItem

# imports
((consts) ->
  cloneAbove = ->
    that = $ this

    model = that.prev().clone()
    model.find("input").val ""  # empty all cloned inputs

    that.prev()
      .after model  # insert after

  consts.afterJquery ->
    $ '.btn-clone-above'
      .on 'click', cloneAbove

)(WebOmi.consts)

