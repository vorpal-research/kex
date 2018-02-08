#!/bin/bash

rm *.log
java -jar target/kex-*-jar-with-dependencies.jar $@
