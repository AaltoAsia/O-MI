# Fequently Asked Questions

## Why there are only 50 values in DB?
By default, the server stores only 50 values per InfoItem, but this can be adjusted
with `num-latest-values-stored` in `application.conf`. This is done to assure
that if program is used only for testing it will not create lot of unrequired
data.  

## How to use a different DB
In `application.conf` you can change `omi-service.database` parameter, that has 
`slick`, `influxdb` or `none` as value (`warp10` is also possible in separate 
release).

If parameter is set to `slick`, O-MI Node uses SQL database defined in
`slick-config`. Check [Slick documentation](http://slick.lightbend.com/doc/3.2.3/database.html).

If parameter is set to `influxdb`, O-MI Node uses InfluxDB and needs
configuration in `influx-config`, where only `database-name` and `address` are
required.

If parameter is set to `none`, O-MI Node uses only cached data. Thus, having
only current values of everything stored.

## How to modify or delete single existing value(s)
You can't do this easily at the moment. You can only delete a whole InfoItem, so if you need to remove specific values only you either need to do it directly with a database query or copy all other values, then delete and then rewrite them.


## What is Callback response history in WebClient?
When webclient connects to O-MI Node using WebSockets, it can use `0` as
callback address  allowing O-MI Node to respond asynchronously using current WebSocket
connection. This lets browser to receive responses from all request with
a callback address. 
These responses are shown when Callback Response History next to Response label is
clicked. The number next to it tells how many unread responses have been received.

## Which requests are optimized?
For all InfoItems, O-MI Node has cached the latest value they have. Thus, optimising
all read request that only access them. Cache also has all metadata(MetaDatas,descriptions,attributes,etc.).

Writing a completely new InfoItem is a lot slower than writing to a existing one, but performance of writing can be increased if value history is not needed (see below).



## Request are taking too long or timeout frequently. What can be done?

### Disable history database
If you do not need large amounts of data stored or are willing to store data
somewhere else, then you disable database and only have latest values stored in
cache. This is done by setting `omi-service.database` to `none`.

### Increase timeouts for large requests
Increase the timeouts listed under `akka.http.server` object in `application.conf`. You can try to inspect what timeout is being triggered from error responses or logs.


## How to publish other file/query format O-MI endpoints?
Not possible easily at the moment. Improvements to this matter has been planned.

If you are looking for how to connect other protocols to O-MI you might want to check Agent development guide or make a small O-MI Write client.

## How to run on ARM architecture (Raspberry pi)

LevelDB used in journal does not have ARM compatible version at the moment, 
so to run on devices with ARM architecture that database needs to be disabled from the [these lines of reference.conf](https://github.com/AaltoAsia/O-MI/blob/development/O-MI-Node/src/main/resources/reference.conf#L432-L437) file. Comment the lines with LevelDB configurations and uncomment the in-memory configuration.

Note that this also creates a risk of losing up to [snapshot-interval](https://github.com/AaltoAsia/O-MI/blob/development/O-MI-Node/src/main/resources/reference.conf#L44) of journal data (changes O-DF structure and subscriptions) if o-mi-node is killed.

## How to change JVM settings? (e.g. jvm memory/heap)

For Linux and Mac: [conf/application.ini](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/conf/application.ini) 

For Windows: [O_MI_NODE_config.txt](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/O_MI_NODE_config.txt)

## How to setup authentication and authorization?

See our [Authentication and authorization guide](https://github.com/AaltoAsia/O-MI/blob/development/docs/AuthenticationAuthorization.md)

## What are DB file locations? Where are the location settings?

The paths specified below are relative to the working directory of the O-MI Node, which is the root of extracted directory with zip and tar (tgz) releases or at `/usr/share/o-mi-node/` with linux packages deb and rpm. In installed packages (dep, rpm) the `./database/` direcotry contains links to `/var/lib/o-mi-node/database`.

1. By default, there is value history data in a h2 sql database in `./database/valuehistorydb/sensorDB.h2.mv.db`.
It contains values and related data that is used only for read request parameters: begin, end, oldest, newest. The path is set in this setting:
`slick-config.db.url = "jdbc:h2:file:./database/valuehistorydb/sensorDB.h2;LOCK_TIMEOUT=10000"`
Modify only the filepath, between the `:` and `;`.
The oldest values in the db are trimmed periodically every `trim-interval` to have `num-latest-values-stored` per InfoItem.

2. There are journals and snapshots of in-memory database in `./database/journaldb/journal` and `./database/journaldb/logs/snapshots`. These contains the actual O-DF tree and all related metadata, subscriptions and the latest value of each InfoItem. The paths are set in settings:
```
akka.persistence.journal.leveldb.dir = "./database/journaldb/journal"
akka.persistence.snapshot-store.local.dir = ""./database/journaldb/snapshots"
```

## How to deploy (linux and docker)?
See the [Readme "Running" section](https://github.com/AaltoAsia/O-MI#running)

## MacOS compilation problems
Check that you have right Java JDK version.

## How to enable write request (in docker version)?
If you don't use any integrated O-MI authorization system: use sed in docker file to change the configuration `omi-service.allowRequestTypesForAll = ["cancel", "read", "call", "write", "response", "delete"]`

