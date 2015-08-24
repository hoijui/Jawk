#!/bin/sh

CWD_ORIG=$(pwd)

cd modules/maven-plugin/
mvn install

cd /home/robin/Projects/spring/repos/develop/AI/Interfaces/Java/modules/api/
mvn compile

cd ${CWD_ORIG}
