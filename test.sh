#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/tests
javac --release 11 -encoding UTF-8 -d build/tests app/src/main/java/com/ordanexa/fidoreader/{Cbor,Crypto,Hid,Reader,TokenSerial}.java tests/ReaderTest.java tests/TokenSerialTest.java
java -cp build/tests com.ordanexa.fidoreader.ReaderTest
java -cp build/tests com.ordanexa.fidoreader.TokenSerialTest
