
/* Returns whether the input on the 1st page is valid (at least 1 object selected)*/
function page1Verified(){
	return $("#objectList").find("input").filter(":checked").length > 0;
}

/* Returns whether the input on the 2nd page is valid (numerical TTL & interval) */
function page2Verified(){
	return ($.isNumeric($("#ttl").val()) && $.isNumeric($("#interval").val()));
}

/* Returns whether the input on the 3rd page is valid (message generated) */
function page3Verified(){
	return !($('#request').is(':empty'));
}