/**
 * Class that simulates the parent-child relationship of objects and SubObjects + InfoItems
 * @constructor
 * @param {string} id The ID of the Object
 */
function OdfObject(id, text){
	this.id = id;
	this.text = text;
	this.subObjects = []; // Child objects
	this.infoItems = []; // Child items
}

/**
 * Class with a single variable stored
 * @constructor
 * @param {string} name The name of the info item
 */
function InfoItem(name){
	this.name = name;
}

/**
* Write the O-DF message (XML) based on form input
* @param {Array} Array of objects, that have their 
* @param {Object} OMI object
* @returns {string} The generated request
*/
function writeXML(items, omi){
	//Using the same format as in demo
	var writer = new XMLWriter('UTF-8');
	setWriterSettings(writer);
	
	if(omi.operation === 'poll'){
		return writePoll(writer, omi.ttl, omi.requestIds);
	}
	
	writer.writeStartDocument();
	
	writeOmiInfo(writer, omi.ttl);

	//(second line)
	writer.writeStartElement('omi:'+ omi.operation);
	
	if(omi.operation === 'read'){
		writeObjects(writer, items, omi.interval, omi.begin, omi.end, omi.newest, omi.oldest, omi.callback);
	} else if (omi.operation === 'cancel'){
		writeRequestId(writer, omi.requestIds);
	} else if(omi.operation === 'write'){
		writeObjects(writer, items);
	} 
	writer.writeEndElement();
    writer.writeEndDocument();

    var request = writer.flush();
    return request;
}

/**
 * Writes the omi:msg element and its attributes
 * @param {XMLWriter} writer The XMLWriter instance used in writing the XML
 */
function writeMsg(writer){
	writer.writeStartElement('omi:msg');
	writer.writeAttributeString( 'xmlns', 'odf.xsd');
	writer.writeAttributeString( 'xsi:schemaLocation', 'odf.xsd odf.xsd');
}

/**
 * Writes the content of the O-MI request
 * @param {XMLWriter} writer The XMLWriter instance used in writing the XML
 * @param {Array} items The array of checked checkboxes
 * @param {Number} interval The interval of the subscription requests (can be undefined)
 * @param {string} begin The datetime string for the beginning of the request's timespan (can be undefined)
 * @param {string} end The datetime string for the end of the request's timespan (can be undefined)
 * @param {Number} newest The number of newest values (can be undefined)
 * @param {Number} oldest The number of oldest values (can be undefined)
 * @param {string} callback The URL of the subscription callback address (can be undefined)
 */

function writeObjects(writer, items, interval, begin, end, newest, oldest, callback){
	writer.writeAttributeString('msgformat', 'odf');
	
	if($.isNumeric(interval)) writer.writeAttributeString('interval', interval);
	
	if(begin){
		if(new Date(begin).getTime() > 0){
			writer.writeAttributeString('begin', begin);
		}
	}
	if(end){
		if(new Date(end).getTime() > 0){
			writer.writeAttributeString('end', end);
		}
	}
	if(newest){
		if($.isNumeric(newest)){
			writer.writeAttributeString('newest', newest);
		}
	}
	if(oldest){
		if($.isNumeric(oldest)){
			writer.writeAttributeString('oldest', oldest);
		}
	}
	
	if(callback) writer.writeAttributeString('callback', callback);
	
	//(third line)
	writeMsg(writer);
	
	writer.writeStartElement('Objects');

	//Payload
	var ids = [];
	var objects = [];
	
	for(var i = 0; i < items.length; i++){
		var item = items[i];
		var cl = $(item).attr("class");
		
		if(cl === "checkbox"){
			var obj = new OdfObject(item.id, item.name);
			addChildren(obj, items);
			objects.push(obj);
		}
	}
	
	for(var i = 0; i < objects.length; i++){
		writeObject(objects[i], writer);
	}
}

/**
 * Adds child items to a object
 * @param {OdfObject} object The object where the child items are added
 * @param {Array} items Child items to be added
 */
function addChildren(object, items){
	var children = [];
	
	for(var i = 0; i < items.length; i++){
		var c = $(items[i]).attr('class');
		if(c.split(" ").indexOf(object.id) > -1){
			children.push(items[i]);
		}
	}
	
	for(var i = 0; i < children.length; i++){
		var child = children[i];
		
		if(child.id){ //Object
			var subobj = new OdfObject(child.id, child.name);
			addChildren(subobj, items);
			object.subObjects.push(subobj);
		} else {
			var infoitem = new InfoItem(child.name);
			object.infoItems.push(infoitem);
		}
	}
}

