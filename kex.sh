#!/bin/bash

java -Dlogback.configurationFile=/logback.xml \
    -jar target/kex-*-jar-with-dependencies.jar $@
