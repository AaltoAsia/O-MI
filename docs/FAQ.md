# Fequently Asked Questions

## How to modify or delete existing value(s)
## Is there only 50 values in DB?
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

## What is Callback response history in WebClient?
When webclient connects to O-MI Node using WebSockets, it can use `0` as
callback address  allowing O-MI Node to respond using current WebSocket
connection. This lets browser to receive responses from all request with
a callback address. 
These responses are shown when Callback Response History next to Response label  is
clicked. The number next to it tells how many new responses have been received.

## Which requests are optimized?
For all InfoItems O-MI Node has cached the latest value they have. Thus, optimising
all read request that only access them. Cache also has all metadata(MetaDatas,descriptions,attributes,etc.).

## Request are taking too long or timeout requently. What can be done?
### Increase timeouts for large requests
### Disable history database
If you do not need large amounts of data stored or are willing to store data
somewhere else, then you disable database and only have latest values stored in
cache. This is done by setting `omi-service.database` to `none`.

## How to publish other file/query format endpoints?
## How to run on ARM architecture

LevelDB used in journal does not have ARM compatible version at the moment, 
so to run on devices with ARM architecture the database needs to be disabled from the [reference.conf](https://github.com/AaltoAsia/O-MI/blob/development/O-MI-Node/src/main/resources/reference.conf#L432-L437) file. Comment the lines with LevelDB configurations and uncomment the in-memory configuration.

## How to change JVM heap settings

For Linux and Mac: [conf/application.ini](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/conf/application.ini) 

For Windows: [O_MI_NODE_config.txt](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/O_MI_NODE_config.txt)

## authz configuration for Kong authentication
## DB file locations, location settings, roles, why logs dir? move db from logs dir without breaking linux packages
## what is development status? (warnings)
## How to deploy (linux and docker)
## macOS compilation problems (newer java version problems?)
## related software components and link to connecting manual (authorization, influxdb, warp10)
## code of coduct (CONTRIBUTING.md)
## How to enable write request to docker version?

