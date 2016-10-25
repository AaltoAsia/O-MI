Simple Java program to configure paths and tokens when running the program. Compiled version can be found in bin directory. Takes path to warp10 directory as parameter and then fixes all config paths required to start Warp10.

build jar (commons compress and commons io dependency for handling uncompressing and downloading of database files):

javac -cp commons-compress-1.12.jar:commons-io-2.5.jar *.java
jar cvf fixpaths.jar ReplacePath.class DownloadBinaries.class

