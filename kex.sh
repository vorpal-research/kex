#!/bin/bash

time java \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-jar core/target/core-*-fat.jar $@
