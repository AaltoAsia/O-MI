var infoArr = [
               "The server is querying for the objects",
               "The server is preparing the response"
];

/* Hide server status box and stop rotating info icon */
function clearInfo() {
	$("#info p").text("The server is not doing anything");
	$("#info p").hide();
	$("#info img").removeClass("rotate");
}

/* Show server status box and start rotating info icon */
function setInfo(index) {
	$("#info p").text(infoArr[index]);
	$("#info p").show();
	$("#info img").addClass("rotate");
}