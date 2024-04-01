#!/bin/sh

JDOCDIR="solutions/javadoc"
cd "$(dirname "${BASH_SOURCE[0]}")"/../..
rm -rf $JDOCDIR
DOC_SRC_FILES="solutions/java-solutions/info/kgeorgiy/ja/nebabin/implementor/Implementor.java
     java-advanced-2024/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/*Impler.java"
COMP_CLASS_PATH="java-advanced-2024/artifacts/info.kgeorgiy.java.advanced.implementor.jar"
javadoc -d "$JDOCDIR" -private $DOC_SRC_FILES --class-path "$COMP_CLASS_PATH"