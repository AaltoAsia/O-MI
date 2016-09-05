
Releasing
=========

**Before** releasing:

1. Check that configuration is right (see below)
  * `O-MI Node/src/main/resources/application.conf`
  * remove excess logging (in `O-MI Node/src/main/resources/logback.xml`)
2. Change version number in `/build.sbt` only
3. Check that tests succeed: `sbt test`

**Releasing:**

4. Release: `sbt release`
5. Results can be found in `/target/universal`
6. Debian package
  * requires dpkg binary installed
  * can be made with `sbt debian:packageBin`
  * result goes to `/target/universal`

Branches
========

**Purposes of branches:**

1. `master` has the sources of the latest release
2. `development` has ongoing development or testing, it should compile but some tests might be failing
3. feature/bugfix/refactor branches has in-progress development of a single task
4. `warp10integration` has the sources of the latest relaese with warp10 as history database.
5. `otaniemi3d` has the version that is running on our test and demo server.


Configuration differences
-------------------------

1. `development`
  * Agents enabled: *anything needed for testing agents*
2. `master` - *Main releasing branch*
  * Agents enabled: SmartHouse
  * External security module disabled
3. `warp10integration`
  * Agents enabled: ExampleRoom(JavaRoomAgent)
  * External security module disabled
4. `otaniemi3d`
  * Agents enabled: SmartHouse, K1Agent, *Manik's agent?*
  * External security module enabled: "registerAuthAPI(securityModule)"
  * Merge application.conf manually in the server
  
Branch upstreams
-----------------

Branches are merged in the following way:
* `feature/bugfix/refactor branches -> development -> master`
* `master -> warp10integration`
* `master -> otaniemi3d`

