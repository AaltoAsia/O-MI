/**
 * Class used to store parameters from XML generation
 * NOTE: this was made as an emergency solution to a usability problem, it is extremely inefficient and might
 * not be fixed due to time constraints
 * Suggestion: The operation-specific inputs on page 2 are currently loaded from external html files, 
 * these inputs should be put directly into the html files (pages/page2.html), input id's should be changed to classes and
 * jQuery selectors should be changed to match these changes.
 * @constructor
 */
function Omi() {
	this.operation;
	this.ttl;
	this.interval;
	this.begin;
	this.end;
	this.newest;
	this.oldest;
	this.callback;
	this.requestIds;

	this.request;
	this.subscribe;
	
	this.save = {};
}

/**
 * Updates the parameters
 */
Omi.prototype.update = function(operation, ttl, interval, begin, end, newest, oldest, callback, requestIds){
	this.operation = operation;
	this.ttl = ttl;
	this.interval = interval;
	this.begin = begin;
	this.end = end;
	this.newest = newest;
	this.oldest = oldest;
	this.callback = callback;
	this.requestIds = requestIds;
	this.request = "";
	this.subscribe = "";
}

/**
 * Save old parameters in an array
 */
Omi.prototype.saveOptions = function(){
	if(!this.operation){
		return;
	}
	if(!this.save[this.operation]){
		this.save[this.operation] = {};
	}
	this.save[this.operation]["ttl"] = this.ttl;
	this.save[this.operation]["interval"] = this.interval;
	this.save[this.operation]["begin"] = this.begin;
	this.save[this.operation]["end"] = this.end;
	this.save[this.operation]["newest"] = this.newest;
	this.save[this.operation]["oldest"] = this.oldest;
	this.save[this.operation]["callback"] = this.callback;
	this.save[this.operation]["requestId"] = this.requestIds;
}

/**
 * Generates and returns the O-MI request
 * @returns {string} The generated request XML
 */
Omi.prototype.getRequest = function(objects) {
	if(this.request.length === 0){
		this.request = writeXML(objects, this);
	}
	return this.request;
};

/**
 * Generates and returns the O-MI subscription request
 * @returns {string} The generated request XML
 */
Omi.prototype.getSub = function(requestId, objects) {
	if(this.subscribe.length === 0){
		this.subscribe = writeSubscribe(requestId, objects, this.ttl, this.interval, this.begin, this.end, this.newest, this.oldest, this.callback);
	}
	return this.subscribe;
};