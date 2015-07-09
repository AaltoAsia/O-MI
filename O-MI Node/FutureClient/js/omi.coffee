# import WebOmi, add submodule
omiExt = (WebOmi) ->
  # Sub module for handling omi xml
  my = WebOmi.omi = {}

  my.parseXmlResponse = (responseString) ->
    window.xmlTree = new DOMParser().parseFromString responseString, 'text/xml'

  nsResolver = (name) ->
    ns =
      omi : "omi.xsd"
      odf : "odf.xsd"
    ns[name] || ns.odf

  my.evaluateXPath = (elem, xpath) ->
    xpe = elem.ownerDocument || elem
    iter = xpe.evaluate(xpath, elem, nsResolver, 0, null)

    res while res = iter.iterateNext()

  WebOmi # export module

# extend WebOmi
window.WebOmi = omiExt(window.WebOmi || {})
