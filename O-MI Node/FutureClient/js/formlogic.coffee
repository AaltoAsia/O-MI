
# Intialize events, import
((consts, requests) ->
    consts.afterJquery ->
        consts.readAllBtn
            .on 'click', -> requests.readAll(true)

)(WebOmi.consts, WebOmi.requests)
