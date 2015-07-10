# import WebOmi, add submodule
requestsExt = (WebOmi) ->
  # Sub module for containing all request type templates 
  my = WebOmi.requests = {}

  my.xmls =
    readAll :
      """
      <?xml version="1.0"?>
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd"
          version="1.0" ttl="0">
        <omi:read msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <Objects></Objects>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope> 
      """
    template :
      """
      <?xml version="1.0"?>
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd"
          version="1.0" ttl="0">
        <omi:read msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope> 

      """

  my.defaults = {}
  my.defaults.empty =
    request  : null  # Maybe string
    ttl      : 0     # double
    callback : null  # Maybe string
    requestID: null  # Maybe int
    odf      : null  # Maybe xml
    interval : null  # Maybe number
    newest   : null  # Maybe int
    oldest   : null  # Maybe int
    begin    : null  # Maybe Date
    end      : null  # Maybe Date

  my.defaults.readAll = $.extend {}, my.defaults.empty,
    odf : '<Objects></Objects>'

  # private
  lastParameters = my.defaults



  # @param fastforward: Boolean Whether to also send the request and update odfTree also
  my.readAll = (fastForward) ->
    WebOmi.formLogic.setRequest my.xmls.readAll
    if fastForward
      WebOmi.formLogic.send(WebOmi.formLogic.buildOdfTreeStr)


  my.addPathToOdf = (path) ->
    o = WebOmi.omi
    reqCM = WebOmi.consts.requestCodeMirror
    xmltree = o.parseXml(reqCM.getValue)
    # TODO: user edit conflict check
    
    o.evaluateXPath(xmlTree, '//omi:msg')

    

  my.read = () ->

          

  WebOmi # export module

# extend WebOmi
window.WebOmi = requestsExt(window.WebOmi || {})
