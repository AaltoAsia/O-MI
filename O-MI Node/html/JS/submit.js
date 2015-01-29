/* IconSelect object */
var iconSelect;

window.onload = function() {
	//IconSelect settings
	iconSelect = new IconSelect("operation-select");

	//Pushing the icons
	var icons = [];
	icons.push({'iconFilePath': 'Resources/icons/read.png', 'iconValue': 'read'});
	icons.push({'iconFilePath': 'Resources/icons/write.png', 'iconValue': 'write'});
	icons.push({'iconFilePath': 'Resources/icons/subscribe.png', 'iconValue': 'subscribe'});
	icons.push({'iconFilePath': 'Resources/icons/cancel.png', 'iconValue': 'cancel'});
	
	iconSelect.refresh(icons);
}; 

/* Click events for buttons */
$(document).on('click', '#object-button', getObjects);
$(document).on('click', '#request-gen', generateRequest);
$(document).on('click', '#request-send', sendRequest);

var objectUrl;

/* Get the objects through ajax get */
function getObjects() {
	console.log("Sending AJAX GET for the objects...");
	
	objectUrl = $("#url-field").val();
	
	$("#send-field").val(objectUrl.replace("/Objects", ""));
	
	//Sent ajax get-request for the objects
	ajaxGet(0, objectUrl, "");
}

function ajaxGet(indent, url, listId){
	$.ajax({
        type: "GET",
		dataType: "xml",
        url: url,
        success: function(data) {
			displayObjects(data, indent, url, listId);
		},
		error: function(a, b, c){
			console.log("Error accessing data discovery");
		}
    });
}

/* Display the objects as checkboxes in objectList 
* @param {XML Object} the received XML data
*/
function displayObjects(data, indent, url, listId) {
	console.log("Got the Objects as XML: \n" + new XMLSerializer().serializeToString(data));

	// Basic objects
	if(indent === 0){
		//Clear the list beforehand, in case objects is changed in between the button clicks
		$("#objectList").empty();
		
		//Append objects as checkboxes to the webpage
		$(data).find('Objects').each(function(){
			$(this).find("Object").each(function(){
				var id = $(this).find("id").text();
				
				$('<li><label><input type="checkbox" class="checkbox" id="' + id + '"/>' + id + '</label></li>').appendTo("#objectList"); 
				$('<ul id="list-' + id + '"></ul>').appendTo("#objectList");
				addInfoItems(this, id, indent + 1);
				
				//Get lower hierarchy values
				ajaxGet(indent + 1, url + "/" + id, "list-" + id)
			});
		});
	} else {
		// Subobjects/Infoitems
		var margin = indent * 20 + "px";
		
		$(data).find("Object").each(function(){
			var id = $($(this).find("id")[0]).text();
			
			$(this).find("Object").each(function(){
				var name = $(this).find("id").text();
				var str = '<li><label><input type="checkbox" class="checkbox ' + id + '" id="' + name + '"/>' + name + '</label></li>';
				
				$(str).appendTo("#" + listId); 
				$("#" + listId).last().css({ marginLeft: margin });
				$('<ul id="list-' + name + '"></ul>').appendTo("#" + listId);
				$("#" + listId).last().css({ marginLeft: margin });
				
				ajaxGet(indent + 1, url + "/" + name);
				
				$("#" + listId + ":last-child").css({ marginLeft:margin });
			});
			addInfoItems(this, id, indent + 1);
		});
	}
}

function addInfoItems(parent, id, indent) {
	var margin = indent * 20 + "px";

	$(parent).find("InfoItem").each(function(){
		var name = $(this).attr('name');
		
		//Append InfoItem as checkbox
		$('<li><label>' + 
		'<input type="checkbox" class="checkbox ' + id + '" name="' + name + '"/>' + name +
		'</label></li>').appendTo("#list-" + id); 
		
		//Styling (margin)
		$("#list-" + id).last().css({ marginLeft: margin });
	});
}

