#!/bin/bash
java --add-opens java.desktop/com.apple.laf=ALL-UNNAMED -jar "$(dirname "$0")/target/hodoku-1.0.jar"
