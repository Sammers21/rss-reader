#!/bin/bash

git clone https://github.com/Sammers21/vertx-cassandra-client
cd vertx-cassandra-client
git checkout with_codegen
mvn clean install -Dmaven.test.skip=true -s .travis.maven.settings.xml
cd ..
./gradlew build