Feature highlights
==================

* A developer webapp for building and sending O-MI/O-DF messages.
* Agent system for writing extensions in JVM compatible language.
* Builtin authentication and authorization APIs

Other features
--------------

* Admin CLI interface

O-MI and O-DF features
======================

Missing features and differences to the standard are collected to [this](https://docs.google.com/spreadsheets/d/1duj-cX7dL9QR0igVMLNq9cBytSA196Ogiby-MWMetGw/edit?pref=2&pli=1#gid=1927687927) (work in progress) document.

Supported communication protocols:

* HTTP (`http://`)
* HTTPS (`https://`)
* [WebSocket](#o-mi-extensions) (`ws://`)
* WebSocket Secure (`wss://`)

O-MI Extensions
===============

This server supports the following extensions to O-MI v1.0:

1. Websockets
  * Client can initiate websocket connection to O-MI Node server by connecting to the same root path as POST requests with http connection upgrade request. (url conventions: `ws://` or secure `wss://`)
  * During a websocket connection the server accepts requests as in normal http communication. Immediate responses are sent in the same order as the corresponding requests were received.
  * During a websocket connection callback can be set to `"0"` in an O-MI message to tell O-MI Node to use current websocket connection as the callback target.
  * Keep in mind that depending on client and server configuration, a websocket connection will timeout if there is long period of inactivity. (Default is usually 1 minute). No callbacks can be sent after a timeout occurs (as websockets are always initiated by a client).
    - If your ws client library doesn't support the native ws ping, sending an empty websocket message is allowed for the purpose of keeping the connection alive and server will not send any special response for that.
2. Delete operation
  * Delete operation is currently an extension until it is released in O-MI standard version 2.0


Planned
=======

* Json support

Details of specific features
============================

* O-DF extra (unknown/proprietary) xml attributes as allowed by the O-DF schema:
   - They can be added to the stored O-DF with a normal O-MI write request
   - They will be left untouched when writing without any of the attributes
   - They will be removed only if rewritten with an empty string as value
