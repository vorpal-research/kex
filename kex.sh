#!/bin/bash

time java \
  -Xmx8g \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-jar kex-runner/target/kex-runner-*-jar-with-dependencies.jar $*
