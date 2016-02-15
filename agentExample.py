#!/bin/env python3

from socket import create_connection

connection = create_connection(("localhost", 8181))

while True:
    data = input()
    odf_message = \
    b'''<Objects xmlns="odf.xsd">
            <Object>
                <id>ExampleAgent</id>
                <InfoItem name="ExampleAgentSensor">
                    <value>''' +\
                    bytes(data, "utf-8") +\
                b'''</value>
                </InfoItem>
            </Object>
        </Objects>'''
    connection.sendall(odf_message)
