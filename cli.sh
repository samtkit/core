#!/bin/sh

./gradlew cli:shadowJar
java -jar cli/build/libs/samt-cli.jar "$@"
