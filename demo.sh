cd Agents/
(while true; do 
    sensors | awk '/Physical/ {print $4}'
    sleep 2
done
) | sbt "run localhost 8181 RoomSensors1/Temperature"
cd ..

