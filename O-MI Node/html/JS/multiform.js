/* 
Orginal Page: http://thecodeplayer.com/walkthrough/jquery-multi-step-form-with-progress-bar 

 */
//jQuery time
var current_fs, next_fs, previous_fs; // fieldsets
var left, opacity, scale; // fieldset properties which we will animate
var animating; // flag to prevent quick multi-click glitches
var count;

/* Event handler for the next button */
$(document).on('click', '.next', function() {
	// Using global variable index
	if (page === 1) {
		if (!page1Verified()) {
			alert("Please check at least one object");
			return;
		}
	} else if (page === 2) {
		if (!page2Verified()) {
			alert("Please specify TTL (Time to live) as numeric value");
			return;
		}
		$("#responseBox").html("");
		
		sendRequest(); // From submit.js, sending request on action
	} else if (page === 3) {
		return false;
	}
	animateNext();
});

/* Event handler for clicking the previous button */
$(document).on('click', '.prev', function() {
	if (page === 3) {
		send = false; // Polling variable
	}
	animatePrev();
});

/* Handle switching from current page to previous page */
function animatePrev() {
	if (animating || page === 1)
		return false;
	
	current_fs = $("#page" + page);
	previous_fs = $("#page" + (page - 1));
	next_fs = $("#page" + (page + 1));
	
	page -= 1; // Update index
	animating = true;

	// de-activate current step on progressbar
	$("#progressbar li").eq(page).removeClass("active");
	
	animating = false;
	
	loadPages(page);
	previous_fs.animate({ scrollTop: 0 }, "slow"); // Move to animation complete?
}	

/* Handle switching from current page to next page */
function animateNext() {
	if (animating || page === 3)
		return false;

	// Animate scrolling
	$("html, body").animate({ scrollTop: 0 }, "slow");

	animating = true;
	
	current_fs = $("#page" + page);
	next_fs = $("#page" + (page + 1));
	prev_fs = $("#page" + (page - 1));
	
	page += 1; // Update index

	// activate next step on progressbar using the page number
	$("#progressbar li").eq((page - 1)).addClass("active");

	loadPages(page);
	animating = false;
	next_fs.animate({ scrollTop: 0 }, "slow"); // Move to animation complete?
}


$(".submit").click(function() {
	return false;
});
