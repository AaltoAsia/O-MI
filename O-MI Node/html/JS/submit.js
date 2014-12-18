var iconSelect;

window.onload = function(){
	iconSelect = new IconSelect("operation-select", 
                {'selectedIconWidth':48,
                'selectedIconHeight':48,
                'selectedBoxPadding':1,
                'iconsWidth':48,
                'iconsHeight':48,
                'boxIconSpace':1,
                'vectoralIconNumber':2,
                'horizontalIconNumber':6});

	var icons = [];
	icons.push({'iconFilePath':'Resources/icons/read.png', 'iconValue':'read'});
	icons.push({'iconFilePath':'Resources/icons/write.png', 'iconValue':'write'});
	icons.push({'iconFilePath':'Resources/icons/subscribe.png', 'iconValue':'subscribe'});
	icons.push({'iconFilePath':'Resources/icons/cancel.png', 'iconValue':'cancel'});
	
	iconSelect.refresh(icons);
};

function getObjects() {
	//Get the current path of the file
	var url = document.URL;
	var path = url.substring(0, url.lastIndexOf("/"));
	
	$.ajax({
        type: "GET",
        url: path + "/SensorData/objects.json",
		dataType: "json",
        success: displayObjects,
		error: handleError
    });
}

function displayObjects(data) {
	var p = new DOMParser();
	document.getElementById('testbox').appendChild(document.createTextNode(data));
}

function handleError(jqXHR, errortype, exc) {
	alert(exc | errortype);
}