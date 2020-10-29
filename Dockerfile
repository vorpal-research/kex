FROM abdullin/archlinux-aur:latest
MAINTAINER Azat Abdullin <abdullin@kspt.icc.spbstu.ru>

ENV Z3_VERSION "4.8.6"
ENV JAVA_HOME /lib/jvm/default

WORKDIR /home
COPY . /home
RUN ./configure.sh

USER arch-user
WORKDIR /tmp
RUN yaourt -S --noconfirm \
	z3-java \
	boolector-java
RUN mvn install:install-file -Dfile=/usr/lib/com.microsoft.z3.jar -DgroupId=com.microsoft -DartifactId=z3 -Dversion=$Z3_VERSION -Dpackaging=jar

USER root
RUN chmod -R a+rwx /home
USER arch-user
WORKDIR /home
RUN git clone https://github.com/vorpal-research/kex.git
WORKDIR /home/kex
RUN mvn clean verify