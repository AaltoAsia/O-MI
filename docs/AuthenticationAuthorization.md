Authentication and Authorization guide
======================================

Permanently allowed request types
---------------------------------

This is used to set the default permissions for all incoming requests regardless of any user information.

Currently it is only possible to allow by O-MI request type with this setting.

This setting is intended for testing the node or, for example, allowing "read" and "cancel"
requests when all data in the db is open data.

1. Set `allowRequestTypesForAll` in [configuration](https://github.com/AaltoAsia/O-MI#configuration-location)

Ip Authorization
----------------

This is useful for allowing localhost connections to write. It allows fast and easy setup of simple O-MI wrapper scripts on the same server machine.

1. Set `input-whitelist-ips` in [configuration](https://github.com/AaltoAsia/O-MI#configuration-location)

Subnet Authorization
---------------------

This is aimed for quick low security solution or allowing a subnet of trusted computers to write.

1. Set `input-whitelist-subnets` in [configuration](https://github.com/AaltoAsia/O-MI#configuration-location)


O-MI Auth API v2
-----------------

This can be used to setup external authentication and authorization services (word *external* means a separate process that can run on the same or other computer). O-MI Node first contacts authentication service and then authorization service, after that it filters the request.

The Authentication and Authorization APIs are quite flexible and are controlled by [configuration](https://github.com/AaltoAsia/O-MI#configuration) options in object `omi-service.authAPI.v2`. Only fixed format is the last step, which is the response of Authorization service: It must have json object in the body that has two lists of paths, `"allow"` and `"deny"`. These lists are used to filter the incoming O-DF with following set operations: <O-DF> intersect <allow> difference <deny>. The filtered O-DF is used in the request instead of the original and the request processing will continue.

The input for authentication service can be passed by several configurable ways (option `omi-service.authAPI.v2.parameters.fromRequest`):
* omiEnvelope attribute, for example `<omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="0" token="eyJ0eXAiOiJKV1Q...">`, This is the recommended way to ensure functionality even when using other transport protocols.
* The `Authorization` http header
* Other http headers
* Uri query parameters

Continue reading below to know about already existing implementations of these APIs.

O-MI Authentication and Authorization reference implementations
---------------------------------------------------------------

* [Authentication module](https://github.com/AaltoAsia/O-MI-Authentication)
* [Authorization module](https://github.com/AaltoAsia/O-MI-Authorization)

These are examples on how to use Auth API v2 of O-MI Node. They might be secure enough for production use, but use with care. Either of them can be replaced by other software by adjusting the configuration approprietly or implementing a wrapper to fix any larger protocol differences.

### Local User DB, username and password Authentication with JWT session

Start with this to test how the modules work.

**Versions used:**
* O-MI Node: 0.12.1 unreleased 2018-06-21 9a3601d1c025d2a6ee6c0dfaa00eed825a45d942
* O-MI Authentication: unreleased 2018-06-21 d67ffe3fd4f3f8534dac3e508b7bdccb091912d3
* O-MI Authorization: unreleased 2018-06-21 47464faeb854d7d627da76ff11aa6c5c7a3491c1

**Instructions:**
1. Install [Authentication module](https://github.com/AaltoAsia/O-MI-Authentication)
    * ldap and nginx installations are optional
2. Install [Authorization module](https://github.com/AaltoAsia/O-MI-Authorization)
2. Configure O-MI Node
    1. Configure according to the readmes of the modules
    1. If testing with localhost: add option `omi-service.input-whitelist-ips=[]` to disable localhost authorization
    2. *Optional:* In O-MI Node `logback.xml` configuration file, add `<logger name="authorization" level="DEBUG"/>` inside configuration element for debugging
3. Start O-MI Node, Authentication module and Authorization module
4. open authentication module in browser http://localhost:8000/ and press signup
    1. Create a new user and remember the email that was used
    2. Log in with your account
    3. Open About page and copy your token string (carefully, don't copy white space)
5. Open O-MI Node webclient http://localhost:8080/html/webclient/index.html
    1. Create a write request
    2. Send and check that the result is `Unauthorized`
    3. Leave page open
6. Open shell
    1. Install httpie or use some other http client `sudo apt-get install httpie`
    2. Add your email address as username `http POST :8001/v1/add-user username=your@test.email`
    3. Add allow write rule to your user (automatically created group) `http POST :8001/v1/set-rules group=your@user.email_USERGROUP rules:='[{"path":"Objects","request":"wcd","allow":true}]'`
7. Go back to O-MI Node webclient and send again. You should see returnCode=200.
 

