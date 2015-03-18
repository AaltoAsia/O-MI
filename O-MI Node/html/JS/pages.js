var requestEditor, responseEditor;

function loadPages(page) {
	$(".page").addClass("behind");

	var selector = $("#page" + page);

	loadSides(page);

	selector.removeClass("prevpage nextpage hidden behind")
	selector.addClass("currentpage");
	selector.prop("disabled", false);
	
	if (selector.is(':empty')) {
		selector.load("pages/page" + page + ".html");
		
		// Handling server url for sending, in case skipping generate check, and field not loaded in time
		if(page === 4){
			var id = setInterval(function(){
				if($("#send-field").length){
					$("#send-field").val($("#url-field").val().replace("/Objects", ""));
					clearInterval(id);
				}
			}, 100);
		}
	}
	if (page === 1) {
		$("#prev").addClass("hidden");
	}
	// Load operation options (page 2)
	if (page === 2) {
		$("#prev").removeClass("hidden");
		
		if($('#options').is(':empty')){
			loadOptions();
		}
	}
	if (page === 3) {
		$("#next").removeClass("hidden");
	}
	if (page === 4) {
		$("#next").addClass("hidden");
		$("#send-field").val($("#url-field").val().replace("/Objects", ""));
		
		$("#response .CodeMirror").remove();
	}
}

function refreshEditor(editor, id) {
	if (editor == "request") {
		$("#edit .CodeMirror").remove();
		
		requestEditor = CodeMirror.fromTextArea(document.getElementById(id), {
			mode : "application/xml",
			lineNumbers : true,
			lineWrapping: true
		});
		requestEditor.refresh();
	} else if (editor == "response") {
		$("#response .CodeMirror").remove();
		
		responseEditor = CodeMirror.fromTextArea(document.getElementById(id), {
			mode : "application/xml",
			lineNumbers : true,
			lineWrapping: true
		});
		responseEditor.refresh();
	}
}

function loadSides(page) {
	var prev = $("#page" + (page - 1));
	var next = $("#page" + (page + 1));

	if (prev) {
		if (prev.is(':empty')) {
			prev.load("pages/page" + (page - 1) + ".html");
		}
		prev.removeClass("currentpage behind");
		prev.addClass("prevpage hidden");
		prev.prop("disabled", true);
	}
	if (next) {
		if (next.is(':empty')) {
			next.load("pages/page" + (page + 1) + ".html");
		}
		next.removeClass("currentpage behind");
		next.addClass("nextpage hidden");
		next.prop("disabled", true);
	}
}

// Page 2 definitions
$(document).on('click', '.icon', function() {
	$(".icon").removeClass("selected");
	$(this).addClass("selected");

	loadOptions();
});

/* Load form options */
function loadOptions() {
	iconValue = $("#icons").find(".selected").attr("alt");
	$("#options").empty();
	$("#options").load("forms/" + iconValue + ".html");
}