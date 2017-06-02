Database configuration
======================

We use Slick library for database access and the default database in the reference implementation uses H2 database. It is possible to use different database driver for slick by editing application.conf file.

```
#H2 database config below
dbconf {
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

All the drivers supported by slick are not tested with the reference implementation and might cause problems if some of the used database features are not supported. Application.conf contains example configuration for postgres database.

Using custom database implementation
------------------------------------
Custom database implementation needs to implement the DB trait
API for the DB trait:
[java](https://otaniemi3d.cs.hut.fi/omi/node/html/api/java/database/DB.html "Scaladoc")
[scala](https://otaniemi3d.cs.hut.fi/omi/node/html/api/index.html#database.DB "Javadoc")

DB trait has three methods that need to be implemented: getNBetween, writeMany and remove.
When writing an implementation with Java, converting Scala collections to Java collections can be done using helper methods in collections.JavaConverters.

Used database is read from boot.scala from 'dbConnection' variable. You will need to edit the value to your implementation and compile the project.

Currently there are two different implementations for the DB trait:
[default](https://github.com/AaltoAsia/O-MI/blob/master/O-MI%20Node/src/main/scala/database/DBInterface.scala#L207 "Default database implemntation")
[warp10](https://github.com/AaltoAsia/O-MI/blob/warp10integration/O-MI%20Node/src/main/scala/database/Warp10Wrapper.scala#L237 "Wrapper for warp10 database")