/**
 * Writes an object and its children to the xml
 * @param {OdfObject} object The object to be written
 * @param {XMLWriter} writer The current xml writer
 */
/*  */
function writeObject(object, writer){
	writer.writeStartElement('Object');
	writer.writeElementString('id', object.text);
	
	// Write InfoItems BEFORE SubObjects (schema)
	for(var i = 0; i < object.infoItems.length; i++){
		writer.writeStartElement('InfoItem');
		writer.writeAttributeString('name', object.infoItems[i].name);
		writer.writeEndElement();
	}
	
	// Write subobjects
	for(var i = 0; i < object.subObjects.length; i++){
		writeObject(object.subObjects[i], writer);
	}
	writer.writeEndElement();
}

/**
 * Writes request ID to the XML
 * @param {XMLWriter} writer The current xml writer
 * @param {Array} requestIds The array of requestId's to be written
 */
function writeRequestId(writer, requestIds) {
	for(var i = 0; i < requestIds.length; i++){
		writer.writeStartElement('omi:requestId');
		writer.writeString(requestIds[i]);
		writer.writeEndElement();
	}
}

/**
 * Writes and returns a O-MI subscription (read) request
 * @param requestId
 * @param items
 * @param ttl
 * @param interval
 * @param begin
 * @param end
 * @param newest
 * @param oldest
 * @param callback
 * @returns {string} The generated request XML
 */
function writeSubscribe(requestId, items, ttl, interval, begin, end, newest, oldest, callback){
	//Using the same format as in demo
	var writer = new XMLWriter('UTF-8');
	
	setWriterSettings(writer);
	
	writer.writeStartDocument();

	writeOmiInfo(writer, ttl);
	writer.writeStartElement('omi:read');
	writer.writeAttributeString('msgformat', 'odf');

	if(begin){
		if(new Date(begin).getTime() > 0){
			writer.writeAttributeString('begin', begin);
		}
	}
	if(end){
		if(new Date(end).getTime() > 0){
			writer.writeAttributeString('end', end);
		}
	}

	if(newest){
		if($.isNumeric(newest)){
			writer.writeAttributeString('newest', newest);
		}
	}
	if(oldest){
		if($.isNumeric(oldest)){
			writer.writeAttributeString('oldest', oldest);
		}
	}

	writeRequestId(writer, requestId);
	writeMsg(writer)
	writer.writeStartElement('Objects');

	var ids = [];
	var objects = [];
	
	for(var i = 0; i < items.length; i++){
		var cl = $(items[i]).attr("class");
		
		if(cl === "checkbox"){
			var obj = new OdfObject(items[i].id);
			addChildren(obj, items);
			objects.push(obj);
		}
	}
	
	for(var i = 0; i < objects.length; i++){
		writeObject(objects[i], writer);
	}
	writer.writeEndElement();
    writer.writeEndDocument();

    return writer.flush();
}

/**
 * Writes a O-MI poll request (read request using requestId from cancel/subscription)
 * @param {XMLWriter} writer The current xml writer
 * @param {Number} ttl Time-to-live
 * @param {Array} requestIds The array of requestId's to be written
 * @returns {string} The generated request XML
 */
function writePoll(writer, ttl, requestIds) {
	writer.writeStartDocument();
	writeOmiInfo(writer, ttl);
	writer.writeStartElement('omi:read');
	writeRequestId(writer, requestIds);
	writer.writeEndDocument();
	
	return writer.flush();
}

/**
 * Writes the omi:omiEnvelope xml element, its attribute strings, and ttl
 * @param {XMLWriter} writer The current xml writer
 * @param {Number} ttl Time-to-live
 */
function writeOmiInfo(writer, ttl) {
	writer.writeStartElement('omi:omiEnvelope');
	writer.writeAttributeString('xmlns:xsi', 'http://www.w3.org/2001/XMLSchema-instance');
	writer.writeAttributeString('xmlns:omi', 'omi.xsd' );
	writer.writeAttributeString('xsi:schemaLocation', 'omi.xsd omi.xsd');
	writer.writeAttributeString('version', '1.0');
	
	if(ttl) writer.writeAttributeString('ttl', ttl);
}

/**
 * Sets XML writer settings
 * @param {XMLWriter} writer The current xml writer
 */
function setWriterSettings(writer) {
	writer.formatting = 'indented';
    writer.indentChar = ' ';
    writer.indentation = 2;
}
