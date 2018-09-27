#!/bin/bash

rm *.log
time java \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-jar core/target/core-*-jar-with-dependencies.jar $@
