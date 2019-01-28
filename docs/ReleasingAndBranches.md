
Releasing
=========

**Before** releasing:
-------------------

1. Check that configuration is right (see below)
    * `O-MI-Node/src/main/resources/reference.conf`
    * remove excess logging in code (and in `O-MI-Node/src/main/resources/logback.xml`)
2. Change version number in `/build.sbt` only
3. Merge master->development: `git pull origin master` (contains usually readme/docs changes)
4. Check that tests succeed: `sbt test`
5. Commit
6. Merge development->master `git checkout master && git merge development`

**Automatic release packages:**
----------------------

4. Create an annotated git tag on latest commit: `git tag -a $(cat O-MI-Node/html/VERSION) HEAD`
5. Push the tag to remote repository: `git push origin $(cat O-MI-Node/html/VERSION)`
6. Travis will now create the release binaries and upload them to docker and GitHub.
7. On Github releases page: check that the release draft tag is right, write release notes and publish.

**Manual release packages:**
----------------------------

1. Release: `sbt release`
2. Results can be found in `/target` and `/target/universal`

3. Doing Debian and RPM package separately (done by the `release` command also)
    * Debian requires dpkg binary installed
    * can be made with `sbt debian:packageBin` and `sbt rpm:packageBin`
    * result goes to `/target/`

Branches
========

**Purposes of branches:**
---------------------

1. `master` has the sources of the latest release
2. `development` has ongoing development or testing, it should compile but some tests might be failing
3. feature_/bugfix_/refactor_ branches has in-progress development of a single task
4. `warp10integration` has the sources of the latest relaese with warp10 as history database **with automatic warp10 installation and start**.


Configuration
-------------------------

1. `reference.conf`
    * Sane default values for settings
    * No agents should be enabled
    * No unnecessary external settings (maybe used in some manual test)
2. `development`
    * Create your own application.conf in `/O-MI-Node/src/main/resources/application.conf`, it is in gitignore list
    * Create your own logback configuration in `/O-MI-Node/src/main/resources/logback-test.xml`, it is in gitignore list

  
Branch upstreams and merging
-----------------

Branches are merged in the following way:
* `feature/bugfix/refactor branches -> development -> master`
* `master -> warp10integration`
* `master -> otaniemi3d`

