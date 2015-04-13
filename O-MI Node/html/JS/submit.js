/* IconSelect object */
var iconSelect, omi, iconValue;

/* ObjectBoxManager object for handling object checkboxes */
var manager;

/* Initial settings */
$(function() {
	// Initialize ObjectBoxManager
	manager = new ObjectBoxManager(); 
	
	// Load themes, pages, page content 
	loadThemes();
	loadPages(page);
	loadOptions();
	
	// Click event listeners for buttons
	$(document).on('click', '#object-button', getObjects);
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
	    
	    // Override the IconSelect click eventhandler to also apply new css to the page
	    for(var i = 0; i < iconSelect.getIcons().length; i++){
	    	iconSelect.getIcons()[i].element.onclick = function(){
	            iconSelect.setSelectedIndex(this.childNodes[0].getAttribute('icon-index'));
	            $('body').css(getCSS(iconSelect.getSelectedValue())); 
	        };
	    }
	}
	
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
});


/**
 *  Gets the objects from the server through ajax get 
 */
function getObjects() {
	console.log("Sending AJAX GET for the objects...");
	
	// Get user specified URL from the DOM
	objectUrl = $("#url-field").val();

	// Send ajax get-request for the objects
	ajaxGet(0, objectUrl, "");
}

/**
 * Sends an ajax query for objects 
 * @param {Number} indent The depth of the object tree hierarchy
 * @param {string} url The URL of the server to get the objects data from
 * @param {string} listId The id of the list DOM object that's being queried
 */
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

/**
 *  Display the objects as checkboxes in objectList 
 *  @param {XML Object} data The received XML data
 *  @param {Number} indent The depth of the object (recursive data discovery)
 *  @param {string} url The URL to the server (recursive data discovery)
 *  @param {string} listId The ID of the current list (recursive data discovery)
 */
function displayObjects(data, indent, url, listId) {
	if(indent === 0){
		// Clear the existing list
		$("#objectList").empty();
		
		// Append objects as checkboxes to the webpage
		$(data).find('Objects').each(function(){
			$(this).find("Object").each(function(){
				var id = $(this).find("id").text();
				
				manager.addObject(id);
				
				// Get lower hierarchy values (Subobjects/Infoitems)
				ajaxGet(indent + 1, url + "/" + id, "list-" + id);
			});
		});
	} else {
		// Subobjects/Infoitems
		$(data).find("Object").each(function(){
			var id = $($(this).find("id")[0]).text();
			var sub = [];
			
			$(this).find("Object").each(function(){
				var name = $(this).find("id").text();

				//ajaxGet(indent + 1, url + "/" + name);
				manager.find(id).addChild(id, name, "list-" + id);
				sub.push(name);
			});
			addInfoItems(this, id, indent);
			
			for(var i = 0; i < sub.length; i++){
				ajaxGet(indent + 1, url + "/" + sub[i], "list-" + id);
			}
		});
	}
}
/**
 * Adds InfoItem checkboxes under the list of the current object
 * @param {Object} parent The parent Object of the the current object
 * @param {string} id The ID of the current object
 */
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
		}
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
	$("#responseBox").html(formatNoHighlight(jqXHR.responseText));
	refreshEditor("response", "responseBox");
	
	console.log("Error sending to server: (" + exc +")");
}

/**
 * Nulls all inputs on every page
 */
function restart() {
	$("#progressbar li").removeClass("active");
	$("#page3").empty();
	$("#page2").empty();
	$("#page1").empty();
	loadPages(1);
	page = 1;
	$("#progressbar li").eq(0).addClass("active");
}

/* Functions related to the checkbox object tree */

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
 * Temp function, allows special characters pass through jQuery
 * @param {string} prefix Class/ID prefix (./#)
 * @param {string} selector Class/ID selector
 * @returns {string} the ID that passes from jQuery
 */
function jq(prefix, selector) {
	return prefix + selector.replace( /(:|\.|\[|\]|\/)/g, "\\$1" );
}

/**
 * Checks if a checkbox is a parent checkbox (has a child)
 * @param element The checkbox
 * @returns {Boolean} true if checkbox has a child otherwise returns false
 */
function isParent(element, index){
	return element != "checkbox" && element != "lower";
}

/**
 * Checks if the checkbox is a root (no parents)
 * @param jqid The ID of the checkbox
 * @returns {Boolean} true if checkbox with the given ID is root, otherwise returns false
 */
function isRootBox(jqid){
	return $(jqid).attr('class').split(' ').length === 1;
}