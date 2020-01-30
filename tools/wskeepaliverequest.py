#!/usr/bin/python3

import sys, getopt
import websocket
from functools import partial

def on_message(ws, message, output):
    if output:
        print("TODO file writing")
    else:
        print("## Got Message ##")
        print(message)
        print("## Message End ##")

def on_cont_message(ws, message, last_frame_flag, output):
    #last_frame_flag is 0 if messages continue
    #output is file path, none will print to console
    if output:
        print("TODO file writing with multiple frames")

    else:
        if last_frame_flag:
            print("## Got Message ##")
            print(message)
            print("## Message End ##")
        else:
            print("## Got Message ##")
            print(message)

def on_close(ws):
    print("# Connection closed #")

def on_error(ws, error):
    print(error)

def on_pong(ws, data):
    print("# Pong! #")

def on_open(ws, request_message):
    if request_message:
        ws.send(request_message)
        #ws.send("""<omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="0"><read msgformat="odf" callback="0" interval="5"><msg><Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/"><Object><id>OMI-Service</id><Object><id>Settings</id><InfoItem name="num-latest-values-stored"/></Object></Object></Objects></msg></read></omiEnvelope>""")
    print("# Connection Open #")

def main(hostn, output,request):
    ws = websocket.WebSocketApp(
            hostn,
            on_message = partial(on_message, output=output),
            on_cont_message = partial(on_cont_message, output=output),
            on_error = on_error,
            on_close = on_close,
            on_pong = on_pong,
            on_open = partial(on_open, request_message=request))
    ws.run_forever(ping_interval = 50)

def usage():
    print('wekeepaliverequest.py -o <outputfile> host')

if __name__ == "__main__":
    hostn = 'ws://localhost:8080'
    output = None #print to console
    request = None
    try:
        opts, args = getopt.getopt(sys.argv[1:],"ho:r:", ["help","output=", "request="])
    except getopt.GetoptError as err:
        print(err)
        usage()
        sys.exit(2)
    for opt, arg in opts:
        if opt in ("-h","--help"):
            usage()
            sys.exit()
        elif opt in ("-o","--output"):
            output = arg
        elif opt in ("-r","--request"):
            request = arg
        else:
            assert False, "unhandled option"
    if len(args) >= 1:
        hostn = args[0]

    main(hostn,output,request)

