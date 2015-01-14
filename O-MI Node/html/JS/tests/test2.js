/* Test that writeXML writes valid xml */
QUnit.test("WriteXML Test #1 (Writing valid XML)", function(assert){
	initCheckBoxes('<Objects><Object><id>a</id></Object>' +
	'<Object><id>b</id></Object>' +
	'<Object><id>c</id></Object>' +
	'</Objects>');
	
	var selectedObjects = $("#objectList").find("input").filter(":checked");
	var passed = true;
	
	try {
		$.parseXML(writeXML(selectedObjects, "read", 0, 0));
	} catch (e){
		passed = false; //XML Parse throws error, invalid xml
	}
	assert.ok(passed, "Passed (O-DF Request is Valid XML)");
});

/* Test that Objects are written as XML Elements */
QUnit.test("WriteXML Test #2 (3 objects)", function (assert) {
	initCheckBoxes('<Objects><Object><id>a</id></Object>' +
	'<Object><id>b</id></Object>' +
	'<Object><id>c</id></Object>' +
	'</Objects>');
	
	var selectedObjects = $("#objectList").find("input").filter(":checked");
	var xml = $.parseXML(writeXML(selectedObjects, "read", 0, 0));
	
	var temp = $(xml).find("Object");
	var array = []; //Array of strings (Object id's)
	
	for(var i = 0; i < temp.length; i++){
		array.push($(temp[i]).text().trim());
	}
	assert.ok(array.indexOf("a") > -1, "Passed! (XML (request) contains Object a)");
	assert.ok(array.indexOf("b") > -1, "Passed! (XML (request) contains Object b)");
	assert.ok(array.indexOf("c") > -1, "Passed! (XML (request) contains Object c)");
	assert.ok(array.indexOf("d") == -1, "Passed! (XML (request) doesn't contain Object d)");
});

/* Test that TTL and Interval are written as XML Attributes */
QUnit.test("WriteXML Test #3 (TTL & Interval)", function (assert) {
	initCheckBoxes('<Objects><Object><id>a</id></Object></Objects>');
	var selectedObjects = $("#objectList").find("input").filter(":checked");
	var operation = "read";
	var ttl = 2;
	var interval = 2;
	var xml = $.parseXML(writeXML(selectedObjects, operation, ttl, interval));
	
	assert.ok($(xml).find("omi\\:omiEnvelope[ttl='" + ttl + "']").length == 1,
	"Passed! TTL is situated as Attribute in the correct Element");
	assert.ok($(xml).find("omi\\:" + operation + "[interval='" + interval + "']").length == 1,
	"Passed! Interval is situated as Attribute in the correct Element");
});

/* Test that the response is written to responseBox paragraph */
QUnit.test("PrintResponse Test #1", function(assert){
	printResponse("hello world");
	assert.ok("hello world" === $("#responseBox").text(), "Passed! (Writing Hello World response)");
});

/* Initialize checkboxes based on XML input and check all of them */
function initCheckBoxes(xml) {
	displayObjects($.parseXML(xml));
	checkAllBoxes();
}

function checkAllBoxes() {
	var checkboxes = document.getElementsByTagName('input'), cb;
	for(cb in checkboxes){
		if((' ' + checkboxes[cb].className + ' ').indexOf(' checkbox ') > -1){
			$(checkboxes[cb]).prop("checked", true); //Check all boxes
		}
	}
}