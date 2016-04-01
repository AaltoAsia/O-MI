#!/bin/sh
# Send O-MI message from file to otaniemi3d using a client certificate

CLIENTCERT=otaniemi3d-client.p12
CLIENTCERT_TYPE=p12
CLIENTCERT_PASSWORD=

SERVER_HTTPS_CACERT=chain_TERENA_SSL_CA_2.pem

usage() {
    echo "usage: $0 <file>"
}

if [[ ! -f $1 ]]; then
    echo "Invalid file given: $1"
    usage
    exit 1
fi

# password is given as part of cert argument, seperated by ':'
if [[ -n "$CLIENTCERT_PASSWORD" ]]; then
    CLIENTCERT="$CLIENTCERT:$CLIENTCERT_PASSWORD"
fi

curl \
    --header "Content-Type:text/xml;charset=UTF-8" \
    --data "@$1" \
    --cert-type $CLIENTCERT_TYPE \
    --cert $CLIENTCERT \
    --cacert $SERVER_HTTPS_CACERT \
    --verbose \
    "https://otaniemi3d.cs.hut.fi/omi/node/"

