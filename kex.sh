#!/bin/bash

time java \
    -Xmx8196m \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-jar core/target/core-*-jar-with-dependencies.jar $@
