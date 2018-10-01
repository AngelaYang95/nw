#!/bin/sh
# Shell script for running tests.

# Compile required files.
# TODO: kill children processes when test.sh is killed
javac Sender.java Receiver.java PLDModule.java STPLogger.java

files="test0.pdf test1.pdf" # test2.pdf"
out_file="test_out.pdf"

in_file="test0.pdf"
# in_file="test1.pdf"

java Receiver 8000 $out_file &
echo "Terminate receiver: kill -15 $!"
java Sender 127.0.0.1 8000 $in_file 600 150 4 0.2 0.1 0.1 0.1 4 0.1 50 100&
# java Sender 127.0.0.1 8000 $in_file 500 50 2 0 0 0 0 0 0.2 1000 300&
echo "Terminate sender: kill -15 $!"

wait
diff -y --suppress-common-lines $in_file $out_file
if [ $? -eq 0 ]
then
	echo "PASS $in_file"
else 
	echo "FAILED $in_file"
	exit 1
fi

echo "SUCCESS all tests passed"
exit 0




