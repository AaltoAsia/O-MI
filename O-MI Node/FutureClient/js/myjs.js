var webOmi;

$( function(){


$('#nodetree').jstree({"plugins" : ["checkbox"]}); 
$('#requesttree').jstree(); 
$('#nodetree').on("changed.jstree", function (e, data) {
      console.log(data.selected);
});
$('#requesttree').on("changed.jstree", function (e, data) {
      console.log(data.selected);
});
$('button').on('click', function () {
      $('#jstree').jstree(true).select_node('child_node_1');
        $('#jstree').jstree('select_node', 'child_node_1');
	  $.jstree.reference('#jstree').select_node('child_node_1');
});

<<<<<<< Updated upstream
// Module
webOmi = (function(my) {
    my.codeMirrorSettings = {
        mode: "text/html",
        lineNumbers: true,
        lineWrapping: true
    };

    my.requestCodeMirror = CodeMirror.fromTextArea($("#requestArea")[0], my.codeMirrorSettings);

    my.responseCodeMirror = CodeMirror.fromTextArea($("#responseArea")[0], my.codeMirrorSettings);

    return my;
}(webOmi || {}));

=======
$(".omi-textarea").each(function(index,element){
    CodeMirror.fromTextArea(element, {
	mode: "text/html",
	lineNumbers: true,
	lineWrapping: true
    });
});
>>>>>>> Stashed changes
});
