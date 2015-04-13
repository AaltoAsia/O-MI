/*
 * The following code is an example on how to send an AJAX request to the server, in order to get the sensor data OMI.
 * The jQuery's $(function(){ ... }) is to make sure that jQuery is loaded, but it's not mandatory in case jQuery is loaded
 * before using the code. Also Zepto.js can be used instead of jQuery.
 */

$(function(){
	/* The URL of the server, replace with 'http://mursu.hookedoncaffeine.com:18080' if using our server */
	serverUrl = 'http://localhost:8080';

	/* The path to the XML file containing the Read Request, replace with your own path */
	xml1 = 'example/test1.xml';
	xml2 = 'example/test2.xml';
	
	/* Call the sendAjaxRequest to send a request */
	loadXML(xml1, serverUrl);
	loadXML(xml2, serverUrl);
	
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
		    timeout: 1000, // can be removed
		    success: function(xml){
		    	/* Upon getting the XML request, send it by calling the sendAjaxRequest function */
		    	/* Note: The function can be called anywhere by giving the xml string and server url as parameters */
		        sendAjaxRequest(xml, serverUrl); 
		    }
		});
	}

	/**
	 * Sends XML with AJAX POST to the server
	 * @param {string} xml The XML string of the request to be sent
	 * @param {Number} serverUrl The URL of the server to send the request to
	 */
	function sendAjaxRequest(xml, serverUrl) {
		/* Using the jQuery ajax shorthand function to send the POST request */
		$.ajax({
			type: "POST",
			url: serverUrl, // The server url here
			data: xml, // The request here
			contentType: "text/xml",
			processData: false,
			dataType: "text",
			success: function(response){
				/* response here, do something with it */
				
				console.log(formatXML(response)); // Formatting the xml for the lulz and a cleaner structure
				console.log(parseToJson(response)); // Check JSON parsing
			},
			error: function(a, b, c){
				// Error handling if necessary
			},
		});
	}
	
	/**
	 * Formats XML string, giving it the necessary indentations and line breaks
	 * @param {string} xml The XML string
	 * @returns {string} The formatted XML string
	 */
	function formatXML(xml) {
		var formatted = '';
	    var reg = /(>)(<)(\/*)/g;
	    xml = xml.replace(reg, '$1\r\n$2$3');
	    var pad = 0;
	    jQuery.each(xml.split('\n'), function(index, node) {
	        var indent = 0;
			var trim = node.trim();
			if(trim.length > 0){
				if (trim.match( /.+<\/\w[^>]*>$/ )) {
					indent = 0;
				} else if (trim.match( /^<\/\w/ )) {
					if (pad != 0) {
						pad -= 2;
					}
				} else if (trim.match( /^<\w[^>]*[^\/]>.*$/ )) {
					indent = 2;
				} else {
					indent = 0;
				}

				var padding = '';
				for (var i = 0; i < pad; i++) {
					padding += ' ';
				}

				formatted += padding + trim + '\r\n';
				pad += indent;
			}
	    });
	    return formatted;
	}
	
	/**
	 * Parses XML to JSON string (brute force)
	 * NOTE: Function was created to follow the O-MI schema, and the vtt/otaakari4 structure in mursu
	 * @oaram {string} xml The xml string
	 * @param {Number} buildingDepth The depth of the building Object in Objects tree
	 * @returns {string} the JSON string
	 */
	function parseToJson(xml, buildingDepth){
		var xmlDoc = $.parseXML(xml);
		var str = '[';
		
		$(xmlDoc).find('Objects > Object > Object').each(function(){
			var count = 0;
			$(this).each(function(){
				$($(this).find("Object")).each(function(){
					if(count === 0){
						str += '{';
					} else {
						str += ',{';
					}
					str += '"building":"' + $($(this).parent().find("id")[0]).text() + '"';
					str += ', "roomID":"' + $(this).find("id").text() + '"';
					str += ', "type":"' + $(this).find('InfoItem').attr('name') + '"';
					str += ', "value":"' + $(this).find('value').text() + '"';
					str += '}';
					count += 1;
				});
			});
		});
		str += ']';
		
		return str;
	}
	
});
