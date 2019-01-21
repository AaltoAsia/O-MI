# Fequently Asked Questions

## How to modify or delete existing value(s)
## only 50 values in DB?
## How to use a different DB
## How to publish other file/query format endpoints?
## Which requests are optimized?
## Java responsible agent
## Callback response history in WebClient
## How to run on ARM architecture

LevelDB used in journal does not have ARM compatible version at the moment, 
so to run on devices with ARM architecture the database needs to be disabled from the [reference.conf](https://github.com/AaltoAsia/O-MI/blob/development/O-MI-Node/src/main/resources/reference.conf#L432-L437) file. Comment the lines with LevelDB configurations and uncomment the in-memory configuration.

## How to change JVM heap settings

For Linux and Mac: [conf/application.ini](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/conf/application.ini) 

For Windows: [O_MI_NODE_config.txt](https://github.com/AaltoAsia/O-MI/blob/master/src/universal/O_MI_NODE_config.txt)

## authz configuration for Kong authentication
## DB file locations, location settings, roles, why logs dir? move db from logs dir without breaking linux packages
## performance improvements (by disabling history database)
## what is development status? (warnings)
## How to deploy (linux and docker)
## macOS compilation problems (newer java version problems?)
## related software components and link to connecting manual (authorization, influxdb, warp10)
## code of coduct (CONTRIBUTING.md)
## How to increase timeouts for large requests?
## How to enable write request to docker version?
