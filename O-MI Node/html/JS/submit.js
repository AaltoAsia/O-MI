var iconSelect;

window.onload = function(){
	iconSelect = new IconSelect("operation-select", 
                {'selectedIconWidth':36,
                'selectedIconHeight':36,
                'iconsWidth':36,
                'iconsHeight':36,
                'vectoralIconNumber':1,
                'horizontalIconNumber':4});

	var icons = [];
	icons.push({'iconFilePath':'Resources/icons/read.png', 'iconValue':'read'});
	icons.push({'iconFilePath':'Resources/icons/write.png', 'iconValue':'write'});
	icons.push({'iconFilePath':'Resources/icons/subscribe.png', 'iconValue':'subscribe'});
	icons.push({'iconFilePath':'Resources/icons/cancel.png', 'iconValue':'cancel'});
	
	iconSelect.refresh(icons);
};

function getObjects() {
	//Get the current path of the file
	var url = document.URL;
	var path = url.substring(0, url.lastIndexOf("/"));
	
	console.log("Sending AJAX GET for the objects");
	
	$.ajax({
        type: "GET",
        url: path + "/SensorData/objects",
        success: displayObjects,
		error: handleError
    });
}

/* Display the objects as checkboxes; Currently data in XML-format */
function displayObjects(data) {
	console.log("Got the Objects as XML: \n" + new XMLSerializer().serializeToString(data));

	//Clear the list beforehand, in case objects is changed in between the button clicks
	$("#objectList").empty();
	
	$(data).find('Objects').each(function(){
		$(this).find("Object").each(function(){
			var id = $(this).find("id").text();
			
			$('<input type="checkbox" name="' + id + '">' + id + '<br>').appendTo("#objectList"); 
		});
	});
}

/* Generate the O-DF request */
function generateRequest(){
	var ttl = $("#ttl").val();
	var interval = $("#interval").val();
	var operation = iconSelect.getSelectedValue();

	if($("#objectList").is(":empty")){
		alert("No requested objects");
		return;
	} 
	if(!($.isNumeric(ttl) && $.isNumeric(interval))){
		alert("Please specify TTL (Time to live) and Interval as integers");
		return;
	} 
	
	var selectedObjects = $("#objectList").find("input").filter(":checked");
	if(selectedObjects.length == 0){
		alert("No objects selected");
		return;
	}
	var request = writeXML(selectedObjects, operation, ttl, interval);
	
	console.log("Generated the O-DF request");
	
	$("#testp").text((request));
}

function writeXML(objects, operation, ttl, interval, callback){
	//Using the same format as in demo
	var writer = new XMLWriter('UTF-8');
	writer.formatting = 'indented';
    writer.indentChar = ' ';
    writer.indentation = 2;
	
	writer.writeStartDocument();
	
	//(first line)
	writer.writeStartElement('omi:omiEnvelope');
	writer.writeAttributeString('xmlns:xsi', 'http://www.w3.org/2001/XMLSchema-instance');
	writer.writeAttributeString('xmlns:omi', 'omi.xsd' );
	writer.writeAttributeString('xsi:schemaLocation', 'omi.xsd omi.xsd');
	writer.writeAttributeString('version', '1.0');
	writer.writeAttributeString('ttl', ttl);
	//(second line)
	writer.writeStartElement('omi:'+ operation);
	writer.writeAttributeString('msgformat', 'omi.xsd');
	if(interval) writer.writeAttributeString('interval', interval);
	if(callback) writer.writeAttributeString('callback', callback);
	//(third line)
	writer.writeStartElement('omi:msg');
	writer.writeAttributeString( 'xmlns', 'omi.xsd');
	writer.writeAttributeString( 'xsi:schemaLocation', 'odf.xsd odf.xsd');
	writer.writeStartElement('Objects');
	//Payload
	for (var i = 0; i < objects.length; i++)
	{
		writer.writeStartElement( 'Object');
		writer.writeElementString('id', objects[i].name);
		writer.writeEndElement();
	}
	writer.writeEndElement();
    writer.writeEndDocument();

    var qlmreq = writer.flush();

    return qlmreq;
}

//TODO:
function sendRequest(){
	console.log("not doing anything yet");
}

function handleError(jqXHR, errortype, exc) {
	console.log("Error: " + (exc | errortype));
}