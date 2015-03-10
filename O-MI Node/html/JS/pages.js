function loadPages() {
	$(".page").addClass("behind");
	
	var selector = $("#page" + page);
	
	loadSides();
	
	if(selector.is(':empty')){
		selector.load("pages/page" + page + ".html");
	}
	// Load operation options (page 2)
	if(page === 2) {
		$("#prev").removeClass("hidden");
		loadOptions();
	}
	if(page === 3){
		$("#next").removeClass("hidden");
	}
	if(page === 4){
		$("#send-field").val($("#url-field").val().replace("/Objects", ""));
	}
	selector.removeClass("prevpage nextpage hidden behind")
	selector.addClass("currentpage")
}

function loadSides() {
	var prev = $("#page" + (page - 1));
	var next = $("#page" + (page + 1));
	
	if(prev){
		if(prev.is(':empty')){
			prev.load("pages/page" + (page - 1) + ".html");
		}
		prev.removeClass("currentpage behind");
		prev.addClass("prevpage hidden");
		prev.prop("disabled", true);
	}
	if(next){
		if(next.is(':empty')){
			next.load("pages/page" + (page + 1) + ".html");
		}
		next.removeClass("currentpage behind");
		next.addClass("nextpage hidden");
		next.prop("disabled", true);
	}
}

// Page 2 definitions
$(document).on('click', '.icon', function(){
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