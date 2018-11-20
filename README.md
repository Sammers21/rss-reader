[![Build Status](https://travis-ci.org/Sammers21/rss-reader.svg?branch=master)](https://travis-ci.org/Sammers21/rss-reader)

# Rss-reader

This is the demo of how you can use [vertx-cassandra-client](https://github.com/Sammers21/vertx-cassandra-client) by real example.


## Running

1. Before running the example you should ensure that cassandra service running locally on port 9042.
As a option you can run cassandra with [ccm](https://github.com/riptano/ccm#installation)(Cassandra Cluster Manager).
Follow [this](https://github.com/riptano/ccm#installation) instructions for installing ccm.
After installing you will be able to run a single node cluster:
    ```bash
    ccm create rss_reader -v 3.11.2 -n 1 -s
    ccm start
    ```

2. Building [vertx-cassandra-client](https://github.com/Sammers21/vertx-cassandra-client) locally:
    ```bash
    git clone https://github.com/Sammers21/vertx-cassandra-client
    cd vertx-cassandra-client
    mvn clean install -Dmaven.test.skip=true -s .travis.maven.settings.xml 
    ```
3. Running the app:
    ```bash
    ./gradlew vertxRun
    ```
    You may also noted that we are using [vertx-gradle-plugin](https://github.com/jponge/vertx-gradle-plugin) for running the app.
    It means that the app will be reloaded automatically when code changes.
4. Now you can open your browser and go to `localhost:8080`
