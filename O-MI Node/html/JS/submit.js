/* IconSelect object */
var iconSelect;

/* Url to get the objects from */
var objectUrl;

var omi;
var iconValue;
var send = false;

$(function() {
	//IconSelect settings
	iconSelect = new IconSelect("operation-select");

	//Pushing the icons
	var icons = [];
	icons.push({'iconFilePath': 'Resources/icons/read.png', 'iconValue': 'read'});
	icons.push({'iconFilePath': 'Resources/icons/write.png', 'iconValue': 'write'});
	icons.push({'iconFilePath': 'Resources/icons/cancel.png', 'iconValue': 'cancel'});
	
	iconSelect.refresh(icons);

	loadOptions();

	/* Click events for buttons */
	$(document).on('click', '#object-button', getObjects);
	$(document).on('click', '#request-gen', generateRequest);
	$(document).on('click', '#request-send', sendRequest);
	$(document).on('click', '#stop', function(){
		send = false;
	});
	$(document).on('click', '#sub', function(){
		if(omi){
			getSub();
		}
	});
	
	$(document).on('click', '#prev4', function(){
		send = false;
	});
	
	for(var i = 0; i < iconSelect.getIcons().length; i++){
		$(iconSelect.getIcons())[i].element.onclick = function(){
			iconSelect.setSelectedIndex(this.childNodes[0].getAttribute('icon-index'));
			loadOptions();
		};
	}
	
	$("#url-field").val('http://' + window.location.host + "/Objects");

/* Load form options */
function loadOptions() {
	iconValue = iconSelect.getSelectedValue();
	$("#options").empty();
	$("#options").load("forms/" + iconValue + ".html"); 
}
	
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
			alert("Error accessing data discovery");
		}
    });
}

/* Display the objects as checkboxes in objectList 
* @param {XML Object} the received XML data
*/
function displayObjects(data, indent, url, listId) {
	//console.log("Got the Objects as XML: \n" + new XMLSerializer().serializeToString(data));

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
				ajaxGet(indent + 1, url + "/" + id, "list-" + id);
			});
		});
	} else {
		// Subobjects/Infoitems
		var margin = "20px";
		
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
			addInfoItems(this, id, indent);
		});
	}
}

function addInfoItems(parent, id) {
	var margin = "20px";

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
	var operation = iconSelect.getSelectedValue(); //Get the selected operation from the IconSelect object
	var ttl = $("#ttl").val(); 
	var interval = $("#interval").val();
	var begin = $("#begin").val();
	var end = $("#end").val();
	var newest = $("#newest").val();
	var oldest = $("#oldest").val();
	var callback = $("#callback").val();
	var requestId = $("#request-id").val();
	
	omi = new Omi(operation, ttl, interval, begin, end, newest, oldest, callback, requestId);
	
	var request = omi.getRequest(checkedObjects());
	
	console.log("Generated the O-DF request");
	console.log(request);
	
	var formattedXML = formatXml(request);
    $("#request").html(formattedXML.value); //Update the request textbox on the webpage
	
	var width = -($("#request").width() / 4) + 'px';
	$("#page3").css('left', width);
}

function checkedObjects() {
	return $("#objectList").find("input").filter(":checked"); //Filter the selected objects (checkboxes that are checked)
}

/* Send the O-DF request using AJAX */
function sendRequest()
{
	// Server URL
	var server = $("#send-field").val();

    var request = $('#request').text(); //Get the request string

	send = true;
    ajaxPost(server, request, getSubscribeLocal());
}

function getSubscribeLocal(){
	return ($.isNumeric(omi.interval) && omi.callback.length === 0);
}

//Test
var count = 0;

function ajaxPost(server, request, subscribeLocal){
	
	$.ajax({
		type: "POST",
		url: server,
		data: request,
		contentType: "text/xml",
		processData: false,
		dataType: "text",
		success: function(response){
			printResponse(response);
			
			count += 1;
			$("#infoBox").text("Count: " + count);
			
			if(subscribeLocal && send){
				window.setTimeout(
					function () {
						getSub();
					},
					1000);
			} 
		},
		error: function(a, b, c){
			$("#infoBox").text("Error sending message");
			handleError(a, b, c);
		}
	});
}

function getSub(){
	var response = $("#responseBox").text();
	console.log(response);
	var r1 = response.split("<omi:requestId>");
	
	if(r1.length === 2 || omi.requestId){
		$("#infoBox").text("Sending request");
		
		if(r1.length === 2){
			r2 = r1[1].split("</omi:requestId>")[0];
			omi.requestId = r2;
		}
		var subRequest = omi.getSub(omi.requestId, checkedObjects());
		console.log("Request: " + subRequest);
		var server =  $("#send-field").val();
		
		ajaxPost(server, subRequest, getSubscribeLocal());
	} else {
		alert("No request id found!");
	}
}

/* Do something with the response from the server */
function printResponse(response){
	console.log("Got response!");
	console.log(response);
	
	var formattedXML = formatXml(response);
	console.log(formattedXML);
    $("#responseBox").html(formattedXML.value);
	
	var width = -($("#responseBox").width() / 4) + 'px';
	$("#page4").css('left', width);
}

/* Handle the ajax errors */
function handleError(jqXHR, errortype, exc) {
	console.log("Error: " + (exc | errortype));
}
}); 

