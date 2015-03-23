var infoArr = [
               "The server is querying for the objects",
               "The server is preparing the response"
];

function clearInfo() {
	$("#info p").text("The server is not doing anything");
	$("#info p").hide();
	$("#info img").removeClass("rotate");
}

function setInfo(index) {
	$("#info p").text(infoArr[index]);
	$("#info p").show();
	$("#info img").addClass("rotate");
}