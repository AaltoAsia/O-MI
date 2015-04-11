/**
 * Passes input data to OMI request generation and display the request in the editRequest textarea
 * NOTE: This along with Omi class are relics of old, and extremely unefficient at the moment, fixing them might not
 * be happening due to time constraints
 */
function generateRequest(){
	var operation = $("#icons").find(".selected").attr("alt"); //Get the selected operation from the IconSelect object
	var ttl = $("#ttl").val(); 
	if(ttl){
		ttl = ttl.replace(/\s/g, '');
	}
	var interval = $("#interval").val();
	var begin = $("#begin").val();
	var end = $("#end").val();
	var newest = $("#newest").val();
	var oldest = $("#oldest").val();
	var callback = $("#callback").val();
	var requestIds = [];
	
	$(".requestId").each(function(){
		requestIds.push($(this).val());
	});
	
	if(!omi){
		omi = new Omi();
	} 
	if(omi.operation && omi.operation != operation){
		omi.saveOptions();
	}
	omi.update(operation, ttl, interval, begin, end, newest, oldest, callback, requestIds);
	
	var request = omi.getRequest(checkedObjects());
	$("#editRequest").html(request);
}

/**
 * Gets all checkboxes that are checked
 * @returns Array of checked checkboxes
 */
function checkedObjects() {
	return $("#objectList").find("input").filter(":checked"); //Filter the selected objects (checkboxes that are checked)
}