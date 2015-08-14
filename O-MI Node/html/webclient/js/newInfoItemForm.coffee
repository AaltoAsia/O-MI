# Code for new InfoItem modal popup,
# which is found with right click in the odf tree and selecting new InfoItem

# imports
((consts, requests, omi) ->
  
  # Utility function; Clone the element above and empty its input fields 
  cloneAbove = ->
    target = $ this
      .prev()

    model = target.clone()
    model.find("input").val ""  # empty all cloned inputs
    model.hide()  # make unvisible for animation

    target.after model  # insert after the cloned one

    model.slideDown null, ->  # animation, default duration
      # readjusts the position (see modal docs)
      consts.infoitemDialog.modal 'handleUpdate'


  # 1. Input helpers to fill the form
  consts.afterJquery ->
    consts.infoitemDialog = $ '#newInfoItem'
    consts.infoitemForm   = consts.infoitemDialog.find 'form'

    consts.originalInfoItemForm = consts.infoitemForm.clone()
  
    # Reset on cancel or close
    consts.infoitemDialog
      .on 'hide.bs.modal', ->
        resetInfoItemForm()

    # For binding of events
    resetInfoItemForm()

    consts.infoitemDialog.find '.newInfoSubmit'
      .on 'click', ->
        infoitemData = readValues()
        updateOdf infoitemData

    return

  # 2. Reading of values
  
  # return an Array of objects extracted from inputs of given selector (:String)
  getGroups = (ofWhat, requiredField) ->
    arr = []
    consts.infoitemForm.find ofWhat
      .each ->
        value = {}
        $(this).find ":input"
          .each ->
            value[this.name] = $(this).val()

        if value[requiredField]? and value[requiredField].length > 0
          arr.push value
        null
    arr

  readValues = ->
    results = {}

    consts.infoitemForm.find "#infoItemName, #infoItemDescription, #infoItemParent"
      .each ->
        results[this.name] = $(this).val()

    results.values    = getGroups ".value-group", "value"
    results.metadatas = getGroups ".metadata-group", "metadataname"

    results


  # 3. Generate the odf and update the state
  # takes input in the form which readValues returns
  updateOdf = (newInfoItem) ->
    tree   = WebOmi.consts.odfTree

    parent = newInfoItem.parent
    name   = newInfoItem.name
    idName = idesc name

    path   = "#{parent}/#{idName}"

    if $(jqesc path).length > 0 # already exists, 
      tree.select_node path

      # Inform the user
      $ '#infoItemName'
        .tooltip
          placement: "top"
          title: "InfoItem with this name already exists"
        .focus() # triggers the tooltip also
        .on 'input', ->
          $(this)
            .tooltip 'destroy'
            .closest '.form-group'
              .removeClass 'has-error'
        .closest '.form-group'
          .addClass 'has-error'

      return # don't close
    else

      # TODO: User friendlier time input
      # TODO: use validators on higher level and set gui indicators (has-success/has-error)
      v = WebOmi.consts.validators

      values =
        for valueObj in newInfoItem.values
          value: valueObj.value
          time:  v.nonEmpty valueObj.valuetime
          type:  valueObj.valuetype

        
      # NOTE: This also selects the node which triggers an event which modifies the request 
      consts.addOdfTreeNode parent, path, name, "infoitem", ->
        # save parameters
        $ jqesc path
          .data "values",      newInfoItem.values
          .data "description", v.nonEmpty newInfoItem.description

      if newInfoItem.metadatas.length > 0
        consts.addOdfTreeNode path, path+"/MetaData", "MetaData", "metadata", (node) ->
          $(jqesc node.id).data "metadatas", newInfoItem.metadatas

      # close the dialog
      consts.infoitemDialog.modal 'hide'
      # reset
      resetInfoItemForm()
      return

  # 4. Resetting
  resetInfoItemForm = ->
    consts.infoitemForm.replaceWith consts.originalInfoItemForm.clone()
    consts.infoitemForm = $ consts.infoitemDialog.find 'form'

    # Re-bind events

    # prevent any submitting fix (maybe not needed)
    consts.infoitemForm
      .submit (event) ->
        event.preventDefault()

    consts.infoitemForm.find '.btn-clone-above'
      .on 'click', cloneAbove

    return


)(WebOmi.consts, WebOmi.requests, WebOmi.omi)

