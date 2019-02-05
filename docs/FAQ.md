# Fequently Asked Questions

## Why there are only 50 values in DB?
By default server stores only 50 values per InfoItem, but this can be adjusted
with `num-latest-values-stored` in `application.conf`. This is done to assure
that if program is used only for testing it will not create lot of unrequired
data.  

## How to use a different DB
In `application.conf` you can change `omi-service.database` parameter, that has 
`slick`, `influxdb` or `none` as value (`warp10` is also possible in separate 
release).

If parameter is set to `slick`, O-MI Node uses SQL database defined in
`slick-config`. Check [Slick documentation](http://slick.lightbend.com/doc/3.2.3/database.htmli).

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


## How to publish other file/query format endpoints?
Not possible easily at the moment. Improvements to this matter has been planned.

## How to run on ARM architecture

LevelDB used in journal does not have ARM compatible version at the moment, 
so to run on devices with ARM architecture that database needs to be disabled from the [these lines of reference.conf](https://github.com/AaltoAsia/O-MI/blob/development/O-MI-Node/src/main/resources/reference.conf#L432-L437) file. Comment the lines with LevelDB configurations and uncomment the in-memory configuration.

## How to change JVM heap settings

For Linux and Mac: [conf/application.ini](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/conf/application.ini) 

For Windows: [O_MI_NODE_config.txt](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/O_MI_NODE_config.txt)

## How to setup authentication and authorization?

See our [Authentication and authorization guide](https://github.com/AaltoAsia/O-MI/blob/development/docs/AuthenticationAuthorization.md)

## What are DB file locations? Where are the location settings?
1. There is value history data in a h2 sql database in ./logs/sensorDB.h2.mv.db. It contains values and related data that is used only for read request parameters: begin, end, oldest, newest. The path is set in this setting:
`slick-config.db.url = "jdbc:h2:file:./logs/sensorDB.h2;LOCK_TIMEOUT=10000"`
The oldest values in the db are trimmed periodically every `trim-interval` to have `num-latest-values-stored` per InfoItem

2. There are journals and snapshots of in-memory database in ./logs/journal and ./logs/snapshots. These contains the actual O-DF tree and all related metadata, subscriptions and the latest value of each InfoItem. The paths are set in settings:
akka.persistence.journal.leveldb.dir = "./logs/journal"
akka.persistence.snapshot-store.local.dir = "./logs/snapshots"

## How to deploy (linux and docker)?
TODO

## MacOS compilation problems
Check that you have right Java JDK version.

## How to enable write request (in docker version)?
If you don't use any integrated O-MI authorization system: use sed in docker file to change the configuration `omi-service.allowRequestTypesForAll = ["cancel", "read", "call", "write", "response", "delete"]`

