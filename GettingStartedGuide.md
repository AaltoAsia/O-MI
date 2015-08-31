Getting startted
================
Download .zip or .tar file from [releases](https://github.com/AaltoAsia/O-MI/releases).
Exract the package. Now you should have something like this in the directory:
* `bin/` -- contains startup scripts.
* `conf/` -- contains application.conf
* `configs/` -- contains config for InternalAgents.
* `html/` -- contains webclient.
* `lib/` -- contains libraries used in project
* `callbackTestServer.py`
* `otaniemi3d-data.xml`
* `README.md`
* `SmartHouse.xml`

Now go to `bin/` directory with your console and run the start script depending on your OS:
1. `o-mi-node.bat` for Windows
2. `o-mi-node` for Unix and MAC

Now you should see some logging in your console.
To send O-MI request to O-MI Node you should go to [webclient](http:localhost:8080/html/webclient/index.html).

Webclient
=========

`Read All`
----------
On left side of the webpage there is yellow `Read All` button, click it. Now the webclient should show a request 
in text area on right side of the webpage under `Request` header. This request is sent now automatically. O-MI Node's response should be
seen in text area under Response` header. The response should contain the whole current state of O-DF structure stored in O-MI Node. 
O-DF structure under the `Read All button should have updated based on received response. 

`Read` request
--------------
From the left side of the webpage you can select the type of O-MI request. As you can see there is three different kinds of `Read` requests.
They all have same XML tag in request, but different attributes and elements in it, and they all do quite different thing.
`One-time-read` is the simplest but has the most optional parameters. Select it. Under the `O-MI Request` selection is section where 
all parameters are given. Each of them will give a tooltip if you hover cursor on top of them and they are enabled. `ttl` is abbreviation for time 
to live, that tells to O-MI Node how long it at most should take to response to the request or how long the request should be kept alive.
Once you set the desired ttl, the request on right side of webpage should have updated. `One-time-read` doesn't need any other parameters.

To select what to read from O-MI Node, you need to check some nodes from the O-DF Structure under the `Read All` button.
As you select nodes from structure the request will be updated. By selecting a directory you get a new Object to request. If Object
doesn't have any subnodes, the whole subtree under it is read. If you select an apple you get new InfoItem to request. 
If InfoItem is read you get it's current value and a empty MetaData tag, if it have some MetaData. MetaData can be read by 
selecting it from O-DF Structure. This adds a empty MetaData tag in InfoItem tag in request.

Request will not be send until you click blue `Send` button next to `Request` header.
Received response should contain current state of selected O-DF Structure or a error message.


`Write` request
----------------
Select `Write` from the O-MI Request selection. Now by selecting nodes from O-DF Structure they will appear in request. These
can be edited through the request's text area. You can also add new nodes by right clicking O-DF Structure's node and selecting
what you want to add. This will open a form where you can give all parameters you want.

O-MI Node will response with returnCode 200 if everything was ok, this will not quarantine that values were written. They may have
been too old or have same timestamp as a value in the database.

Because `Write` request makes possible to override current values and add new nodes, we want to restrict its usage. For this we have
three white lists, one for each: IPs, subnets and Shibboleth users. These can be managed from `application.conf`.

Agents
======
See [AgentDeveloperGuide](AgentDeveloperGuide.md) for information on agents. 
Adding custom jar file for agents is possible in bin/deploy this file is prioritized over the agents file in the lib folder.

Configuration
=============
Configuration settings for the program are read from [application.conf](O-MI Node/src/main/resources/application.conf).
