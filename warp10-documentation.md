
Data model, O-MI conversion
===========================

Warp10 takes location data for each value but O-MI doesn't have that kind of field for each value, instead we use the following format:

* Location is stored in the parent `<Object>` under InfoItem `location`
* Each location value that is related to a warp10 sensor value should have __the same timestamp__
  * (for single values you can have a single location and value without timestamp and the server will fill it in automatically)
* Locations are converted from ISO 6709 standard to warp10 (warp10 geolocation format and others migth be supported in the future)

Location format (ISO 6709)
--------------------------

* https://en.wikipedia.org/wiki/ISO_6709
* Supported coordinate system is [WGS 84](https://en.wikipedia.org/wiki/World_Geodetic_System#WGS84)
* We only support the decimal representation: `±00.0±000.0`
* height/depth:
  - By the standard: "When height or depth is present, CRS identifier must follow." So it becomes `±00.0±000.0±0CRSWGS_84`
  - Unit is millimeters above/below sea level.
* Recommended place for MetaData about the location format is in the `<MetaData>` with InfoItem named `type`. It is also added automatically by this server.

Example Object
---------------

```xml
<Objects>
  <Object>
    <id>SensorBox123</id>
    <InfoItem name="location">
     <MetaData>
      <InfoItem name="type">ISO 6709</InfoItem>
     </MetaData>
     <value unixTime="1382441207" dateTime="2013-10-22T14:26:47.762+03:00">+51.50198796764016+000.005952995270490646+12345CRSWGS_84/</value>
     <value unixTime="1382441237" dateTime="2013-10-22T14:27:17.727+03:00">+51.50198796764016+000.005952995270490646+42313CRSWGS_84/</value>
     <value unixTime="1382441267" dateTime="2013-10-22T14:27:47.504+03:00">+51.50198796764016+000.005952995270490646+12423CRSWGS_84/</value>
    </InfoItem>
    <InfoItem name="humidity">
      <value unixTime="1382441207" dateTime="2013-10-22T14:26:47.762+03:00" type="xs:double">79.16</value>
      <value unixTime="1382441237" dateTime="2013-10-22T14:27:17.727+03:00" type="xs:double">75.87</value>
      <value unixTime="1382441267" dateTime="2013-10-22T14:27:47.504+03:00" type="xs:double">73.55</value>
    </InfoItem>
  </Object>
</Objects>
```

Warp10 details
==============

* O-DF InfoItems are saved to Warp10 with *class name* that is built from the path of the O-DF hierarchy.
  - They are paths but seperator is `.` (dot) instead of `/` because warp10 write syntax uses it.
  - So the above example would become `Objects.model.humidity`
* Writes include the location data if it is sent in the same request as MetaData (as described above)
* Reads include the location MetaData automatically
* Tokens should be pasted to `application.conf` configuration file of O-MI Node

Change log
==========

2016-08-03
----------

* Changed location data to be in a location InfoItem of the parent Object instead of MetaData 
* InfoItem for locations renamed from `locations` to `location`
* Recommended coordinate syntax metadata place is now in the `<MetaData>` of `location` InfoItem instead of `type` attribute of values
