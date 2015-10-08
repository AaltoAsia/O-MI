/* IconSelect object */
var iconSelect, omi, iconValue, objectUrl, page;

/* ObjectBoxManager object for handling object checkboxes */
var manager;

var fullObjectsRequest = 
'<?xml version="1.0" encoding="UTF-8" ?>\n' + 
'<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">\n' +
  '<omi:read msgformat="odf">\n' +
    '<omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">\n' +
      '<Objects>\n' +
      '</Objects>\n' +
    '</omi:msg>\n' +
  '</omi:read>\n' +
'</omi:omiEnvelope>\n';

/**
 * Nulls all inputs on every page
 */
function restart() {
	$(".progressbar li").removeClass("active");
	$("#page3").empty();
	$("#page2").empty();
	$("#page1").empty();
	loadPages(1);
	page = 1;
	$(".progressbar li").eq(0).addClass("active");
}
/**
 * Updates which children or parent are checked when a checkbox is un-/checked
 * @param obj {Object}
 */
function update(obj) {
	var ref = $(obj); //Reference (jquery object) of the clicked checkbox
	var id = ref.attr('id'); // If id is defined, the checkbox has 'child' checkboxes
	
	if(id){
		propChildren(ref, true);
		propParent(ref);
	} else { 
		propParent(ref);
	}
}

/* Initial settings */
$(function() {
	// Initialize ObjectBoxManager
	manager = new ObjectBoxManager(); 
	
	// Load themes, pages, page content 
	loadThemes();
	loadPages(page);
	loadOptions();
	
	// Click event listeners for buttons
	//$(document).on('click', '#object-button', getObjects);
	$(document).on('click', '#object-button', dataDiscovery);
	
	$(document).on('click', '#request-send', sendRequest);
	$(document).on('click', '#resend', function(){
		console.log("Resending request.");
		
		$("#responseBox").html("");
		refreshEditor("response", "responseBox");
		
		sendRequest();
	});
	$(document).on('click', '#restart', function(){
		restart();
	});
	$(document).on("mouseenter", ".help", function(){
		$(this).children("p").show();
	});
	$(document).on("mouseleave", ".help", function(){
		$(this).children("p").hide();
	});
	
	/* Event listeners related to the checkbox object tree */
	
	// Event handler for clicking the dropdown icons next to checkboxes (opens the list) 
	$(document).on('click', '.drop', function(event){
		event.stopPropagation(); // Prevent event bubbling

		$(this).toggleClass("down");
		var id = $(this).attr("id").replace("drop-", ""); 
		$("#list-" + id).toggleClass("closed-list"); 
	});
	
	// Event handler for checking all checkboxes (button click)
	$(document).on('click', '#checkall', function() {
		$(".checkbox").prop('checked', true);
	});

	// Event handler for unchecking all checkboxes (button click)
	$(document).on('click', '#uncheckall', function() {
		$(".checkbox").prop('checked', false);
	});

	// Eventlistener for object tree updating
	$(document).on('click', '.checkbox', function() {
		update(this);
	});

	/**
	 * Returns the required css for background themes
	 * @param {string} value The IconSelect value of the selected icon
	 */
	function getCSS(value) {
		if(value.split("_").indexOf("repeat") > -1){
			return {
				"background": "url('Resources/icons/" + value + ".svg')",
				"background-size": "100px 100px"
			};
		}
		return {
	    	"background": "url('Resources/icons/" + value + ".svg') no-repeat center center fixed",
	    	"-webkit-background-size": "cover",
			"-moz-background-size": "cover",
			"-o-background-size": "cover",
			"background-size": "cover"
	    };
	}
	
	/**
	 * Loads clickable icons for page themes using IconSelect
	 */
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
	    icons.push({'iconFilePath':'Resources/icons/green.svg', 'iconValue':'green'});
	    icons.push({'iconFilePath':'Resources/icons/test_repeat.svg', 'iconValue':'test_repeat'});
	    iconSelect.refresh(icons);

	    function iconselect(){
	            iconSelect.setSelectedIndex(this.childNodes[0].getAttribute('icon-index'));
	            $('body').css(getCSS(iconSelect.getSelectedValue())); 
	        };

	    // Override the IconSelect click eventhandler to also apply new css to the page
	    for(var i = 0; i < iconSelect.getIcons().length; i++){
	    	iconSelect.getIcons()[i].element.onclick = iconselect;
	    }
	}
	
});


