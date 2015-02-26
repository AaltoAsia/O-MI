/* 
* Write the O-DF message (XML) based on form input
* @param {Array} Array of objects, that have their 
* @param {String} the O-DF operation (read, write, cancel, subscribe)
* @param {Number} Time to live 
* @param {Number} Message interval
* @param {function} Callback function (not used atm)
*/
function writeXML(items, operation, ttl, interval, begin, end, newest, oldest, callback){
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
	
	if($.isNumeric(interval)) writer.writeAttributeString('interval', interval);
	
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
	
	if(callback) writer.writeAttributeString('callback', callback);
	
	//(third line)
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

function addChildren(object, items){
	var children = [];
	
	for(var i = 0; i < items.length; i++){
		var c = $(items[i]).attr('class');
		if(c.indexOf(object.id) > -1){
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