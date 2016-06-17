
Some options for the protocol
=======================================

We require

1. the original request including
  1. O-DF
  2. method type (read/write)
2. userinfo



A) Request as a value
---------------------

* O-MI parser overhead: very low, just xml escaping

### Request

```xml
<?xml version="1.0"?>
<omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" version="1.0" ttl="0">
  <write xmlns="omi.xsd" msgformat="odf">
    <omi:msg>
      <Objects xmlns="odf.xsd">
        <Object>
          <id>AuthorizationRequest</id>
          <InfoItem name="OriginalRequest">
            <value type="omi.xsd">

&lt;?xml version=&quot;1.0&quot;?&gt;
&lt;omi:omiEnvelope xmlns:xs=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; xmlns:omi=&quot;omi.xsd&quot; version=&quot;1.0&quot; ttl=&quot;0&quot;&gt;
  &lt;write xmlns=&quot;omi.xsd&quot; msgformat=&quot;odf&quot;&gt;
    &lt;omi:msg&gt;
      &lt;Objects xmlns=&quot;odf.xsd&quot;&gt;
        &lt;Object&gt;
          &lt;id&gt;SmartHouse&lt;/id&gt;
          &lt;InfoItem name=&quot;FrontDoor&quot;&gt;
            &lt;value&gt;VALUE_PLACEHOLDER&lt;/value&gt;
          &lt;/InfoItem&gt;
          &lt;InfoItem name=&quot;BackDoor&quot;&gt;
            &lt;value&gt;VALUE_PLACEHOLDER&lt;/value&gt;
          &lt;/InfoItem&gt;
        &lt;/Object&gt;
      &lt;/Objects&gt;
    &lt;/omi:msg&gt;
  &lt;/write&gt;
&lt;/omi:omiEnvelope&gt;

            </value>
          </InfoItem>
          <InfoItem name="UserInfo">
            <value type="xs:string">user@email.com</value>
          </InfoItem>
        </Object>
      </Objects>
    </omi:msg>
  </write>
</omi:omiEnvelope>
```

B) Request as O-DF and type
--------------------------

* O-MI parser overhead: medium/low, only header needs parsing


```xml
<?xml version="1.0"?>
<omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" version="1.0" ttl="0">
  <write xmlns="omi.xsd" msgformat="odf">
    <omi:msg>
      <Objects xmlns="odf.xsd">
        <Object>
          <id>AuthorizationRequest</id>
          <InfoItem name="PermissionForRequestType">
            <value type="xs:string">write</value>
          </InfoItem>
          <Object>
            <id>PermissionForODF</id>
            <Object>
              <id>SmartHouse</id>
              <InfoItem name="FrontDoor">
                <value>VALUE_PLACEHOLDER</value>
              </InfoItem>
              <InfoItem name="BackDoor">
                <value>VALUE_PLACEHOLDER</value>
              </InfoItem>
            </Object>
          </Object>
          <InfoItem name="UserInfo">
            <value type="xs:string">user@email.com</value>
          </InfoItem>
        </Object>
      </Objects>
    </omi:msg>
  </write>
</omi:omiEnvelope>
```



C) Request as paths and type
---------------------------

* O-MI parser overhead: high, whole request needs parsing


```xml
<?xml version="1.0"?>
<omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" version="1.0" ttl="0">
  <write xmlns="omi.xsd" msgformat="odf">
    <omi:msg>
      <Objects xmlns="odf.xsd">
        <Object>
          <id>AuthorizationRequest</id>
          <InfoItem name="PermissionForRequestType">
            <value type="xs:string">write</value>
          </InfoItem>
          <InfoItem name="PermissionForPaths">
            <value type="xs:string">Objects/SmartHouse/FrontDoor</value>
            <value type="xs:string">Objects/SmartHouse/BackDoor</value>
          </InfoItem>
          <InfoItem name="UserInfo">
            <value type="xs:string">user@email.com</value>
          </InfoItem>
        </Object>
      </Objects>
    </omi:msg>
  </write>
</omi:omiEnvelope>
```

Response
--------

### Authorized

```xml
<omi:omiEnvelope ttl="1.0" version="1.0" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <omi:response>
    <omi:result>
      <omi:return returnCode="200"/>
    </omi:result>
  </omi:response>
</omi:omiEnvelope>
```

### Unauthorized

```xml
<omi:omiEnvelope ttl="1.0" version="1.0" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <omi:response>
    <omi:result>
      <omi:return returnCode="401"/>
    </omi:result>
  </omi:response>
</omi:omiEnvelope>
```

### Partial

```xml
<omi:omiEnvelope ttl="1.0" version="1.0" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <omi:response>
    <omi:result msgformat="odf">
      <omi:return returnCode="206">
      </omi:return>
      <omi:msg>
        <Objects>
          <Object>
            <id>AuthorizationResponse</id>
            <InfoItem name="AuthorizedPartialRequest">
              <value type="omi.xsd">
&lt;?xml version=&quot;1.0&quot;?&gt;
&lt;omi:omiEnvelope xmlns:xs=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; xmlns:omi=&quot;omi.xsd&quot; version=&quot;1.0&quot; ttl=&quot;0&quot;&gt;
  &lt;write xmlns=&quot;omi.xsd&quot; msgformat=&quot;odf&quot;&gt;
    &lt;omi:msg&gt;
      &lt;Objects xmlns=&quot;odf.xsd&quot;&gt;
        &lt;Object&gt;
          &lt;id&gt;SmartHouse&lt;/id&gt;
          &lt;InfoItem name=&quot;FrontDoor&quot;&gt;
            &lt;value&gt;VALUE_PLACEHOLDER&lt;/value&gt;
          &lt;/InfoItem&gt;
        &lt;/Object&gt;
      &lt;/Objects&gt;
    &lt;/omi:msg&gt;
  &lt;/write&gt;
&lt;/omi:omiEnvelope&gt;
              </value>
            </InfoItem>
          </Object>
        </Objects>
      </omi:msg>
    </omi:result>
  </omi:response>
</omi:omiEnvelope>
```
