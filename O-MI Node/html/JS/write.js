/* 
* Write the O-DF message (XML) based on form input
* @param {Array} Array of objects, that have their 
* @param {Object} OMI object 
*/
function writeXML(items, omi){
	//Using the same format as in demo
	var writer = new XMLWriter('UTF-8');
	writer.formatting = 'indented';
    writer.indentChar = ' ';
    writer.indentation = 2;
	
	if(omi.operation === 'poll'){
		return writeSubscribe(omi.requestId, items, omi.ttl);
	} 
    
	writer.writeStartDocument();
	//(first line)
	writer.writeStartElement('omi:omiEnvelope');
	writer.writeAttributeString('xmlns:xsi', 'http://www.w3.org/2001/XMLSchema-instance');
	writer.writeAttributeString('xmlns:omi', 'omi.xsd' );
	writer.writeAttributeString('xsi:schemaLocation', 'omi.xsd omi.xsd');
	writer.writeAttributeString('version', '1.0');
	
	if(omi.ttl) writer.writeAttributeString('ttl', omi.ttl);
	
	//(second line)
	writer.writeStartElement('omi:'+ omi.operation);
	
	if(omi.operation === 'read'){
		writeObjects(writer, items, omi.interval, omi.begin, omi.end, omi.newest, omi.oldest, omi.callback);
	} else if (omi.operation === 'cancel'){
		writeCancel(writer, omi.requestId);
	} else if(omi.operation === 'write'){
		writeObjects(writer, items);
	}
	
	writer.writeEndElement();
    writer.writeEndDocument();

    var request = writer.flush();

    return request;
}

function writeMsg(writer){
	writer.writeStartElement('omi:msg');
	writer.writeAttributeString( 'xmlns', 'omi.xsd');
	writer.writeAttributeString( 'xsi:schemaLocation', 'odf.xsd odf.xsd');
}

function writeObjects(writer, items, interval, begin, end, newest, oldest, callback){
	writer.writeAttributeString('msgformat', 'omi.xsd');
	
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
}

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
			var subobj = new OdfObject(child.id);
			addChildren(subobj, items);
			object.subObjects.push(subobj);
		} else {
			var infoitem = new InfoItem(child.name);
			object.infoItems.push(infoitem);
		}
	}
}

/* Writes an object and its children to the xml */
function writeObject(object, writer){
	writer.writeStartElement('Object');
	writer.writeElementString('id', object.id);
	
	// Write InfoItems BEFORE SubObjects
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

function writeCancel(writer, requestId) {
	writer.writeStartElement('omi:requestId');
	writer.writeString(requestId);
	writer.writeEndElement();
}

function writeSubscribe(requestId, items, ttl, interval, begin, end, newest, oldest, callback){
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
	writer.writeStartElement('omi:read');
	writer.writeAttributeString('msgformat', 'omi.xsd');
	
	//if($.isNumeric(interval)) writer.writeAttributeString('interval', interval);
	
	if(begin){
		console.log(new Date(begin).getTime());
		if(new Date(begin).getTime() > 0){
			writer.writeAttributeString('begin', begin);
		}
	}
	if(end){
		console.log(new Date(end).getTime());
		if(new Date(end).getTime() > 0){
			writer.writeAttributeString('end', end);
		}
	}
	console.log("Newest: " + newest);
	console.log("Oldest: " + oldest);
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
	
	//if($.isNumeric(interval)) writer.writeAttributeString('interval', interval);
	
	//(third line)
	writer.writeStartElement('omi:requestId');
	writer.writeString(requestId);
	writer.writeEndElement();
	writer.writeStartElement('omi:msg');
	writer.writeAttributeString( 'xmlns', 'omi.xsd');
	writer.writeAttributeString( 'xsi:schemaLocation', 'odf.xsd odf.xsd');
	writer.writeStartElement('Objects');
	//Payload
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

    var request = writer.flush();

    return request;
}
