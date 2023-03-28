#!/bin/sh

if ./gradlew cli:shadowJar;
then
  java -jar cli/build/libs/samt-cli.jar "$@"
fi

