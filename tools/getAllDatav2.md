`usage: getAllDatav2.py [-h] [--output OUTPUT] [--max-newest NMAX] [--ssl-client-cert CCERT] [--ssl-client-key CKEY] [--odf-xmlns ODFVERSION] [--omi-xmlns OMIVERSION] [--omi-version VERSION] [--sort] [--chunk-size CHUNK_SIZE] [--verbose] [--pretty-print] [--version] URL`


Get All Data from target server, including descriptions, metadata and historical values. The results are saved in files separated by each infoitem.

```
positional arguments:
  * URL

optional arguments:
  -h, --help            show this help message and exit
  --output OUTPUT, -o OUTPUT
                        Output files directory
  --max-newest NMAX, -n NMAX
                        Max value for newest parameter, if n is small, multiple request may be neccessary to query all the data from a single InfoItem.
  --ssl-client-cert CCERT
                        File containing the client certificate as well as any number of CA certificates needed to establish the authenticity of the certificate
  --ssl-client-key CKEY
                        File containing the private key. If omitted the key is taken from the cert file as well.
  --odf-xmlns ODFVERSION
                        Xml namespace for the O-DF. Default: http://www.opengroup.org/xsd/odf/1.0/
  --omi-xmlns OMIVERSION
                        Xml namespace for the O-MI. Default: http://www.opengroup.org/xsd/omi/1.0/
  --omi-version VERSION
                        Value of omiEnvelope version attribute. If empty, attempts to parse from namespace or defaults to 1.0 if parsing fails.
  --sort                Sorts received values, required if the target server does not sort values for the response messages. Latest value must be at top.
  --chunk-size CHUNK_SIZE
                        Size of the response chunks written to file in bytes. Increasing this value might increase memory consumption. Default 1024
  --verbose, -v         Print extra information during processing
  --pretty-print        Pretty prints the output xml
  --version             show program's version number and exit
```
