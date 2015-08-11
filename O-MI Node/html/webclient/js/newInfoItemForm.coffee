# Code for new InfoItem modal popup,
# which is found with right click in the odf tree and selecting new InfoItem

# imports
((consts) ->
  
  # Utility function; Clone the element above and empty its input fields 
  cloneAbove = ->
    target = $ this
      .prev()

    model = target.clone()
    model.find("input").val ""  # empty all cloned inputs

    target.after model  # insert after the cloned one

  # 1. Input helpers to fill the form
  consts.afterJquery ->
    $ '.btn-clone-above'
      .on 'click', cloneAbove

  # 2. Reading of values

  # 3. Generate the odf

)(WebOmi.consts)

