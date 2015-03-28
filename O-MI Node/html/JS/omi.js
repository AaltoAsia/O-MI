/* Class used to provide parameters from XML generation */
function Omi() {
	this.operation;
	this.ttl;
	this.interval;
	this.begin;
	this.end;
	this.newest;
	this.oldest;
	this.callback;
	this.requestId;

	this.request;
	this.subscribe;
	
	this.save = {};
}

Omi.prototype.update = function(operation, ttl, interval, begin, end, newest, oldest, callback, requestId){
	this.operation = operation;
	this.ttl = ttl;
	this.interval = interval;
	this.begin = begin;
	this.end = end;
	this.newest = newest;
	this.oldest = oldest;
	this.callback = callback;
	this.requestId = requestId;
	this.request = "";
	this.subscribe = "";
}

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
	this.save[this.operation]["requestId"] = this.requestId;
}

/* Change request id */
Omi.prototype.setId = function(id) {
	this.requestId = id;
};

/* Returns the request XML */
Omi.prototype.getRequest = function(objects) {
	if(this.request.length === 0){
		this.request = writeXML(objects, this);
	}
	return this.request;
};

/* Returns the subscription request XML */
Omi.prototype.getSub = function(requestId, objects) {
	if(this.subscribe.length === 0){
		this.subscribe = writeSubscribe(requestId, objects, this.ttl, this.interval, this.begin, this.end, this.newest, this.oldest, this.callback);
	}
	return this.subscribe;
};