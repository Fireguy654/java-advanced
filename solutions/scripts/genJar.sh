#!/bin/sh

cd "$(dirname "${BASH_SOURCE[0]}")"
COMP_DIR="compOut"
mkdir -p $COMP_DIR
TEST_DIR="../../java-advanced-2024/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/"
JAVA_FILES="../java-solutions/info/kgeorgiy/ja/nebabin/implementor/*
     $TEST_DIR/Impler.java
     $TEST_DIR/JarImpler.java
     $TEST_DIR/ImplerException.java"
javac -cp . $JAVA_FILES -d $COMP_DIR
jar cfm "Implementor.jar" "MANIFEST.MF" -C $COMP_DIR .
rm -r $COMP_DIR