
O-MI Security Service Protocol
==============================

Intro
-----

This protocol transfers the following information:

1. the original request
2. authenticated user info (e.g. email address or username)

It is sent to a service that can decide whether the request is authorized or not for the user described by the user info.

Response can also include a new O-MI request that is intended to be used when user has partial permissions and the request is wanted to automatically get the result of the partial permissions (e.g. get all objects).


Request
-------

* Request is a O-MI write request (because it contains values)
* Request is xml escaped string and inserted as the value of InfoItem `Objects/AuthorizationRequest/OriginalRequest`:
* User info is in InfoItem `Objects/AuthorizationRequest/UserInfo`

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


Response
--------

* `returnCode="200"`: Authorized
* `returnCode="401"`: Fully unauthorized
* `returnCode="206"`: Partially authorized, modified request given in the value of `Objects/AuthorizationResponse/AuthorizedPartialRequest` as escaped xml string

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
