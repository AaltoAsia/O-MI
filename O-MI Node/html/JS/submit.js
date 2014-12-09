/* What to do with the form submissal */
function submit() {
	var sensorID = $("#sensorId").val();
	var loc = $("#location").val();
	var url = document.URL;
	var path = url.substring(0, url.lastIndexOf("/"));

	var readRequest = '<?xml version="1.0" encoding="UTF-8"?>' +
		'<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' +
		'xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">' +
			'<omi:read msgformat="omi.xsd">'
				'<omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">' +
					'<Objects>' + 
						'<Object>' + 
							'<id>' + sensorID + '</id>'
								'<InfoItem name="PowerConsumption">' + 
							'</InfoItem>' +
						'</Object>' +
					'</Objects>' +
				'</omi:msg>' +
			'</omi:read>' +
		'</omi:omiEnvelope>';
	
	$.ajax({
		type : "GET",
		url : path + "/SensorData/test" + sensorID + ".html",
		success : function(d) {
			console.log("SUCCESS");

			// Expecting the data to be json
			var data = (JSON.stringify(d).split("")).map(function(c) {
				if (c === "," || c === "{")
					return c + "\n";
				else if (c === "}")
					return "\n" + c;
				else
					return c;
			}).join("");
			console.log(data);

			// Visualize the data (?), currently writing to canvas
			var canvas = document.getElementById("infoCanvas");
			var ctx = canvas.getContext("2d");
			ctx.fillStyle = "Blue";
			ctx.fillRect(0, 0, canvas.width, canvas.height);
			ctx.fillStyle = "Yellow";
			ctx.font = "12px Arial";
			wrapText(ctx, data, 20, 20, canvas.width, 15);
		},
		error : function(error, textStatus, et) {
			console.log("error");
			var canvas = document.getElementById("infoCanvas");
			var ctx = canvas.getContext("2d");
			ctx.fillStyle = "Blue";
			ctx.fillRect(0, 0, canvas.width, canvas.height);
			ctx.fillStyle = "Yellow";
			ctx.font = "12px Arial";
			ctx.fillText("Sensor not found", 20, 20);
		}
	});
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