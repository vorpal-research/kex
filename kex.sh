#!/bin/bash

rm *.log
java \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-jar target/kex-*-jar-with-dependencies.jar $@
