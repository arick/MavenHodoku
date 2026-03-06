#!/bin/bash
java --add-opens java.desktop/com.apple.laf=ALL-UNNAMED -jar "$(dirname "$0")/target/hodoku-3.0.0.jar"
