$("#info").on("mouseenter", function() {
	$("#info p").show();
});

$("#info").on("mouseleave", function() {
	$("#info p").hide();
});

var infoArr = [
               "The server is querying for the objects",
               "The server is preparing the response"
];

function clearInfo() {
	$("#info p").text("The server is not doing anything");
	$("#info img").removeClass("rotate");
}

function setInfo(index) {
	$("#info p").text(infoArr[index]);
	$("#info img").addClass("rotate");
}