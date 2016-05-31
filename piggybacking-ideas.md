Strategies
==========

Piggybacking requires at least:
1. Target user (or other way to identify the target, IP can change but it can be a dynamic DNS address)
2. Request or some way to tell which requests to send to the target

A. Per request
  - Some new O-MI attribute?
B. Per path
  - Some O-DF paths are marked to be piggybacked to some user
  - How marking happens?
  - What interface is used to change the configuration? Is it needed "at runtime"


Escaped O-MI message inside of an InfoItem value
================================================

Send
----


```
<?xml version="1.0"?>
<omi:omiEnvelope xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" version="1.0" ttl="0">
  <write xmlns="omi.xsd" msgformat="odf">
    <msg>
      <Objects xmlns="odf.xsd">
        <Object>
          <id>Piggybagging</id>
          <InfoItem name="Queue">
<value>&lt;?xml version=&quot;1.0&quot;?&gt;
&lt;omi:omiEnvelope xmlns:xs=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; xmlns:omi=&quot;omi.xsd&quot; version=&quot;1.0&quot; ttl=&quot;0&quot;&gt;
  &lt;write xmlns=&quot;omi.xsd&quot; msgformat=&quot;odf&quot;&gt;
    &lt;omi:msg&gt;
      &lt;Objects xmlns=&quot;odf.xsd&quot;&gt;
        &lt;Object&gt;
          &lt;id&gt;MyObject&lt;/id&gt;
          &lt;InfoItem name=&quot;MyInfoItem&quot;&gt;
            &lt;value type=&quot;xs:string&quot;&gt;0&lt;/value&gt;
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
    </msg>
  </write>
</omi:omiEnvelope>
```

Receive
-------

* The same or some other InfoItem, like Result, gets written the received result.

Good
----

1. Result can be subscribed to.
2. All requests work the same way.

Problems
--------

1. Awkward interface with all the escaping
2. Requires some naming convention for InfoItems


Responsible agent piggybacking
==============================

Send Write
----------

1. Write to paths that are marked as piggybackable in the O-MI node configuration.
2. Behind the scenes the configuration is actually an Intrnal Agent that is responsible for the piggybackable InfoItems.
3. Internal agent manager splits the request for the right agents.
4. The internal agent then calls a new API command that adds the request to piggybacking queue (Map).
5. The piggybacking is done when the target contacts the O-MI server with any O-MI request and the request gets some result.
6. The original write request finishes when the O-MI node receives the result for the piggybacked request.

Good
----

1. Clean interface and straightforward configuration.

Problems
--------

1. No support for other requests than write at the moment.


New argument for piggybacking
-----------------------------





