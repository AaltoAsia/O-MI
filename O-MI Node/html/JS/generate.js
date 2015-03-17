/* Generate the O-DF request */
function generateRequest(){
	var operation = $("#icons").find(".selected").attr("alt"); //Get the selected operation from the IconSelect object
	var ttl = $("#ttl").val(); 
	var interval = $("#interval").val();
	var begin = $("#begin").val();
	var end = $("#end").val();
	var newest = $("#newest").val();
	var oldest = $("#oldest").val();
	var callback = $("#callback").val();
	var requestId = $("#request-id").val();
	
	omi = new Omi(operation, ttl, interval, begin, end, newest, oldest, callback, requestId);
	
	var request = omi.getRequest(checkedObjects());
	
	console.log("Generated the O-DF request");
	
	//var formattedXML = formatXml(request);
	$("#editRequest").html(request);
	
    //$("#request").html(formattedXML.value); //Update the request textbox on the webpage
}

function checkedObjects() {
	return $("#objectList").find("input").filter(":checked"); //Filter the selected objects (checkboxes that are checked)
}