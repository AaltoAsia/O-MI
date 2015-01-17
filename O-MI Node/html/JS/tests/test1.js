/* Test that displayObjects function appends the correct number of labels (and checkboxes) to #objectList */
QUnit.test( "Display Objects Test #1 (Checkbox count)", function( assert ) {
	assert.ok( 1 === displayCount('<Objects><Object><id>Refrigerator123</id></Object></Objects>'), "Passed! (1 object)" );
	assert.ok( 2 === displayCount('<Objects><Object><id>a</id></Object><Object><id>b</id></Object></Objects>'), "Passed! (2 objects)" );
	
	var xml = '<Objects><Object><id>Refrigerator123</id><InfoItem name="Temperature" /><InfoItem name="Weight" /> ' +
	'<InfoItem name="????" /><InfoItem name="asdf" /></Object>' +
	'<Object><id>HeatingController321</id></Object>' +
	'<Object><id>WeatherStation651</id><InfoItem name="Temperature" /></Object>' +
	'</Objects>';
	assert.ok( 8 === displayCount(xml), "Passed! (8 objects)" );
});

/* Test that labels display correct text (displayObjects function) */
QUnit.test("Display Objects Test #2 (Matching texts on label)", function(assert) {
	assert.ok(
		true === displayNamesMatch('<Objects><Object><id>a</id></Object><Object><id>b</id></Object></Objects>', ['a', 'b']),
		"Passed! (Testing: 2 equal objects)"
	);
	assert.ok(
		false === displayNamesMatch('<Objects><Object><id>a</id></Object></Objects>', ['a', 'b']),
		"Passed! (Testing: Missing 1 objects)"
	);
	assert.ok(
		false === displayNamesMatch('<Objects><Object><id>a</id></Object></Objects>', ['b']),
		"Passed! (Testing: No matching objects)"
	);
	assert.ok(
		true === displayNamesMatch('<Objects><Object><id>a</id></Object><Object><id>b</id></Object><Object><id>c</id></Object></Objects>',
		['a','b','c']),
		"Passed! (Testing: 3 equal objects)"
	);
});

QUnit.test("Display Objects Test #3 (Correct classes)", function(assert) {
	assert.ok(1 === displayClassCount('<Objects><Object><id>Refrigerator123</id></Object></Objects>', true),
	"Passed! (1 normal box)");
	assert.ok(3 === displayClassCount('<Objects><Object><id>a</id></Object><Object><id>b</id></Object><Object><id>c</id></Object></Objects>'
	, true),
	"Passed! (3 normal boxes)");
	
	var xml = '<Objects><Object><id>Refrigerator123</id><InfoItem name="Temperature" /><InfoItem name="Weight" /> ' +
	'<InfoItem name="????" /><InfoItem name="asdf" /></Object>' +
	'<Object><id>HeatingController321</id></Object>' +
	'<Object><id>WeatherStation651</id><InfoItem name="Temperature" /></Object>' +
	'</Objects>';
	
	assert.ok(3 === displayClassCount(xml, true),
	"Passed! (3 normal boxes (and x InfoItems))");
	assert.ok(5 === displayClassCount(xml, false),
	"Passed! (5 InfoItem boxes (and x normal boxes)");
});

/* Return the number of labels (same as checkboxes) appended to objectList using the function displayObjects 
* @param {String} the XML string of the sensor data
*/
function displayCount(xmlString){
	displayObjects($.parseXML(xmlString));
	
	var labelCount = document.querySelectorAll("#objectList > label").length;
	
	return labelCount;
}

/* Return the number of normal(true)/Non-InfoItem(false) checkboxes */
function displayClassCount(xmlString, normal){
	displayObjects($.parseXML(xmlString));
	
	var labels = document.querySelectorAll("#objectList > label");
	var count = 0;
	
	for(var i = 0; i < labels.length; i++){
		var cl = $(labels[i]).find("input").attr("class"); //Checkbox class
		
		if((cl.indexOf("lower") == -1) == normal){ //Lower is the class if InfoItem-boxes
			count += 1;
		}
	}
	return count;
}

/* 
* @param {String} the XML string of the sensor data
* @param {Array} array of strings that should be in the labels
*/
function displayNamesMatch(xmlString, names) {
	displayObjects($.parseXML(xmlString));
	
	var labels = $("#objectList").children(); // Array of labels
	var textArr = [];
	
	for(var i = 0; i < labels.length; i++){
		var temp = $(labels[i]).text();
		
		// Filter out empty strings
		if(temp.length > 0){
			textArr.push(temp);
		}
	}
	
	for(var i = 0; i < names.length; i++){
		if(textArr.indexOf(names[i]) === -1){
			return false //String not found on labels
		}
	}
	return true; //All strings found
}