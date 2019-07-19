#!/bin/bash
# Send O-MI message from file to url

filepath=$1
shift
nodeurl=$1
shift

usage() {
    echo "usage: $0 file url"
}

if [[ ! -f "$filepath" ]]; then
    echo "Invalid file given: $1"
    usage
    exit 1
fi
if [[ -z "$nodeurl" ]]; then
    echo "No url was given as second argument"
    usage
    exit 1
fi

curl --max-time 180 -X POST --header "Content-Type:text/xml;charset=UTF-8" -H "Transfer-Encoding:chunked" --data "@$filepath" "$nodeurl" $*

