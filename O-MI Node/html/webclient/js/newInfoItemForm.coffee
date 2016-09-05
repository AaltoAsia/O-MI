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
# Code for new InfoItem modal popup,
# which is found with right click in the odf tree and selecting new InfoItem

# imports
((consts, requests, omi, util) ->
  
  # Utility function; Clone the element above and empty its input fields 
  # callback type: (clonedDom) -> void
  cloneAbove = (target, callback) ->
    util.cloneAbove target, (cloned) ->

      if callback? then callback cloned

      cloned.slideDown null, ->  # animation, default duration
        # readjusts the position because of size change (see modal docs)
        consts.infoItemDialog.modal 'handleUpdate'

  # Utility; create timestamp picker gui
  createTimestampPicker = (dom) ->
    dom.find '.timestamp'
      .datetimepicker
        format: 'X' # NOTE: Current version of datetimepicker does not support unixtime
                    # (thinks that month, year and time is not enabled)
                    # the required change is to add the following line to isEnabled private function:
                    # if (actualFormat.toLowerCase().indexOf('x') !== -1) return true;
        sideBySide: true


  notifyErrorOn = (jqElement, errorMsg) ->
    jqElement
      .tooltip
        placement: "top"
        title: errorMsg
      .focus() # triggers the tooltip also
      .on 'input', ->
        $(this)
          .tooltip 'destroy'
          .closest '.form-group'
            .removeClass 'has-error'
      .closest '.form-group'
        .addClass 'has-error'

  # 1. Input helpers to fill the form
  consts.afterJquery ->
    consts.infoItemDialog = $ '#newInfoItem'
    consts.infoItemForm   = consts.infoItemDialog.find 'form'

    consts.originalInfoItemForm = consts.infoItemForm.clone()
  
    # Reset on cancel or close
    consts.infoItemDialog
      .on 'hide.bs.modal', ->
        resetInfoItemForm()

    # For binding of events
    resetInfoItemForm()

    consts.infoItemDialog.find '.newInfoSubmit'
      .on 'click', ->
        infoitemData = readValues()
        updateOdf infoitemData

    return

  # 2. Reading of values
  
  # return an Array of objects extracted from inputs of given selector (:String)
  getGroups = (ofWhat, requiredField) ->
    arr = []
    consts.infoItemForm.find ofWhat
      .each ->
        value = {}
        $(this).find ":input"
          .each ->
            value[this.name] = $(this).val()

        if value[requiredField]? and value[requiredField].length > 0
          arr.push value
        return
    arr

  readValues = ->
    results = {}

    consts.infoItemForm.find "#infoItemName, #infoItemDescription, #infoItemParent"
      .each ->
        results[this.name] = $(this).val()

    results.values    = getGroups ".value-group", "value"
    results.metadatas = getGroups ".metadata-group", "metadataname"

    results


  findDuplicate = (arr) ->
    set = {}
    for item in arr
      if set[item]?
        return item
      else
        set[item] = true
    return null

  # 3. Generate the odf and update the state
  # takes input in the form which readValues returns
  updateOdf = (newInfoItem) ->
    tree   = WebOmi.consts.odfTree

    parent = newInfoItem.parent
    name   = newInfoItem.name
    idName = idesc name

    path   = "#{parent}/#{idName}"

    if $(jqesc path).length > 0 # name already exists, 
      tree.select_node path

      # Inform the user
      notifyErrorOn $('#infoItemName') "InfoItem with this name already exists"

      return # don't close
    else

      # TODO: use validators on higher level and set gui indicators (has-success/has-error)
      v = WebOmi.util.validators


      values =
        for valueObj in newInfoItem.values
          value: valueObj.value
          type : valueObj.valuetype
          time : v.nonEmpty valueObj.valuetime

      duplicateTime = findDuplicate(values.map((val) -> val.time))
      if duplicateTime?
        duplicateInputs = $("input[name='valuetime']").filter((_, e) -> $(e).val() == duplicateTime)
        notifyErrorOn duplicateInputs, "Server probably doesn't accept multiple values with the same timestamp."
        return # don't close

      metas =
        for metaObj in newInfoItem.metadatas
          name       : metaObj.metadataname
          value      : metaObj.metadatavalue
          type       : v.nonEmpty metaObj.metadatatype
          description: v.nonEmpty metaObj.metadatadescription

        
      # NOTE: This also selects the node which triggers an event which modifies the request 
      consts.addOdfTreeNode parent, path, name, "infoitem", ->
        # save parameters
        $ jqesc path
          .data "values",      values
          .data "description", v.nonEmpty newInfoItem.description

      if newInfoItem.metadatas.length > 0
        consts.addOdfTreeNode path, path+"/MetaData", "MetaData", "metadata", (node) ->
          $(jqesc node.id).data "metadatas", metas

      if newInfoItem.description?
        consts.addOdfTreeNode path, path+"/description", "description", "description", (node) ->
          $(jqesc node.id).data "description", newInfoItem.description

      # close the dialog
      consts.infoItemDialog.modal 'hide'
      # reset
      resetInfoItemForm()
      return

  # 4. Resetting
  resetInfoItemForm = ->
    consts.infoItemForm.replaceWith consts.originalInfoItemForm.clone()
    consts.infoItemForm = $ consts.infoItemDialog.find 'form'

    # Re-bind events

    # prevent any submitting fix (maybe not needed)
    consts.infoItemForm
      .submit (event) ->
        event.preventDefault()

    consts.infoItemForm.find '.btn-clone-above'
      .on 'click', -> cloneAbove $(this), createTimestampPicker # creates if there is class .timestamp

    # tooltips & popovers also lose some event handlers
    consts.infoItemForm.find('[data-toggle="tooltip"]').tooltip
      container : 'body'

    # recreate complex ui widgets
    createTimestampPicker consts.infoItemForm

    return


)(WebOmi.consts, WebOmi.requests, WebOmi.omi, WebOmi.util)

