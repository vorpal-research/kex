#!/bin/bash

kex=$(pwd)

echo "
[subrepos]
git:allowed = true" >> $kex/.hg/hgrc
hg add .hgsub
hg update
cd $kex/boolector
./configure.sh
