Simple Build Tool cheat sheet
-----------------------------

Native SBT commands

- `sbt compile`: just compile the project
- `sbt clean`: remove compiled and temporary files
- `sbt run`: run the project; We don't use this much, so sometimes it's broken and we don't even notice. We use `re-start` from Revolver which allows us to recompile and restart the node without exiting sbt, because restarting sbt takes unnecessary extra time.
- `sbt doc`: compile api documentation
- `sbt test`: run all tests

Extra commands from plugins and other

- We use sbt-revolver: run `sbt` and then write
    - `reStart`: compile&run the project in background
    - `reStop`: close the background process
- We use sbt-native-packager:
    - `sbt stage`: creates file structure, used in packaged version, to the `./target/universal/stage/` directory
    - `sbt debian:packageBin`: create release debian package (requires `dpkg` program installed)
    - See native packager docs for configuring other packages. Our sbt configuration is in ./build.sbt.
- We use sbt-scoverage:
    - `sbt clean coverage test coverageReport`: calculate test coverage and generate reports in `O-MI-Node/target/scala-2.11/scoverage-report/`   
- We also have some extra commands for convenience:
    - `sbt systemTest`: run only system tests (the used requests and responses can be found in `ImplementationDetails.html`)
    - `sbt release`: create release tar and zip packages
    
_extra info:_

- automatically run any of above commands when there is a file change by adding `~` in front, like `sbt ~re-start`
- all commands above compiles the needed files that are not yet compiled
- run many commands in sequence faster if you open a single sbt command line with `sbt`