/**
 *  Gets the objects from the server using AJAX (single read request)
 */
function getObjects() {
	console.log("Sending AJAX POST for the objects...");
	
	// Get user specified URL from the DOM
	objectUrl = $("#url-field").val();

	// Send ajax get-request for the objects
	//loadXML("request/objects.xml", objectUrl);
	ajaxObjectQuery(objectUrl, fullObjectsRequest);
}

function joinPath(a, b) {
    var aLast = a.charAt(a.length - 1);
    var bHead = b.charAt(0);
    if (aLast === "/" && bHead === "/") {
        return a + b.substr(1);
    } else if (aLast === "/" && bHead !== "/" ||
               aLast !== "/" && bHead === "/") {
        return a + b;
    } else {
        return a + "/" + b;
    }
}


/**
 * Gets the objects from the server using AJAX (multiple AJAX requests)
 */
function dataDiscovery() {
	console.log("Sending AJAX GET for the objects...");
	
	// Get user specified URL from the DOM
	objectUrl = joinPath($("#url-field").val(), "Objects");

	// Send ajax get-request for the objects
	//loadXML("request/objects.xml", objectUrl);
	ajaxGet(0, objectUrl, "");
}

/**
 *  Display the objects as checkboxes in objectList (data discovery)
 *  @param {XML Object} data The received XML data
 *  @param {Number} indent The depth of the object (recursive data discovery)
 *  @param {string} url The URL to the server (recursive data discovery)
 *  @param {string} listId The ID of the current list (recursive data discovery)
 *  @param {Array} pathArray The array containing previous objects
 */
function displayDiscoveryObjects(data, indent, url, listId, pathArray) {
	if(indent === 0){
		// Clear the existing list
		$("#objectList").empty();
		
		// Append objects as checkboxes to the webpage
		$(data).find('Objects').each(function(){
			$(this).find("Object").each(function(){
				var id = $(this).find("id").text();
				
				manager.addObject(id);
				
				var pathArray = [id];
				
				// Get lower hierarchy values (Subobjects/Infoitems)
				ajaxGet(indent + 1, joinPath(url, id), "list-" + id, pathArray);
			});
		});
	} else {
		// Subobjects/Infoitems
		$(data).find("Object").each(function(){
			//var id = $($(this).find("id")[0]).text();
			var id = pathArray.join('-');
			var sub = [];
			var arrays = [];
			
			
			$(this).find("Object").each(function(){
				var name = $(this).find("id").text();

				/*
				//ajaxGet(indent + 1, url + "/" + name);
				manager.find(id).addChild(id, name, "list-" + id);
				sub.push(name);
				*/
				
				pathArray.push(name);
				arrays.push(pathArray.slice(0));
				manager.find(id).addChild(id, pathArray, "list-" + id);
				sub.push(name);
				pathArray.pop();
			});
			addInfoItems(this, id, indent);
			
			for(var i = 0; i < sub.length; i++){
				ajaxGet(indent + 1, joinPath(url, sub[i]), "list-" + id, arrays[i]);
			}
		});
	}
}

/**
 * Sends an ajax query for objects (data discovery) 
 * @param {Number} indent The depth of the object tree hierarchy
 * @param {string} url The URL of the server to get the objects data from
 * @param {string} listId The id of the list DOM object that's being queried
 */
