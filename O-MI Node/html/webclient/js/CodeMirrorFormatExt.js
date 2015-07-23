
(function (CodeMirror) {
    CodeMirror.extendMode("xml", {
        newlineAfterToken: function(type, content, textAfter, state) {
          return ((type === "tag" && />$/.test(content) && state.context) ||
                  (type !== null  && /^</.test(textAfter)));
        }
    });
    // CodeMirror autoFormat extension
    CodeMirror.defineExtension("autoFormatRange", function (from, to) {
        var cm = this;
        var outer = cm.getMode(), text = cm.getRange(from, to).split("\n");
        var state = CodeMirror.copyState(outer, cm.getTokenAt(from).state);
        var tabSize = cm.getOption("tabSize");

        var out = "", lines = 0, atSol = from.ch === 0;
        function newline() {
            out += "\n";
            atSol = true;
            ++lines;
        }

        for (var i = 0; i < text.length; ++i) {
            var stream = new CodeMirror.StringStream(text[i], tabSize);
            while (!stream.eol()) {
                var inner = CodeMirror.innerMode(outer, state);
                var style = outer.token(stream, state), cur = stream.current();
                stream.start = stream.pos;
                if (!atSol || /\S/.test(cur)) {
                    out += cur;
                    atSol = false;
                }
                if (!atSol && inner.mode.newlineAfterToken &&
                    inner.mode.newlineAfterToken(style, cur, stream.string.slice(stream.pos) || text[i+1] || "", inner.state)){
                    newline();
                }
            }
            if (!stream.pos && outer.blankLine) {outer.blankLine(state);}
            if (!atSol) {newline();}
        }

        cm.operation(function () {
            cm.replaceRange(out, from, to);
            for (var cur = from.line + 1, end = from.line + lines; cur <= end; ++cur){
                cm.indentLine(cur, "smart");
            }
            //cm.setSelection(from, cm.getCursor(false));
        });
    });

    // Gets full content range for given CodeMirror editor
    // @return Range object {from: {line, ch}, to: {line, ch}}
    CodeMirror.defineExtension("getFullRange", function () {
        var cm = this;
        var from, to;
        from = { line: 0, ch: 0 };
        to = cm.posFromIndex(cm.getValue().length);
        return {
          from: from,
          to: to
        };
    });

    CodeMirror.defineExtension("autoFormatAll", function () {
        var cm = this;
        var range = cm.getFullRange();
        cm.autoFormatRange(range.from, range.to);
    });

    // Applies automatic mode-aware indentation to the specified range
    CodeMirror.defineExtension("autoIndentRange", function (from, to) {
        var cmInstance = this;
        this.operation(function () {
            for (var i = from.line; i <= to.line; i++) {
                cmInstance.indentLine(i, "smart");
            }
        });
    });
}(CodeMirror));

