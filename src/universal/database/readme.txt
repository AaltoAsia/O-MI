Warp10 binary should be placed here to path `./warp10` for warp10 release.
If there is no "warp10" directory in this directory, then warp10 is not used
as part of this O-MI Node software.

Warp10 licence related info can be found in LICENCE.txt and NOTICE.txt in this directory.

When warp10 directory is included here, it can be used in the release
package. To make a release:

* Release can be tested with `sbt stage` and result can be found on
`target/universal/stage/`

* Release packages can be made with `sbt release` and can be found on
`target/scala-2.11/`
