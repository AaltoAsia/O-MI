#!/bin/bash
# Usage
# * To start: ./start.sh
# * To stop:  ./start.sh stop

pidfile=./o-mi-node.pid

if [[ $1 == "-h" || $1 == "--help" ]]; then
    echo "Usage"
    echo "* To start: ./start.sh"
    echo "* To stop:  ./start.sh stop"
fi

# pid check
if [[ -f $pidfile ]]; then
    oldpid=`cat $pidfile`
    if ps ax | awk '{print $1}' | grep $oldpid > /dev/null; then
        echo Stopping pid $oldpid...
        kill $oldpid
    fi
    rm $pidfile
fi

if [[ $1 == "stop" ]]; then
    exit 0
fi


echo Starting...
echo java -jar -Dconfig.file=application.conf o-mi-node-*.jar >> "log-startedon-`date -I`" &
javapid=$!
echo Started pid $javapid.

# save pid
echo $javapid > $pidfile


