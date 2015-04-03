/* Classes that store information used in XML generation */

function OdfObject(id){
	this.id = id;
	this.subObjects = [];
	this.infoItems = [];
}

function InfoItem(name){
	this.name = name;
}