var requestEditor, responseEditor;
var timeout; //Used for request generation timeout
var requestInterval = 1000; // Interval in milliseconds for automatic request generation
var generating = false; // TODO: used as global variable, see submit.js

/* Load pages from separate html files */
function loadPages(page) {
	$(".page").addClass("behind");

	var selector = $("#page" + page);

	loadSides(page);

	selector.removeClass("prevpage nextpage hidden behind")
	selector.addClass("currentpage");
	selector.prop("disabled", false);
	
	if (selector.is(':empty')) {
		selector.load("pages/page" + page + ".html");
		
		if(page === 1){
			setTimeout(getObjects, 500);
		}
	}
	
	if(page === 1){
		$(document).on('click', '.drop', function(event){
			event.stopPropagation();
		    
			$(this).toggleClass("down");
			
			var id = $(this).attr("id").replace("drop-", "");
			
			$("#list-" + id).toggleClass("closed-list");
		});
	}
	
	// Load operation options (page 2)
	if (page === 2) {
		refreshEditor("request", "editRequest");

		if($('#options').is(':empty')){
			loadOptions();
		}
		$("#options").change(function(){
			updateRequest(requestInterval);
		});
		$(document).on("click", "#autorequest", function(){
			updateRequest(requestInterval);
		});
		$("#response .CodeMirror").remove();
	}
	if(page === 3){
		refreshEditor("response", "responseBox");
	}
}

/* Set a 0.5 second timeout to automatically update the request */
function updateRequest(interval){
	// If automatic update allowed by user
	if($("#autorequest").prop("checked") || interval === 0){
		generating = true;
		
		$("#editRequest .CodeMirror").hide();
			
		if(timeout){
			clearTimeout(timeout);
		}
		
		if(interval === 0){
			generateRequest(); // From generate.js
			refreshEditor("request", "editRequest");
			generating = false;
		} else {
			var loadSelector = $("#edit .loading");
			loadSelector.show();
			
			timeout = setTimeout(function(){
				generateRequest(); // From generate.js
				refreshEditor("request", "editRequest");
				
				loadSelector.hide();
				$("#editRequest .CodeMirror").show();
				generating = false;
			}, interval);
		}
	}
}

/* Refresh CodeMirror editor */
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

/* Load the previous and the next page of the current page */
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
	if(!$(this).hasClass("selected")){
		$(".icon").removeClass("selected");
		$(this).addClass("selected");
		loadOptions();
		updateRequest(requestInterval);
	}
});

/* Load form options */
function loadOptions() {
	iconValue = $("#icons").find(".selected").attr("alt");
	$("#options").empty();
	$("#options").load("forms/" + iconValue + ".html");
	
	if(omi){
		var save = omi.save[iconValue];
		if(save){
			setTimeout(function(){
				if(save["ttl"]) $("#ttl").val(save["ttl"]); 
				if(save["interval"]) $("#interval").val(save["interval"]);
				if(save["begin"]) $("#begin").val(save["begin"]);
				if(save["end"]) $("#end").val(save["end"]);
				if(save["newest"]) $("#newest").val(save["newest"]);
				if(save["oldest"]) $("#oldest").val(save["oldest"]);
				if(save["callback"]) $("#callback").val(save["callback"]);
				if(save["request-id"]) $("#request-id").val(save["request-id"]);
			}, 100);
		}
	}
}