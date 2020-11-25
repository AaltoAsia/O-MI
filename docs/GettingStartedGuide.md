Getting startted
================
Download .zip or .tar file from [releases](https://github.com/AaltoAsia/O-MI/releases).
Exract the package. Now you should have something like this in the directory:
* `bin/` -- contains startup scripts.
* `conf/` -- contains configuration files, application.conf.
* `database/` -- contains database files which preserve data when restarting.
* `README.md`

Now go to `bin/` directory with your console and run the start script depending on your OS:
1. `o-mi-node.bat` for Windows
2. `o-mi-node` for Unix and MAC

Now you should see some logging in your console.

To send O-MI request to O-MI Node you should go to [webclient at localhost:8080](http://localhost:8080/html/webclient/index.html).

Webclient
=========

`Read All`
----------
On left side of the webpage there is yellow `Read All` button, click it. Now the webclient should show a request 
in text area on right side of the webpage under `Request` header. This request is sent now automatically. O-MI Node's response should be
seen in text area under `Response` header. The response should contain the whole current state of O-DF structure stored in O-MI Node. 
O-DF structure under the `Read All` button should have updated based on received response. 

`Read` request
--------------
From the left side of the webpage you can select the type of O-MI request. As you can see there is three different kinds of `Read` requests.
They all have the same XML tag in request, but different attributes and elements in it, and they all do somewhat different thing.
`One-time-read` is the simplest but has the most optional parameters. Select it. Under the `O-MI Request` selection is the section where 
all parameters are given. Each of them will give a tooltip if you hover cursor on top of them and they are enabled. `ttl` is abbreviation for time 
to live, that tells to O-MI Node how long it at most should take to response to the request or how long the request should be kept alive.
Once you set the desired ttl, the request on right side of webpage should have updated. `One-time-read` doesn't need any other parameters.

To select what to read from O-MI Node, you need to check some nodes from the O-DF Structure under the `Read All` button.
As you select nodes from structure the request will be updated. By selecting a directory you get a new Object to request. If Object
doesn't have any subnodes, the whole subtree under it is read when the request is processed. If you select an apple you get new InfoItem to the request. 
If InfoItem is read you will get its current value. If a node has MetaData, it can be read by 
selecting it from O-DF Structure. This adds a empty MetaData tag in InfoItem tag in the request.

Request will not be sent until you click blue `Send` button next to `Request` header.

Received response should contain current latest value of selected O-DF Structure or an error message.

### Reading more than one value

Open the `Optional Parameters` section by clicking it. There are two ways to get many values with the optional parameters. The first is that you can give the number values you want to request by `newest` or `oldest` parameter, and either the number of newest or oldest values will be returned. The second way is to give `begin` and/or `end` timestamps, which will select all values within the given timerange.


`Write` request
----------------
Select `Write` from the O-MI Request selection. Now by selecting nodes from O-DF Structure they will appear in request. These
can be edited through the request's text area. You can also add new nodes by **right clicking** O-DF Structure's node and selecting
what you want to add. This will open a form where you can give all parameters you want. Timestamp and type of an O-DF `<value>` can be skipped, then O-MI Node inserts current server time upon receiving and will either use the string type or try to infer the correct type, depending on the implementation or settings.

O-MI Node will response with returnCode 200 if everything was ok. This will not guarantee that values were written, if they are older than the latest value or have same timestamp as an existing value in the database, the server might have been configured to ignore those without giving an error.

Because `Write` request makes possible to override current values and add new nodes, we want to restrict its usage. For this we have
different settings: IPs, subnets, authentication proxy users or authentication api settings. These can be managed from `application.conf`.

`Subscription` request
------------------------

Subscriptions are read requests which may receive more than one response over time. There are two different types of subscriptions which only differ by having different `interval` parameter. If `interval` is greater than zero, the current state of the requested structure is returned on every `interval` number of seconds. If the `interval` is `-1` the response is only sent when a new value is written in the O-MI Node. This is called as an event subscription.

To make an event subscription in the webclient, simply select which nodes you want to request from the O-DF tree and then `Subscription` from O-MI Request box. Then send the request. As there can be any number of responses, they will be listed in the `Callback response history` instead of the usual Response field, with a number indicating how many responses has been received.

If the subscription didn't work, you should check a few things. The webclient should be set to use websockets, which is the default, but you can check that the Server url box on the top bar starts with `ws`. Then select `Subscription` from O-MI Request box and make sure that `interval` is `-1` and `callback` in Optional Parameters is `0`, meaning that the response will be sent to the open connection instead of some other url. If implementing the subscription in some other system, make sure it has a mechanism to keep the connection open (e.g. send a websocket ping or an empty message every 40 seconds).

Server Agents
=============
See [AgentDeveloperGuide](AgentDeveloperGuide.md) for information on agents. 

Server Configuration
=====================
Configuration settings for the O-MI Node are read from [application.conf](O-MI-Node/src/main/resources/application.conf).
