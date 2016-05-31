Strategies
==========

Piggybacking requires at least:

1. Target user (or other way to identify the target, IP can change but it can be a dynamic DNS address).
2. Request or some way to tell what to send to the target.
3. Some way to return the result from piggybacking.

Strategies:

A. Per request
  - The whole request is forwarded to the piggybacking target

B. Per path
  - Some O-DF paths are marked to be piggybacked to some piggybacking target.
  - How marking happens? What interface is used to change the configuration?
  - Is it needed to be changed at run time "on-demand"?

Results can be sent as the result to the original piggybacking request such that the user needs to leave the connection open (http keepalive might work). This causes problems only in the case of mobile original sender where connection might be lost momentarily. Result might not be possible to send back but it we could piggyback the results also if user is known.


Escaped O-MI message inside of an InfoItem value
================================================

Strategy A.

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


Path/requestID mapping to piggyback targets
===========================================

Strategy B.

Could use some of the code from responsible internal agents:

Responsible agent piggybacking: Send Write
------------------------------------------

1. Write to paths that are marked as piggybackable in the O-MI node configuration.
2. Behind the scenes the configuration is actually an Intrnal Agent that is responsible for the piggybackable InfoItems.
3. Internal agent manager splits the request for the right agents.
4. The internal agent then calls a new API command that adds the request to piggybacking queue (Map).
5. The piggybacking is done when the target contacts the O-MI server with any O-MI request and the request gets some result.
6. The original write request finishes when the O-MI node receives the result for the piggybacked request.

Good
----

1. Clean interface and straightforward usage. Just send O-MI messages as normal the responses might just have more latency.
2. Other requests can use the same path-target mapping to figure out the need of piggybacking and Subscription related messages could save the piggy-backed requestIDs to be mapped also.

Problems
--------

1. How the configuration happens? Does it need to be done "on-demand"?



New argument for piggybacking
=============================

Good
----

1. Clear indication where the request is wanted to be sent. Works like a relay (like `nodeList` but takes user instead).
2. Request can be independent of the O-DF tree of our O-MI node if needed.

Bad
---

Depends on the use cases.
1. User needs to know the target user identifier (vs. strategy B: per path piggybacking, where targets can be saved to the O-MI Node)
2. Requires a new (custom) attribute to O-MI.




