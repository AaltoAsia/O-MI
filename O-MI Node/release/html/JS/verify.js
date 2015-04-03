/* Returns whether the input on the 1st page is valid (at least 1 object selected)*/
function page1Verified(){
	return $("#objectList").find("input").filter(":checked").length > 0;
}

/* Returns whether the input on the 2nd page is valid (numerical TTL) */
function page2Verified(){
	return ($.isNumeric($("#ttl").val().replace(/\s/g, ''))) && !($('#request').is(':empty'));
}