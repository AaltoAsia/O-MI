#!/bin/bash
# Send O-MI message from file to url

usage() {
    echo "usage: $0 file url"
}

if [[ ! -f $1 ]]; then
    echo "Invalid file given: $1"
    usage
    exit 1
fi
if [[ -z $2 ]]; then
    echo "No url was given as second argument"
    usage
    exit 1
fi

curl --max-time 180 -X POST --header "Content-Type:text/xml;charset=UTF-8" -H "Transfer-Encoding:chunked" --data @"$1" "$2"

