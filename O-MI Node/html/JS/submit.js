/* IconSelect object */
var iconSelect;

window.onload = function(){
	//IconSelect settings
	iconSelect = new IconSelect("operation-select", 
                {'selectedIconWidth':36,
                'selectedIconHeight':36,
                'iconsWidth':36,
                'iconsHeight':36,
                'vectoralIconNumber':1,
                'horizontalIconNumber':4});

	//Pushing the icons
	var icons = [];
	icons.push({'iconFilePath':'Resources/icons/read.png', 'iconValue':'read'});
	icons.push({'iconFilePath':'Resources/icons/write.png', 'iconValue':'write'});
	icons.push({'iconFilePath':'Resources/icons/subscribe.png', 'iconValue':'subscribe'});
	icons.push({'iconFilePath':'Resources/icons/cancel.png', 'iconValue':'cancel'});
	
	iconSelect.refresh(icons);
}; 

/* Click events for buttons */
$(document).on('click', '#object-button', getObjects);
$(document).on('click', '#request-gen', generateRequest);
$(document).on('click', '#request-send', sendRequest);


/* Get the objects through ajax get */
function getObjects() {
	//Get the current path of the file
	var url = document.URL;
	var path = url.substring(0, url.lastIndexOf("/"));
	
	console.log("Sending AJAX GET for the objects...");
	
	//Sent ajax get-request for the objects
	$.ajax({
        type: "GET",
		dataType: "xml",
        url: path + "/SensorData/objects",
        success: displayObjects,
		error: handleError
    });
}

/* Display the objects as checkboxes in objectList 
* @param {XML Object} the received XML data
*/
function displayObjects(data) {
	console.log("Got the Objects as XML: \n" + new XMLSerializer().serializeToString(data));

	//Clear the list beforehand, in case objects is changed in between the button clicks
	$("#objectList").empty();
	
	//Append objects as checkboxes to the webpage
	$(data).find('Objects').each(function(){
		$(this).find("Object").each(function(){
			var id = $(this).find("id").text();
			
			$('<label><input type="checkbox" class="checkbox" id="' + id + '/">' + id + '</label><br>').appendTo("#objectList"); 
		});
	});
}

/* Generate the O-DF request */
function generateRequest(){
	var ttl = $("#ttl").val(); 
	var interval = $("#interval").val();
	var operation = iconSelect.getSelectedValue(); //Get the selected operation from the IconSelect object
	var selectedObjects = $("#objectList").find("input").filter(":checked"); //Filter the selected objects (checkboxes that are checked)
	var request = writeXML(selectedObjects, operation, ttl, interval);
	
	console.log("Generated the O-DF request");
	
    $("#request").text((request)); //Update the request textbox on the webpage
}

/* 
* Write the O-DF message (XML) based on form input
* @param {Array} Array of objects, that have their 
* @param {String} the O-DF operation (read, write, cancel, subscribe)
* @param {Number} Time to live 
* @param {Number} Message interval
* @param {function} Callback function
*/
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

    var request = writer.flush();

    return request;
}

// Server URL
var server = 'http://localhost:8080';

/* Send the O-DF request using AJAX */
function sendRequest()
{
    var request = $('#request').val(); //Get the request string
	
    if(request.indexOf("subscribe") >= 0)
        startSubscriptionEventListener(request); //If subscribe request, create eventlistener for request
    else
    {
        $.ajax({
            type: "POST",
            url: server, //TODO: the real server here
            data: {msg : request},
            dataType: "text",
            success: printResponse,
			error: function(a, b,c){
				console.log("Error sending request");
			}
        });
    } 
}

/* HTML 5 Server Sent Event communication */
function startSubscriptionEventListener(request) {
    var source = new EventSource(server+"?msg="+request);

    source.onmessage = function(event)
    {
        printResponse(event.data);
    };
    source.onerror = function(event) {
        source.close();
        console.log("Subscription TTL Expired");
    };
}

/* Do something with the response from the server */
function printResponse(response){
	//TODO: print the response somewhere on the page
	console.log((response));
}

/* Handle the AJAX errors */
function handleError(jqXHR, errortype, exc) {
	console.log("Error: " + (exc | errortype));
}

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