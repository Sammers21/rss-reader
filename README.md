# Rss-reader

This is the demo of how you can use [vertx-cassandra-client](https://github.com/Sammers21/vertx-cassandra-client) by real example.


## Running

1. Before running the example you should ensure that cassandra service running locally on port 9042.

2. Building [vertx-cassandra-client](https://github.com/Sammers21/vertx-cassandra-client) locally:
    ```bash
    git clone https://github.com/Sammers21/vertx-cassandra-client
    cd vertx-cassandra-client
    mvn clean install -Dmaven.test.skip=true -s .travis.maven.settings.xml 
    ```
3. Running the app
    ```bash
    ./gradlew run
    ```
4. Now you can open your browser and go to `localhost:8080`