/* What to do with the form submissal */
function submit() {
	var sensorID = $("#sensorId").val();
	var loc = $("#location").val();
	var url = document.URL;
	var path = url.substring(0, url.lastIndexOf("/"));

	/* Test */
	var readRequest = '<?xml version="1.0" encoding="UTF-8"?>'
			+ '<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
			+ 'xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">'
			+ '<omi:read msgformat="omi.xsd">'
	'<omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">'
			+ '<Objects>' + '<Object>' + '<id>' + sensorID + '</id>'
	'<InfoItem name="PowerConsumption">' + '</InfoItem>' + '</Object>'
			+ '</Objects>' + '</omi:msg>' + '</omi:read>'
			+ '</omi:omiEnvelope>';

	
	$.ajax({
		type : "GET",
		url : path + "/SensorData/test" + sensorID + ".html", //this needs to be changed in the future
		data: "json", //TO be changed (omi-response = xml ?)
		success : function(d) {
			console.log("SUCCESS");
			handleData(d);
		},
		error : function(error, textStatus, et) {
			console.log("error");
			//TODO: fix for the canvas -> div implementation
			//handleError(error);
		}
	});
}

/* Not used yet */
function handleOMI(d) {
	var xml = "<rss version='2.0'><channel><title>RSS Title</title></channel></rss>",
		xmlDoc = $.parseXML( xml ),
		$xml = $( xmlDoc ),
		$title = $xml.find( "title" );
		
	// Append "RSS Title" to #someElement
	$( "#infoCanvas" ).html($title.text());
}

function handleData(d) {
	// Expecting the data to be json; formatting
	var data = (d.split("")).map(function(c) {
		if (c === "," || c === "{")
			return c + "<br>";
		else if (c === "}")
			return "<br>" + c;
		else
			return c;
	}).join(""); 
	console.log(data);

	$("#infoCanvas").html(data);
	/*
	// Visualize the data (?), currently writing to canvas
	var canvas = document.getElementById("infoCanvas");
	var ctx = canvas.getContext("2d");
	readyFillText(canvas, ctx);
	wrapText(ctx, data, 20, 20, canvas.width, 15); */
}

function handleError(error) {
	var canvas = document.getElementById("infoCanvas");
	var ctx = canvas.getContext("2d");
	readyFillText(ctx);
	ctx.fillText("Sensor not found", 20, 20);
}

function wrapText(context, text, x, y, maxWidth, lineHeight) {
	var lines = text.split("\n");

	for (var ii = 0; ii < lines.length; ii++) {
		var line = "";
		var words = lines[ii].split(" ");
		for (var n = 0; n < words.length; n++) {
			var testLine = line + words[n] + " ";
			var metrics = context.measureText(testLine);
			var testWidth = metrics.width;
			if (testWidth > maxWidth) {
				context.fillText(line, x, y);
				line = words[n] + " ";
				y += lineHeight;
			} else {
				line = testLine;
			}
		}
		context.fillText(line, x, y);
		y += lineHeight;
	}
}

//Initializes the text drawing properties
function readyFillText(canvas, ctx){
	clearCtx(canvas, ctx); 
	ctx.fillStyle = "Yellow";
	ctx.font = "12px Arial";
}

//Clear canvas
function clearCtx(canvas, ctx) {
	ctx.fillStyle = "Blue";
	ctx.fillRect(0, 0, canvas.width, canvas.height);
}