
Prometheus metrics
==================

Current metrics system is implemented via Prometheus, because it is good and
efficient timeseries database and often used for server diagnostics. Prometheus
can also be used directly from Grafana, which is easy to use graph and
dashboard editor.


Setup
-----

1. Install Prometheus and Grafana
2. In O-MI-node `application.conf`: set `omi-service.metrics.enable = true`
3. In `prometheus.yml`, under `scrape_configs`, add new job:
   ```
   - job_name: 'o-mi-node'
    static_configs:
    - targets: ['localhost:6060']
    ```
4. (re)start O-MI-node, Prometheus and Grafana
5. Open Grafana http://localhost:3000/ (default user and pass are: `admin` and `admin`)
6. Add a new datasource, Prometheus at http://localhost:9090 (you need to click and select the url even if the placeholder is correct)


Configuration
-------------

Some metrics data uses the Histogram data model, like request duration. It
requires configuration of bucket boundaries, such that each duration would be
added to a bucket that would indicate useful statistics. The bucket boundaries
depend on how fast the server is and how large requests are encountered with
the use case on hand.



