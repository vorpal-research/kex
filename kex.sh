#!/bin/bash

time java \
  -Xmx8g \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener \
	-jar kex-runner/target/kex-runner-*-jar-with-dependencies.jar $*
