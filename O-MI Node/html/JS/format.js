function formatXml(xml) {
    var formatted = '';
    var reg = /(>)(<)(\/*)/g;
    xml = xml.replace(reg, '$1\r\n$2$3');
    var pad = 0;
    jQuery.each(xml.split('\r\n'), function(index, node) {
        var indent = 0;
		var trim = node.trim();
        if (trim.match( /.+<\/\w[^>]*>$/ )) {
            indent = 0;
        } else if (trim.match( /^<\/\w/ )) {
            if (pad != 0) {
                pad -= 2;
            }
        } else if (trim.match( /^<\w[^>]*[^\/]>.*$/ )) {
            indent = 2;
        } else {
            indent = 0;
        }

        var padding = '';
        for (var i = 0; i < pad; i++) {
            padding += ' ';
        }

        formatted += padding + trim + '\r\n';
        pad += indent;
    });
    var formattedXML = hljs.highlightAuto(formatted);

    return formattedXML;
}