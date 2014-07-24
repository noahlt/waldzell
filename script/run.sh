#!/bin/sh

java -cp ext/swt.jar:ext/jackson-core-2.3.4.jar:ext/jackson-databind-2.3.4.jar:ext/jackson-annotations-2.3.0.jar:build -XstartOnFirstThread org.noahtye.scalator.Editor
