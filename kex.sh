#!/bin/bash

time java \
    -Xmx8196m \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-jar runner/target/runner-*-jar-with-dependencies.jar $@
