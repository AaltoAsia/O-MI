/* Class for managing all checkbox instances */
function ObjectBoxManager(){
	this.objects = []; // Array for storing objects
	
	/* Created a DOM checkbox and adds the reference object to objects array */
	this.addObject = function(id) {
		$('<li><label><input type="checkbox" class="checkbox" id="' + id + '"/>' + id + '</label></li>').appendTo("#objectList"); 
		$('<ul id="list-' + id + '"></ul>').appendTo("#objectList");
		
		this.push(new ObjectBox(id, 0));
	}
	
	/* Pushes an object into the array */
	this.push = function(o) {
		this.objects.push(o);
	};
	
	/* Finds the object with the specified id */
	this.find = function(id) {
		var o;
		this.objects.forEach(function(elem, index, array){
			var temp = elem.find(id);
			
			if(temp){
				o = temp;
				return;
			}
		});
		return o;
	};
}

/* Class for simulating a single checkbox instance */
function ObjectBox(id, depth, parent){
	this.id = id;
	this.depth = depth;
	this.parent = parent;
	this.children = [];
	
	this.getPath = function() {
		if(this.parent){
			return this.parent.getPath() + "/" + this.id;
		}
		return this.id;
	};
	
	this.find = function(id) {
		if(this.id === id){
			return this;
		}
		var o;
		this.children.forEach(function(elem, index, array){
			var temp = elem.find(id);
			
			if(temp){
				o = temp;
				return;
			}
		});
		return o;
	};
	
	this.addChild = function(parendId, name, listId) {
		var margin = "20px";
		
		var str = '<li><label><input type="checkbox" class="checkbox ' + id + '" id="' + name + '"/>' + name + '</label></li>';
		
		$(str).appendTo("#" + listId); 
		$("#" + listId).last().css({ marginLeft: margin });
		$('<ul id="list-' + name + '"></ul>').appendTo("#" + listId);
		$("#" + listId).last().css({ marginLeft: margin });

		$("#" + listId + ":last-child").css({ marginLeft:margin });
		
		this.children.push(new ObjectBox(name, this.depth + 1, this));
	};
	
	this.getDepth = function() {
		return this.depth;
	};
}

/* Event handler for checking all checkboxes (button click) */
$(document).on('click', '#checkall', function() {
	console.log("Checking all boxes");
	$(".checkbox").prop('checked', true);
});

/* Event handler for unchecking all checkboxes (button click) */
$(document).on('click', '#uncheckall', function() {
	console.log("Unchecking all boxes");
	$(".checkbox").prop('checked', false);
});


/* Eventlistener for object tree updating */
$(document).on('click', '.checkbox', function() {
	update(this);
});

/* Update the parents and children of the checked checkbox (obj) */
function update(obj) {
	var ref = $(obj); //Reference (jquery object) of the clicked button
	var id = ref.attr('id');
	
	//Parent item clicked
	if(id){
		propChildren(ref, true);
		propParent(ref);
	} else { 
		propParent(ref);
	}
}

/* Prop all children to match the propped checkbox, if dig is true, sends an ajax query to the server for deeper objects */
function propChildren(parent, dig){
	var parentId = $(parent).attr("id");
	
	//Find child items and mark their value the same as their parent
	var children = getChildren(parentId);
	var url = $("#url-field").val();
	
	if(children.length == 0 && parentId && dig){
		// Using manager from submit.js
		// TODO: Change manager to static class
		var o = manager.find(parentId);
		ajaxGet(o.getDepth() + 1, url + "/" + o.getPath(), "list-" + parentId);
	}
	
	children.each(function(){
		$(this).prop('checked', $(parent).is(':checked'));
		propChildren(this, false);
	});
}

/* Child is a jquery object */
function propParent(child){
	//ChildItem clicked;
	var ids = ($(child).attr('class')).split(' ').filter(isParent);
	if(ids.length > 0){
		var parentId = ids[0];
		var jqId = jq("#", parentId);

		var checked = $("#objectList").find(jq(".", parentId)).filter(":checked").length > 0;
		
		if(checked){
			//Change parent item check value
			$(jqId).prop('checked', true);
			
			if(!isRootBox(jqId)){
				propParent($(jqId));
			}
		}
	}
}

/* Temp function, returns an array of children with the given id (as their class) */
function getChildren(id){
	return $("#objectList").find("input").filter(function(){
		return $(this).attr('class').split(" ").indexOf(id) > -1;
	});
}

/* Temp function, allows special characters pass through jQuery */
function jq(prefix, myid) {
	return prefix + myid.replace( /(:|\.|\[|\]|\/)/g, "\\$1" );
}

/* Returns true if checkbox (element) has a child, otherwise returns false */
function isParent(element, index, array){
	return element != "checkbox" && element != "lower";
}

/* Returns true if checkbox with given id is root, otherwise returns false */
function isRootBox(jqid){
	return $(jqid).attr('class').split(' ').length === 1;
}