/* Generate the O-DF request */
function generateRequest(){
	var ttl = $("#ttl").val(); 
	var interval = $("#interval").val();
	var begin = $("#begin").val();
	var end = $("#end").val();
	var operation = iconSelect.getSelectedValue(); //Get the selected operation from the IconSelect object
	var selectedObjects = $("#objectList").find("input").filter(":checked"); //Filter the selected objects (checkboxes that are checked)
	var request = writeXML(selectedObjects, operation, ttl, interval, begin, end);
	
	console.log("Generated the O-DF request");
	console.log(request);
	
	var formattedXML = formatXml(request);
    $("#request").html(formattedXML.value); //Update the request textbox on the webpage
}

/* 
* Write the O-DF message (XML) based on form input
* @param {Array} Array of objects, that have their 
* @param {String} the O-DF operation (read, write, cancel, subscribe)
* @param {Number} Time to live 
* @param {Number} Message interval
* @param {function} Callback function (not used atm)
*/
function writeXML(objects, operation, ttl, interval, begin, end, callback){
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
	
	if(ttl) writer.writeAttributeString('ttl', ttl);
	
	//(second line)
	writer.writeStartElement('omi:'+ operation);
	writer.writeAttributeString('msgformat', 'omi.xsd');
	
	if(interval > 0) writer.writeAttributeString('interval', interval);
	
	if(begin && end){
		if(new Date(begin).getTime() > 0 && new Date(end).getTime() > 0){
			writer.writeAttributeString('begin', begin);
			writer.writeAttributeString('end', end);
		}
	}
	
	if(callback) writer.writeAttributeString('callback', callback);
	
	//(third line)
	writer.writeStartElement('omi:msg');
	writer.writeAttributeString( 'xmlns', 'omi.xsd');
	writer.writeAttributeString( 'xsi:schemaLocation', 'odf.xsd odf.xsd');
	writer.writeStartElement('Objects');
	//Payload
	var ids = [];
	if(objects.length > 0){
		writer.writeStartElement('Object');
		writer.writeElementString('id', objects[0].id);
		ids.push(objects[0].id);
	}

	for (var i = 1; i < objects.length; i++)
	{
		var classes = $(objects[i]).attr("class").split(" ");
		var cl = classes[classes.length - 1];
		
		if(objects[i].id) {
			if(cl != ids[ids.length - 1]){
				while(ids.length > 0){
					ids.pop();
					writer.writeEndElement();
				}			
			}
			
			//Object
			ids.push(objects[i].id);
			writer.writeStartElement('Object');
			writer.writeElementString('id', objects[i].id);
		} else {
			//InfoItem
			if(cl != ids[ids.length - 1]){
				ids.pop();
				writer.writeEndElement();
			}
			writer.writeStartElement('InfoItem');
			writer.writeAttributeString('name', objects[i].name);
			writer.writeEndElement();
		}
	}
	if(objects.length > 0) {
		writer.writeEndElement();
	}
	
	writer.writeEndElement();
    writer.writeEndDocument();

    var request = writer.flush();

    return request;
}

/* Send the O-DF request using AJAX */
function sendRequest()
{
	// Server URL
	var server = $("#send-field").val();

    var request = $('#request').text(); //Get the request string
	console.log(request);
	
    if(request.indexOf("subscribe") >= 0)
		//TODO:
        startSubscriptionEventListener(request); //If subscribe request, create eventlistener for request
    else
    {
        $.ajax({
            type: "POST",
            url: server, //TODO: the real server here
            data: request,
			contentType: "text/xml",
			processData: false,
            dataType: "text",
            success: printResponse,
			error: function(a, b, c){
				$("#responseBox").text("Error sending message");
				handleError(a, b, c);
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
	console.log("Got response!");
	console.log(response);
	
	var formattedXML = formatXml(response);
	console.log(formattedXML);
    $("#responseBox").html(formattedXML.value);
	
	//$("#responseBox").text(response);
}

/* Handle the ajax errors */
function handleError(jqXHR, errortype, exc) {
	console.log("Error: " + (exc | errortype));
}
