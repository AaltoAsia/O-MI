cd Agents/
(while true; do 
    free | awk '/Mem:/ {print $7}'
    sleep 1
done
) | sbt "run localhost 8181 Server/RAM/Available" &

(latestval="0"
while true; do 
    newval=`cat /proc/loadavg | awk '{print $3}'`
    if [[ $newval != $lastval ]]; then
        echo $newval
    else
        sleep 1
    fi
    lastval=$newval
done
) | sbt "run localhost 8181 Server/CPU/Usage" 

#(while true; do 
#    cat /proc/uptime | awk '{print $1}'
#    sleep 2
#done
#) | sbt "run localhost 8181 Server/Uptime" &
cd ..

