var iconSelect;

window.onload = function(){
	iconSelect = new IconSelect("operation-select", 
				{'vectoralIconNumber':4,
                'horizontalIconNumber':4});

	var icons = [];
	icons.push({'iconFilePath':'Resources/icons/read.png', 'iconValue':'read'});
	icons.push({'iconFilePath':'Resources/icons/write.png', 'iconValue':'write'});
	icons.push({'iconFilePath':'Resources/icons/subscribe.png', 'iconValue':'subscribe'});
	icons.push({'iconFilePath':'Resources/icons/cancel.png', 'iconValue':'cancel'});
	
	iconSelect.refresh(icons);
};

/* Send O-MI read request using HTTP GET */
function submitGet() {
	var url = document.URL;
	var path = url.substring(0, url.lastIndexOf("/"));
	var objectPath = $("#objectPath").val();

	/* Full path should be something like localhost:8080/qlm/Objects
	   and the <Objects> (XML(?)) should be prepared by the server;
	   we could use this property (getting available objects) in creating the POST form
	*/
	$.ajax({
		type : "GET",
		url : path + "/qlm/" + objectPath, 
		success : function(d) {
			console.log("SUCCESS");
			console.log(d);
			$("#box2").html(d);
		},
		error : function(error, textStatus, et) {
			handleError(error);
		}
	});
}

/* Send O-MI read request using HTTP POST */
function submitPost() {
	var url = document.URL;
	var path = url.substring(0, url.lastIndexOf("/"));
	
	//Construct the read request
	var omiData = getRequestXml();
	
	console.log("Posting O-MI Read Request: ");
	console.log(omiData);
	
	$.ajax({
		type : "POST",
		url : "server", //server url here
		data: omiData,
		contentType: "text/xml",
		dataType: "text",
		success : parseOmiResponse,
		error : function(error, textStatus, et) {
			handleError(error);
		}
	});
}

/* Construct the O-MI read request for the ajax POST */
function getRequestXml(){
	var checked = [$("#Object1"), $("#Object2"), $("#Object3"), $("#Object4")].filter(function(checkBox){
		return checkBox.is(":checked");
	});

	
	var objectStr = "<Objects>";
	
	//Append the Object-elements to the xml
	checked.forEach(function(box){
		//Note: The id's of the checkboxes should be the same as the labels following them
		objectStr += "<Object><id>" + box.attr("id") + "</id></Object>";
	});
	objectStr += "</Objects>";
	
	var request = "<omi:omiEnvelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
			+ "xmlns:omi=\"omi.xsd\" xsi:schemaLocation=\"omi.xsd omi.xsd\" version=\"1.0\" ttl=\"10\">" 
				+ "<omi:read msgformat=\"omi.xsd\">"
					+ "<omi:msg xmlns=\"odf.xsd\" xsi:schemaLocation=\"odf.xsd odf.xsd\">"
						+ objectStr
					+ "</omi:msg>"
				+ "</omi:read>"
		+ "</omi:omiEnvelope>";
		
	return request;
}
