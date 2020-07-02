`usage: getAllDatav2.py [-h] [--output OUTPUT] [--odf-path ODFPATH]
                       [--max-newest NMAX] [--ssl-client-cert CCERT]
                       [--ssl-client-key CKEY] [--odf-xmlns ODFVERSION]
                       [--omi-xmlns OMIVERSION] [--omi-version VERSION]
                       [--sort] [--single-file]
                       [--compression {0,1,2,3,4,5,6,7,8,9}]
                       [--chunk-size CHUNK_SIZE] [--verbose] [--pretty-print]
                       [--version]
                       URL`

Get All Data from target server, including descriptions, metadata and
historical values. The results are saved in files separated by each infoitem.

```
positional arguments:
  URL

optional arguments:
  -h, --help            show this help message and exit
  --output OUTPUT, -o OUTPUT
                        Output files directory. If --single-file argument is
                        used, the output value is interprented as file name.
  --odf-path ODFPATH, --path ODFPATH, -p ODFPATH
                        Select only InfoItems under some O-DF paths rather
                        than read all.
  --max-newest NMAX, -n NMAX
                        Max value for newest parameter, if n is small,
                        multiple request may be neccessary to query all the
                        data from a single InfoItem. Some servers might limit
                        the newest parameter.
  --ssl-client-cert CCERT
                        File containing the client certificate as well as any
                        number of CA certificates needed to establish the
                        authenticity of the certificate
  --ssl-client-key CKEY
                        File containing the private key. If omitted the key is
                        taken from the cert file as well.
  --odf-xmlns ODFVERSION
                        Xml namespace for the O-DF. Default:
                        http://www.opengroup.org/xsd/odf/1.0/
  --omi-xmlns OMIVERSION
                        Xml namespace for the O-MI. Default:
                        http://www.opengroup.org/xsd/omi/1.0/
  --omi-version VERSION
                        Value of omiEnvelope version attribute. If empty,
                        attempts to parse from namespace or defaults to 1.0 if
                        parsing fails.
  --sort                Sorts received values, required if the target server
                        does not sort values for the response messages. Latest
                        value must be at top.
  --single-file         Store the results in a single file instead of a file
                        strucure.
  --compression {0,1,2,3,4,5,6,7,8,9}, -c {0,1,2,3,4,5,6,7,8,9}
                        If compression level is more than 0, gzip is used to
                        compress the output files and .gz suffix is added to
                        the output file.
  --chunk-size CHUNK_SIZE
                        Size of the response chunks written to file in bytes.
                        Increasing this value might increase memory
                        consumption. Default 1024
  --verbose, -v         Print extra information during processing
  --pretty-print        Pretty prints the output xml
  --version             show program's version number and exit
```
