Database configuration
======================

We use Slick library for SQL database access. The default database in the reference implementation uses H2 database.

SQL databases
-------------

It is possible to use different database driver for slick just by editing application.conf file and restarting the o-mi-node.

```
# The default H2 db configuration
slick-config {
  driver = "slick.driver.H2Driver$"
  db {
    url = "jdbc:h2:file:./logs/sensorDB.h2;LOCK_TIMEOUT=10000"
    driver = org.h2.Driver
    connectionPool = disabled
    keepAliveConnection = true
    connectionTimeout = 15s
  }
}
```

Supported SQL databeses are listed on the [Slick documentation](http://slick.lightbend.com/doc/3.2.1/supported-databases.html).

All the drivers supported by slick are not tested with the reference implementation and might cause problems if some of the used database features are not supported. application.conf contains example configuration for postgres database.

Other supported databases
-------------------------

We have also these DBs available:
* InfluxDB
* Warp10

They can be activated by the `database` option under `omi-service` in the application.conf file:
```
# Determines which database implementation is used: Slick, InfluxDB or Warp10
database = "slick"
```

After that you can tweak settings under the respective configuration object.

For Warp10 there is a release version that configures warp10 automatically.

Using custom database implementation
------------------------------------
Custom database implementation needs to implement the DB trait
API for the DB trait:
[java](https://otaniemi3d.cs.hut.fi/omi/node/html/api/java/database/DB.html "Scaladoc")
[scala](https://otaniemi3d.cs.hut.fi/omi/node/html/api/index.html#database.DB "Javadoc")

DB trait has three methods that need to be implemented: getNBetween, writeMany and remove.
When writing an implementation with Java, converting Scala collections to Java collections can be done using helper methods in package `scala.collections.JavaConverters`.

Used database is read from boot.scala from `dbConnection` variable. You will need to edit the value to your implementation and compile the project.

Currently there are three different implementations for the DB trait:

- [default](https://github.com/AaltoAsia/O-MI/blob/master/O-MI-Node/src/main/scala/database/DBInterface.scala#L207 "Default database implemntation")
- [warp10](https://github.com/AaltoAsia/O-MI/blob/warp10integration/O-MI-Node/src/main/scala/database/Warp10Wrapper.scala#L237 "Wrapper for warp10 database")
- [influxDB](https://github.com/AaltoAsia/O-MI/tree/master/O-MI-Node/src/main/scala/database/influxDB)



