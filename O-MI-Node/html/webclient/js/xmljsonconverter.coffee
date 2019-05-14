xmlConverter = (WebOmi) ->


  # Sub module for handling omi xml
  my = WebOmi.jsonConverter = {}

  my.parseXml = (xmlString) ->
    try
      xmlTree =  new DOMParser().parseFromString(xmlString, 'application/xml')
    catch ex
      xmlTree = null
      console.log "ParseError while parsing input string"
    if xmlTree.firstElementChild.nodeName == "parsererror" or not xmlTree?
      console.log "PARSE ERROR:"
      console.log "in:", responseString
      console.log "out:", xmlTree
      xmlTree = null
  
    xmlTree
  
  my.ns =
    omi : "http://www.opengroup.org/xsd/omi/1.0/"
    odf : "http://www.opengroup.org/xsd/odf/1.0/"
  
  my.nsResolver = (name) ->
    my.ns[name] || my.ns.odf
 
  my.filterNullKeys = (obj) ->
    (Object.entries(obj).filter (item) -> item[1]?).reduce ( a,b ) ->
      Object.assign(a, {[b[0]] : b[1]})
    , {}


  my.evaluateXPath = (elem, xpath) ->
    xpe = elem.ownerDocument || elem
    iter = xpe.evaluate(xpath, elem, my.nsResolver, 0, null)
    res while res = iter.iterateNext()
  
  #my.omiEnvelope = (xml) ->
  #  xml.getElementsByTagName("omiEnvelope")[0]
  
  my.parseOmiEnvelope = (xml) ->
    if !xml?
      return null

    try
      version = my.exSingleAtt(my.evaluateXPath(xml,"/omi:omiEnvelope/@version"))
      ttl = my.exSingleAtt(my.evaluateXPath(xml,"/omi:omiEnvelope/@ttl"))
      if Number(ttl) != -1 and Number(ttl) < 0 or !ttl?
        ttl = null
        throw "Invalid Interval"
      else
        ttl = Number(ttl)
    catch ex
      version = null
      ttl = null

    if !version? || !ttl?
      return null

    try
      reads = my.evaluateXPath(xml, 'omi:omiEnvelope/omi:read')
      if reads.length != 1
        reads = null
      else
        reads = my.parseRead(reads[0])
    catch ex
      reads = null

    try
      writes = my.evaluateXPath(xml, 'omi:omiEnvelope/omi:write')
      if writes.length != 1
        writes = null
      else
        writes = my.parseWrite(writes[0])
    catch ex
      writes = null

    try
      responses = my.evaluateXPath(xml, 'omi:omiEnvelope/omi:response')
      if responses.length != 1
        responses = null
      else
        responses = my.parseResponse(responses[0])
    catch ex
      responses = null
  
    try
      cancels = my.evaluateXPath(xml, 'omi:omiEnvelope/omi:cancel')
      if cancels.length != 1
        cancels = null
      else
        cancels = my.parseCancel(cancels[0])
    catch ex
      cancels = null
  
    try
      calls = my.evaluateXPath(xml, 'omi:omiEnvelope/omi:call')
      if calls.length != 1
        calls = null
      else
        calls = my.parseCall(calls[0])
    catch ex
      calls = null
  
    try
      deletes = my.evaluateXPath(xml, 'omi:omiEnvelope/omi:delete')
      if deletes.length != 1
        deletes = null
      else
        deletes = my.parseDelete(deletes[0])
    catch ex
      deletes = null

    result =
      version:version
      ttl:ttl
      read:reads
      write:writes
      response:responses
      cancel:cancels
      call:calls
      delete:deletes

    omiEnvelope: my.filterNullKeys(result)

  my.exSingleAtt = (res) ->
    if !res?
      return null

    if (res.length != 1)
      if (res.length > 1)
        throw "invalid number of attributes"
      return null
    res[0].value

  my.exSingleNode = (res) ->
    if !res?
      return null

    try
      node = res.textContent
    catch ex
      null
    #if (res.length != 1)
    #  if (res.length > 1)
    #    throw "invalid number of Nodes"
    #  return null
    #else
    #  res.textContent

  my.parseRequestId = (xml) ->
    if !xml?
      return null

    try
      format = my.exSingleAtt(my.evaluateXPath(xml,"./@format"))
    catch ex
      format = null
    id = xml.textContent
    
    if format == null
      id
    else
      {
        id : id
        format : format
      }

  my.parseNodesType = (xml) ->
    if !xml?
      return null

    try
      type = my.exSingleAtt(my.evaluateXPath(xml,"./@type"))
    catch ex
      type = null
    node = my.evaluateXPath(xml,"./omi:node").map my.exSingleNode
    if !type?
      {
        node : node
      }
    else
      {
        type : type
        node : node
      }

  my.parseBaseType = (xml) ->
    if !xml?
      return null

    try
      callback = my.exSingleAtt(my.evaluateXPath(xml,"./@callback"))
    catch ex
      callback = null
    
    try
      msgformat = my.exSingleAtt(my.evaluateXPath(xml,"./@msgformat"))
    catch ex
      msgformat = null

    try
      targetType = my.exSingleAtt(my.evaluateXPath(xml,"./@targetType"))
    catch ex
      targetType = null

    try
      requestId = my.evaluateXPath(xml,"./omi:requestID").map my.parseRequestId
      if requestId.length == 1
        requestId = requestId[0]
      else if requestId.length == 0
        requestId = null
    catch ex
      requestId = null

    try
      nodeList = my.evaluateXPath(xml,"./omi:nodeList").map my.parseNodesType
      if nodeList.length == 1
        nodeList = nodeList[0]
      else if requestId.length == 0
        requestId = null
    catch ex
      nodeList = null

    try
      msg = my.parseMsg(my.evaluateXPath(xml, "./omi:msg")[0])
    catch ex
      msg = null

    result = {
      callback : callback
      msgformat : msgformat
      targetType : targetType
      nodeList : nodeList
      requestId : requestId
      msg : msg
    }

    my.filterNullKeys(result)


  my.parseRead = (xml) ->
    if !xml?
      return null

    baseType = my.parseBaseType(xml)

    try
      interval = my.exSingleAtt(my.evaluateXPath(xml,"./@interval"))
      if Number(interval) != -1 && Number(interval) != -2 && Number(interval) < 0 || interval == null
        interval = null
      else
        interval = Number interval
    catch ex
      interval = null

    try
      oldest = Number my.exSingleAtt(my.evaluateXPath(xml,"./@oldest"))
      if !Number.isInteger(oldest) || oldest < 1
        console.log "invalid oldest value: #{oldest}"
        oldest = null
    catch ex
      oldest = null

    try
      begin = my.exSingleAtt(my.evaluateXPath(xml,"./@begin"))
    catch ex
      begin = null

    try
      end = my.exSingleAtt(my.evaluateXPath(xml,"./@end"))
    catch ex
      end = null

    try
      newest = Number my.exSingleAtt(my.evaluateXPath(xml,"./@newest"))
      if !Number.isInteger(newest) || newest < 1
        newest = null
    catch ex
      newest = null

    try
      all = my.exSingleAtt(my.evaluateXPath(xml,"./@all"))
      if !all?
        all=null
    catch ex
      all = null

    try
      maxlevels = my.exSingleAtt(my.evaluateXPath(xml,"./@maxlevels"))
      if !Number.isInteger(Number maxlevels) || maxlevels < 1
        maxlevels = null
      else
        maxlevels = Number maxlevels
    catch ex
      maxlevels = null

    readObj =
      {
        interval: interval
        oldest: oldest
        begin:begin
        end:end
        newest:newest
        all:all
        maxlevels:maxlevels
      }
    
    readResult = my.filterNullKeys(readObj)

    result = {...baseType, ...readResult}


  my.parseWrite = (xml) ->
    if !xml?
      return null

    my.parseBaseType(xml)

  my.parseReturnType = (xml) ->
    if !xml?
      return null

    try
      returnCode = my.exSingleAtt(my.evaluateXPath(xml,"./@returnCode"))
      if returnCode.length < 2
        console.log "invalid returnCode: #{returnCode}"
        returnCode = null
    catch ex
      console.log "invalid returnCode: #{ex}"
      returnCode = null

    try
      description = my.exSingleAtt(my.evaluateXPath(xml,"./@description"))
    catch ex
      description = null

    if !returnCode?
      return null
    else
      if !description?
        return {returnCode:returnCode}
      else
        return {
          returnCode: returnCode
          description: description}

  my.parseRequestResultType = (xml) ->
    if !xml?
      return null


    try
      msgformat = my.exSingleAtt(my.evaluateXPath(xml,"./@msgformat"))
    catch ex
      console.log ex
      msgformat = null

    try
      targetType = my.exSingleAtt(my.evaluateXPath(xml,"./@targetType"))
    catch ex
      console.log ex
      targetType = null

    try
      returnT = my.parseReturnType(my.evaluateXPath(xml,"./omi:return")[0])
    catch ex
      console.log ex
      returnT = null

    try
      requestId = my.parseRequestId(my.evaluateXPath(xml,"./omi:requestId")[0])
      if returnT? and requestId?
        console.log "both return and requestID found"
        requestId = null
    catch ex
      console.log ex
      requestId = null

    try
      msg = my.parseMsg(my.evaluateXPath(xml,"./omi:msg")[0])
    catch ex
      console.log ex
      msg = null

    try
      nodeList = my.parseNodesType(my.evaluateXPath(xml, "./omi:nodeList")[0])
    catch ex
      console.log ex
      nodeList = null

    try
      omiEnvelope = my.parseOmiEnvelope(my.evaluateXPath(xml,"./omi:omiEnvelope")[0])
    catch ex
      console.log ex
      omiEnvelope = null

    result = {
      msgformat : msgformat
      targetType :  targetType
      return : returnT
      requestId :requestId
      msg : msg
      nodeList : nodeList
      omiEnvelope : omiEnvelope
    }

    my.filterNullKeys(result)

      



  my.parseResponse = (xml) ->
    if !xml?
      return null

    try
      result = my.headOrElseNull(my.evaluateXPath(xml,"./omi:result").map my.parseRequestResultType)
    catch ex
      result = null

    if result?
      {result: result}
    else
      null

      
  
  my.parseCancel = (xml) ->
    if !xml?
      return null

    try
      nodeList = my.parseNodesType(my.evaluateXPath(xml,"./omi:nodeList")[0])
    catch ex
      nodeList = null

    try
      requestId = my.evaluateXPath(xml,"./omi:requestID").map my.parseRequestId
      if requestId.length == 1
        requestId = requestId[0]
      else if requestId.length == 0
        requestId = null
    catch ex
      requestId = null

    result = {
      nodeList : nodeList
      requestId : requestId
    }

    my.filterNullKeys(result)

  
  my.parseCall = (xml) ->
    if !xml?
      return null

    my.parseBaseType(xml)
  
  my.parseDelete = (xml) ->
    if !xml?
      return null

    my.parseBaseType(xml)

  my.parseMsg = (xml) ->
    if !xml?
      return null

    #check msgformat?
    try
      objects = my.evaluateXPath(xml,"./odf:Objects")
      if objects.length != 1
        objects = null
      else
        objects = my.parseObjects(objects[0])
    catch ex
      console.log ex
      objects = null

    if !objects?
      null
    else
      {
        Objects: objects
      }
      
