cd Agents/
(while true; do 
    free -h | awk '/Mem:/ {print $3}'
    sleep 2
done
) | sbt "run localhost 8181 Server/Mem/Used" &

(while true; do 
    cat /proc/loadavg | awk '{print $1}'
    sleep 2
done
) | sbt "run localhost 8181 Server/CPU/Usage" &

(while true; do 
    cat /proc/uptime | awk '{print $1}'
    sleep 2
done
) | sbt "run localhost 8181 Server/Uptime" &
cd ..

