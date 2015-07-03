
$(function () { $('#nodetree').jstree({"plugins" : ["checkbox"]}); });
$('#jstree_demo_div').on("changed.jstree", function (e, data) {
      console.log(data.selected);
});
$('button').on('click', function () {
      $('#jstree').jstree(true).select_node('child_node_1');
        $('#jstree').jstree('select_node', 'child_node_1');
	  $.jstree.reference('#jstree').select_node('child_node_1');
});
