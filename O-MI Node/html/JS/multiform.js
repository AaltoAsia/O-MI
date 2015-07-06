//jQuery time
var next_fs, previous_fs; // fieldsets
var animating; // flag to prevent quick multi-click glitches

// Variable to keep count on which page we are
var page = 1; 

/**
 * Handle switching from current page to next page
 */
function animateNext() {
	if (page === 3)
		return ;

	// Animate scrolling
	$("html, body").animate({ scrollTop: 0 }, "slow");
	
	// Update pages and page index
	page += 1;
	loadPages(page);
	
	// activate next step on progressbar using the page number
	$(".progressbar li").eq((page - 1)).addClass("active");
	
	next_fs = $("#page" + page);
	next_fs.animate({ scrollTop: 0 }, "slow");
	
	$(".resize").removeClass("resize");
	
	// Allow animation again
	animating = false;
	
	// Ask about automatic request generation on page 2 if many checkboxes checked
	if(page === 2){
		if(checkedObjects().length > 100){
			if (confirm('You have checked lot of objects. This webform has automatic request generation ' +
					'enabled by default. Do you want to disable automatic generation to prevent the page ' +
					'from lagging? (Note: Cancel keeps automatic generation)')){
				$("#autorequest").prop("checked", false);
			}
		}
		updateRequest(1000); //from pages.js
	} 
	
	// Ask about request generation on page 3 if automatic generation disabled
	if(page === 3){
		if(!($("#autorequest").prop("checked"))){
			if(confirm('You have disabled automatic generation, thus your request might not be up-to-date. Do you want ' +
					'to generate the message once more before sending? (Note: Cancel sends the current request)')) {
				updateRequest(0); //from pages.js, create request manually due to possibly ungenerated request
			}
		}
		sendRequest(); // From submit.js, sending request on action
	}
}

/**
 * Returns whether the input on the 1st page is valid (at least 1 object selected)
 * @returns {Boolean} true if at least 1 object selected on page 1, otherwise returns false
 */
function page1Verified(){
	return $("#objectList").find("input").filter(":checked").length > 0;
}

/**
 * Returns whether the input on the 2nd page is valid (numberical ttl)
 * @returns {Boolean} true if ttl exists and it's numerical, otherwise returns false
 */
function page2Verified(){
	return ($.isNumeric($("#ttl").val().replace(/\s/g, ''))) && !($('#request').is(':empty'));
}

/**
 * Set timeout for prev/next button animation
 * @param {Object} button The button clicked
 * @param {Function} func The function to be called after timeout
 */
function transitionButton(button, func){
	if(!animating){
		// Small resize animation for next/prev buttons
		$(button).addClass("resize");
		
		// Timeout to stop the animation
		setTimeout(function(){
			$(".resize").removeClass("resize");
		}, 150);
		
		// Call function after both ended
		setTimeout(func, 300);
	}
	animating = true;
}


/* Event handler for the next button */
$(document).on('click', '.next', function() {
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
	} else if (page === 3) {
		return false;
	}
	transitionButton(this, animateNext);
});

/**
 * Handle switching from current page to previous page
 */
function animatePrev() {
	if (page === 1) { return; }

	// Update pages and page index
	page -= 1;
	loadPages(page);
	
	// de-activate current step on progressbar
	$(".progressbar li").eq(page).removeClass("active");
	
	previous_fs = $("#page" + page);
	previous_fs.animate({ scrollTop: 0 }, "slow"); // Move to animation complete?
	
	// Allow animation again
	animating = false;
}	

/* Event handler for clicking the previous button */
$(document).on('click', '.prev', function() {
	if(page === 1){
		return false;
	}
	transitionButton(this, animatePrev);
});



