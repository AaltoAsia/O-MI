/* 
Orginal Page: http://thecodeplayer.com/walkthrough/jquery-multi-step-form-with-progress-bar 

 */
//jQuery time
var current_fs, next_fs, previous_fs; // fieldsets
var left, opacity, scale; // fieldset properties which we will animate
var animating; // flag to prevent quick multi-click glitches
var count;

/* Event handler for the next button on the 3rd page */
$(document).on('click', '#next', function() {
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
		$("#request").html("");
	} else if (page === 3) {
		if (!page3Verified()) {
			alert("Please generate the request");
			return;
		}
		$("#responseBox").html("");
	} else if (page === 4) {
		return false;
	}
	animateNext();
});

$(document).on('click', '#prev', function() {
	if (page === 4) {
		send = false; // Polling variable
	}
	animatePrev();
});

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
	
	loadPages();
	previous_fs.animate({ scrollTop: 0 }, "slow"); // Move to animation complete?
}	

function animateNext() {
	if (animating || page === 4)
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

	/*
	current_fs.animate({
		opacity : 0.5,
		top : "5em",
		left : "5em",
		width : "40%",
		zIndex : -10
	}, {
		duration : 1500,
		complete : checkCount,
		easing : 'easeInOutBack'
	});
	
	next_fs.animate({
		opacity : 1,
		left : "20%",
		width : "60%",
		zIndex : 0
	}, {
		duration : 1500,
		complete : checkCount,
		step : function(a, b){
			console.log($(this).css("zIndex"));
		},
		easing : 'easeInOutBack'
	});
	
	if(prev_fs){
		prev_fs.animate({
			left : "30%",
			zIndex : -20
		}, {
			duration : 1500,
			complete : checkCount,
			easing : 'easeInOutBack'
		});
	} */
	
	animating = false;
	
	loadPages();
	next_fs.animate({ scrollTop: 0 }, "slow"); // Move to animation complete?
}

$(".submit").click(function() {
	return false;
})
