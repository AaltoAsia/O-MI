function Omi(operation, ttl, interval, begin, end, newest, oldest, callback, requestId) {
	this.operation = operation;
	this.ttl = ttl;
	this.interval = interval;
	this.begin = begin;
	this.end = end;
	this.newest = newest;
	this.oldest = oldest;
	this.callback = callback;
	this.request = "";
	this.subscribe = "";
	this.requestId = requestId;
}

Omi.prototype.setId = function(id) {
	this.requestId = id;
};

Omi.prototype.getRequest = function(objects) {
	if(this.request.length === 0){
		this.request = writeXML(objects, this);
	}
	return this.request;
};

Omi.prototype.getSub = function(requestId, objects) {
	if(this.subscribe.length === 0){
		this.subscribe = writeSubscribe(requestId, objects, this.ttl, this.interval, this.begin, this.end, this.newest, this.oldest, this.callback);
	}
	return this.subscribe;
};