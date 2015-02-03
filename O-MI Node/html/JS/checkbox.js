/* Eventlistener for object tree updating */
$(document).on('click', '.checkbox', function() {
	var ref = $(this); //Reference (jquery object) of the clicked button
	var id = ref.attr('id');
	
	//Parent item clicked
	if(id){
		propChildren(ref);
		propParent(ref);
	} else { 
		propParent(ref);
	}

	function propChildren(parent){
		var parentId = $(parent).attr("id");
		//Find child items and mark their value the same as their parent
		getChildren(parentId).each(function(){
			$(this).prop('checked', parent.is(':checked'));
			propChildren($(this));
		});
	}
	
	/* Child is a jquery object */
	function propParent(child){
		//ChildItem clicked;
		var parentId = child.attr('class').split(' ').find(isParent);
		if(parentId){
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
			return $(this).attr('class').indexOf(id) > -1;
		});
	}
	
	/* Temp function, allows special characters pass through jQuery */
	function jq(prefix, myid) {
		return prefix + myid.replace( /(:|\.|\[|\]|\/)/g, "\\$1" );
	}
	
	function isParent(element, index, array){
		return element != "checkbox" && element != "lower";
	}
	
	function isRootBox(jqid){
		return $(jqid).attr('class').split(' ').length == 1;
	}
});
