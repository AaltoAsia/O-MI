/* IconSelect object */
var iconSelect, objectUrl, omi, iconValue;

var send = false;
var page = 1; // Start at page 1

$(function() {
	
	loadThemes();
	loadPages(page);
	loadOptions();
	
	/* Click events for buttons */
	$(document).on('click', '#object-button', getObjects);
	$(document).on('click', '#request-send', sendRequest);
	$(document).on('click', '#stop', function(){
		send = false;
	});
	$(document).on('click', '#poll', function(){
		send = true;
		if(omi){
			if(omi.operation === "read" && getSubscribeLocal()){
				getSub();
			}
		}
	});
	
	$(document).on('click', '#prev4', function(){
		send = false;
	});
	

function loadThemes(){
	iconSelect = new IconSelect("themes",{
		'selectedIconWidth':48,
        'selectedIconHeight':48,
        'selectedBoxPadding':1,
        'iconsWidth':48,
        'iconsHeight':48,
        'boxIconSpace':1,
        'vectoralIconNumber':1,
        'horizontalIconNumber':4});

	var icons = [];
    icons.push({'iconFilePath':'Resources/icons/dark.svg', 'iconValue':'dark'});
    icons.push({'iconFilePath':'Resources/icons/light.svg', 'iconValue':'light'});
    icons.push({'iconFilePath':'Resources/icons/white.svg', 'iconValue':'white'});
    icons.push({'iconFilePath':'Resources/icons/green.svg', 'iconValue':'green'});
    iconSelect.refresh(icons);

    for(var i = 0; i < iconSelect.getIcons().length; i++){
    	iconSelect.getIcons()[i].element.onclick = function(){
            iconSelect.setSelectedIndex(this.childNodes[0].getAttribute('icon-index'));
            
            $('body').css({
            	"background": "url('Resources/icons/" + iconSelect.getSelectedValue() + ".svg') no-repeat center center fixed",
            	"-webkit-background-size": "cover",
        		"-moz-background-size": "cover",
        		"-o-background-size": "cover",
        		"background-size": "cover"
            }); 
        };
    }
}
});


/* Get the objects through ajax get */
function getObjects() {
	console.log("Sending AJAX GET for the objects...");
	
	objectUrl = $("#url-field").val();
	
	$("#send-field").val(objectUrl.replace("/Objects", ""));
	
	// Sent ajax get-request for the objects
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

/*
 * Display the objects as checkboxes in objectList @param {XML Object} the
 * received XML data
 */
function displayObjects(data, indent, url, listId) {
	// console.log("Got the Objects as XML: \n" + new
	// XMLSerializer().serializeToString(data));

	// Basic objects
	if(indent === 0){
		// Clear the list beforehand, in case objects is changed in between the
		// button clicks
		$("#objectList").empty();
		
		// Append objects as checkboxes to the webpage
		$(data).find('Objects').each(function(){
			$(this).find("Object").each(function(){
				var id = $(this).find("id").text();
				
				$('<li><label><input type="checkbox" class="checkbox" id="' + id + '"/>' + id + '</label></li>').appendTo("#objectList"); 
				$('<ul id="list-' + id + '"></ul>').appendTo("#objectList");
				addInfoItems(this, id, indent + 1);
				
				// Get lower hierarchy values
				//ajaxGet(indent + 1, url + "/" + id, "list-" + id);
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
		
		// Append InfoItem as checkbox
		$('<li><label>' + 
		'<input type="checkbox" class="checkbox ' + id + '" name="' + name + '"/>' + name +
		'</label></li>').appendTo("#list-" + id); 
		
		// Styling (margin)
		$("#list-" + id).last().css({ marginLeft: margin });
	});
}

/* Send the O-DF request using AJAX */
function sendRequest()
{
	// Server URL
	var server = $("#send-field").val();

    var request = requestEditor.getValue(); // Get the request string

    ajaxPost(server, request, getSubscribeLocal());
}

function getSubscribeLocal(){
	return ($.isNumeric(omi.interval) && omi.callback.length === 0);
}

// Test
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
			
			/*
			if(subscribeLocal && send){
				window.setTimeout(
					function () {
						getSub();
					},
					1000);
			
			}*/ 
		},
		error: function(a, b, c){
			$("#infoBox").text("Error sending message");
			handleError(a, b, c);
		}
	});
}

/* Get subscription from the server */
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
	
	var formattedXML = formatNoHighlight(response);
	// console.log(formattedXML);
    $("#responseBox").html(formattedXML);

    refreshEditor("response", "responseBox");
}

/* Handle the ajax errors */
function handleError(jqXHR, errortype, exc) {
	console.log("Error: " + (exc | errortype));
}