#--------ODF Part-------#

  my.headOrElseNull = (res) ->
    if res.length == 1
      res[0]
    else if res.length == 0
      null
    else
      res

  my.parseIoTIdType = (xml) ->
    if !xml?
      return null

    try
      id = my.exSingleNode(xml)
    catch ex
      console.log "ID missing"
      id = null

    try
      idType = my.exSingleAtt(my.evaluateXPath(xml,"./@idType"))
    catch ex
      idType = null

    try
      tagType = my.exSingleAtt(my.evaluateXPath(xml,"./@tagType"))
    catch ex
      tagType = null

    try
      startDate = my.exSingleAtt(my.evaluateXPath(xml,"./@startDate"))
    catch ex
      startDate =  null

    try
      endDate = my.exSingleAtt(my.evaluateXPath(xml,"./@endDate"))
    catch ex
      endDate =  null

    if idType? or tagType? or startDate? or endDate?
      result = {
        id: id
        idType: idType
        tagType: tagType
        startDate: startDate
        endDate: endDate
      }
      my.filterNullKeys(result)
    else
      id

  my.parseObjects = (xml) ->
    if !xml?
      return null

    try
      version = my.exSingleAtt(my.evaluateXPath(xml,"./@version"))
    catch ex
      console.log ex
      version = null
    try
      prefix = my.exSingleAtt(my.evaluateXPath(xml,"./@prefix"))
    catch ex
      console.log ex
      prefix = null

    try
      objects = my.headOrElseNull(my.evaluateXPath(xml, "./odf:Object").map my.parseObject)
    catch ex
      console.log ex
      objects = null

    result = {
      version: version
      prefix: prefix
      Object: objects
    }

    my.filterNullKeys(result)

  my.parseObject = (xml) ->
    if !xml?
      return null

    try
      ids = my.headOrElseNull(my.evaluateXPath(xml,"./odf:id").map my.parseIoTIdType)
    catch ex
      console.log ex
      ids = null
      console.log("Object Id missing")

    console.log ids

    try
      type = my.exSingleAtt(my.evaluateXPath(xml,"./@type"))
    catch ex
      console.log ex
      type = null
    
    try
      objects = my.headOrElseNull(my.evaluateXPath(xml, "./odf:Object").map my.parseObject)
    catch ex
      console.log ex
      objects = null

    try
      infoitems = my.headOrElseNull(my.evaluateXPath(xml, "./odf:InfoItem").map my.parseInfoItem)
    catch ex
      console.log ex
      infoitems = null
    
    try
      descriptions = my.headOrElseNull(my.evaluateXPath(xml,"./odf:description").map my.parseDescription)
    catch ex
      console.log ex
      descriptions = null

    if ids?
      my.filterNullKeys({
        id: ids
        type: type
        description:descriptions
        InfoItem: infoitems
        Object: objects
      })
    else
      null
      


  my.parseInfoItem = (xml) ->
    if !xml?
      return null

    try
      name = my.exSingleAtt(my.evaluateXPath(xml,"./@name"))
    catch ex
      console.log "name missing for infoitem"
      name = null
 
    try
      type = my.exSingleAtt(my.evaluateXPath(xml,"./@type"))
    catch ex
      type = null

    try
      altnames = my.headOrElseNull(my.evaluateXPath(xml,"./odf:altname").map my.parseIoTIdType)
    catch ex
      altnames = null

    try
      values = my.headOrElseNull(my.evaluateXPath(xml, "./odf:value").map my.parseValue)
    catch ex
      values = null

    try
      metadatas = my.headOrElseNull(my.evaluateXPath(xml, "./odf:MetaData").map my.parseMetaData)
    catch ex
      metadatas = null

    try
      descriptions = my.headOrElseNull(my.evaluateXPath(xml, "./odf:description").map my.parseDescription)
    catch ex
      descriptions = null

    if !name?
      null
    else
      my.filterNullKeys({
        name: name
        type: type
        altname: altnames
        description: descriptions
        MetaData: metadatas
        value: values
      })

  my.parseDescription = (xml) ->
    if !xml?
      return null

    try
      text = my.exSingleNode(xml)
    catch ex
      console.log "Empty description"
      text = null

    try
      lang = my.exSingleAtt(my.evaluateXPath(xml,"./@lang"))
    catch ex
      lang = null

    if !text?
      null
    else
      my.filterNullKeys({
        lang: lang
        text: text
      })

  my.parseMetaData = (xml) ->
    if !xml?
      return null

    try
      infoitems = my.headOrElseNull(my.evaluateXPath(xml, "./odf:InfoItem").map my.parseInfoItem)
    catch ex
      infoitems = null

    if infoitems?
      {InfoItem: infoitems}
    else
      ""
  
  my.parseValue = (xml) ->
    if !xml?
      return null

    try
      type = my.exSingleAtt(my.evaluateXPath(xml, "./@type"))
    catch ex
      type = null

    try
      dateTime = my.exSingleAtt(my.evaluateXPath(xml,"./@dateTime"))
    catch ex
      dateTime = null

    try
      unixTime = my.exSingleAtt(my.evaluateXPath(xml,"./@unixTime"))
      if !unixTime?
        unixTime = null
      else
        unixTime = Number(unixTime)
    catch ex
      unixTime = null
 

    try
      content = my.exSingleNode(xml)
      content = switch type.toLowerCase()
        when "xs:string" then content
        when "xs:integer" then Number(content)
        when "xs:int" then Number(content)
        when "xs:long" then Number(content)
        when "xs:decimal" then Number(content)
        when "xs:double" then Number(content)
        when "xs:integer" then Number(content)
        when "xs:boolean" then switch content.toLowerCase()
          when "true" then true
          when "false" then false
          when "1" then true
          when "0" then false
          else false
        else content
    catch ex
      content = null

    try
      objects = my.parseObjects(my.evaluateXPath(xml, "./odf:Objects")[0])
    catch ex
      objects = null

    if objects?
      my.filterNullKeys({
        type: type
        dateTime: dateTime
        unixTime: unixTime
        content:
          Objects: objects
      })
    else
      my.filterNullKeys({
        type: type
        dateTime: dateTime
        unixTime: unixTime
        content: content
      })
      







  WebOmi


window.WebOmi = xmlConverter(window.WebOmi || {})
window.json = "ready"
