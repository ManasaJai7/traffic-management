#!/bin/sh
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -cp "lib/*" -d out src/*.java && java -cp "out:lib/*" Main