function ajaxGet(indent, url, listId, pathArray){
	$.ajax({
        type: "GET",
		dataType: "xml",
        url: url,
        success: function(data) {
			displayDiscoveryObjects(data, indent, url, listId, pathArray);
		},
		error: function(a, b, c){
			//alert("Error accessing data discovery");
			console.log("Error accessing data discovery ");
                        console.log(a);
                        console.log(b);
                        console.log(c);
		}
    });
}

/**
 * Sends an ajax query for objects 
 * @param {string} url The URL of the server to get the objects data from
 * @param {string} xml The XML string of the request to be sent
 */
function ajaxObjectQuery(url, xml){
	// Show loading animation
	$("#objectContainer .loading").show();
	
	$.ajax({
		type: "POST",
		url: url, // The server url here
		data: xml, // The request here
		contentType: "text/xml",
		processData: false,
		dataType: "text",
        success: function(data) {
			displayObjects(data);
		},
		error: function(a, b, c){
			alert("Error accessing data discovery; The database might be updating.");
		}
    });
}

/**
 * Loads the XML using AJAX GET and call the send function upon loading
 * @param {string} filepath The path to the XML file
 * @param {Number} serverUrl The URL of the server to send the request to
 */
function loadXML(filepath, serverUrl) {
	$.ajax({
	    url: filepath, // path to xml
	    type: 'GET',
	    dataType: 'xml',
	    success: function(xml){
	    	/* Upon getting the XML request, send it by calling the sendAjaxRequest function */
	    	/* Note: The function can be called anywhere by giving the xml string and server url as parameters */
	    	ajaxObjectQuery(serverUrl, xml); 
	    }
	});
}

/**
 * Adds subobjects to the Object tree
 * @param {Object} parent The parent object
 * @param {Array} pathArray Array of string that specifies the object path
 */
function addSubObjects(parent, pathArray){
	var id = pathArray.join('-');
	$(parent).children("Object").each(function(){
		var name = $($(this).children("id")[0]).text();
		
		pathArray.push(name);
		manager.find(id).addChild(id, pathArray, "list-" + id);
		addSubObjects(this, pathArray);
		pathArray.pop();
	});
	addInfoItems(parent, id);
}

/**
 *  Display the objects as checkboxes in objectList 
 *  @param {XML Object} data The received XML data
 */
function displayObjects(data) {
	// Append objects as checkboxes to the webpage
	$(data).find('Objects').each(function(){
		// Clear the existing list
		$("#objectList").empty();
		
		$(this).children("Object").each(function(){
			var id = $($(this).children("id")[0]).text();
			
			manager.addObject(id);
		});
	});
	$(data).find('Objects').each(function(){
		// Get lower hierarchy values (Subobjects/Infoitems)
		$(this).children("Object").each(function(){
			var id = $($(this).children("id")[0]).text();

			var pathArray = [id];
			
			addSubObjects(this, pathArray);
		});
	});
	
	// Hide loading animation
	$("#objectContainer .loading").hide();
}

/**
 * Adds InfoItem checkboxes under the list of the current object
 * @param {Object} parent The parent Object of the the current object
 * @param {string} id The ID of the current object
 */
function addInfoItems(parent, id) {
	var margin = "20px";
	
	$(parent).children("InfoItem").each(function(){
		var name = $(this).attr('name');

		// Append InfoItem as checkbox
		$('<li><label>' + 
		'<input type="checkbox" class="checkbox ' + id + '" name="' + name + '"/>' + name +
		'</label></li>').appendTo("#list-" + id); 
		
		// Styling (margin)
		$("#list-" + id).last().css({ marginLeft: margin });
	});
}

/**
 * Gets the server url to send requests to
 * @returns {string} The URL of the server based on the URL field on the page
 */
function getServerUrl() {
	var o = $("#url-field").val();
	
	if(o) {
		return o.replace("/Objects", "");
	}
	alert("Couldn't find server url");
	return "";
}


/**
 *  Send the O-MI request using AJAX
 */
