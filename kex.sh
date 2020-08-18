#!/bin/bash

time java \
    -Xmx8196m \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.lang.module=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.module=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.loader=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.reflect=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.ref=ALL-UNNAMED \
    --add-opens java.base/jdk.internal.util.jar=ALL-UNNAMED \
	-Djava.security.manager \
	-Djava.security.policy==kex.policy \
	-jar kex-runner/target/kex-runner-*-jar-with-dependencies.jar $*