function sendRequest() {
	// If the request is still being generated, try again in 0.5 seconds
	if(generating){
		setTimeout(sendRequest, 500);
		return;
	}
	
	// O-MI node Server URL
	var server = getServerUrl();
    var request = requestEditor.getValue(); // Get the request string

    ajaxPost(server, request);
}

/**
 * Sends and AJAX POST request to the server
 * @param {string} server The URL of the server
 * @param {string} request The XML request to be sent
 */
function ajaxPost(server, request) {
	// Show loading animation
	$("#response .loading").show();
	
	console.log($("#response .loading"));
	
	$.ajax({
		type: "POST",
		url: server,
		data: request,
		contentType: "text/xml",
		processData: false,
		dataType: "text",
		success: function(response) {
			printResponse(response);
		},
		error: function(a, b, c) {
			handleError(a, b, c);
		},
		complete: function() {
			// Hide loading animation
			$("#response .loading").hide();
		}
	});
}


/**
 * Writes the response to the response textfield
 * @param {string} response The response XML from the server
 */
function printResponse(response) {
	console.log("Got response!");
	
	// Format the XML in case the XML from the server hasn't been
	var formattedXML = formatXml(response);
	
    $("#responseBox").html(formattedXML);

    refreshEditor("response", "responseBox");
}

/**
 * Handles error from the server (currently only logging to the console)
 * @param jqXHR
 * @param errortype
 * @param exc
 */
function handleError(jqXHR, errortype, exc) {
	console.log(jqXHR.responseText);
	refreshEditor("response", "responseBox");
	
	console.log("Error sending to server: (" + exc +")");
	printResponse(jqXHR.responseText);
}




/**
 * Temp function, gets an array of children with the given id (as their class)
 * @param id The ID of the current checkbox
 * @returns {Array} the array of child checkboxes
 */
function getChildren(id){
	return $("#objectList").find("input").filter(function(){
		return $(this).attr('class').split(" ").indexOf(id) > -1;
	});
}

/**
 * Props all children to match the propped checkbox
 * @param {Object} parent The checkbox that was clicked
 */
function propChildren(parent){
	var parentId = $(parent).attr("id");
	
	//Find child items and mark their value the same as their parent
	var children = getChildren(parentId);
	var url = $("#url-field").val();
	
	children.each(function(){
		$(this).prop('checked', $(parent).is(':checked'));
		propChildren(this);
	});
}

/**
 * Temp function, allows special characters pass through jQuery
 * @param {string} prefix Class/ID prefix (./#)
 * @param {string} selector Class/ID selector
 * @returns {string} the ID that passes from jQuery
 */
function jq(prefix, selector) {
	return prefix + selector.replace( /(:|\.|\[|\]|\/)/g, "\\$1" );
}

/**
 * Props the parent(s) to match the propped checkbox
 * @param {Object} child The checkbox that was clicked
 */
function propParent(child){
	var ids = ($(child).attr('class')).split(' ').filter(isParent);
	
	if(ids.length > 0){
		var parentId = ids[0];
		var jqId = jq("#", parentId);
		var checked = $("#objectList").find(jq(".", parentId)).filter(":checked").length > 0;
		
		if(checked){
			$(jqId).prop('checked', true); //Change parent item check value
			
			if(!isRootBox(jqId)){
				propParent($(jqId));
			}
		}
	}
}



/**
 * Checks if a checkbox is a parent checkbox (has a child)
 * @param element The checkbox
 * @returns {Boolean} true if checkbox has a child otherwise returns false
 */
function isParent(element, index){
	return element !== "checkbox" && element !== "lower";
}

/**
 * Checks if the checkbox is a root (no parents)
 * @param jqid The ID of the checkbox
 * @returns {Boolean} true if checkbox with the given ID is root, otherwise returns false
 */
function isRootBox(jqid){
	return $(jqid).attr('class').split(' ').length === 1;
}