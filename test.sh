#!/bin/sh
# Shell script for running tests.

# Compile required files.
# TODO: kill children processes when test.sh is killed
javac Sender.java Receiver.java PLDModule.java Logger.java

files="test0.pdf test1.pdf" # test2.pdf"
out_file="test_out.pdf"

in_file="test2.pdf"
# Stop and wait protocol.
# for in_file in $files 
# do
	java Receiver 8000 $out_file &
	echo "Terminate receiver: kill -15 $!"
	java Sender 127.0.0.1 8000 $in_file 500 500 6 0 0.5 0 &
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
# done

echo "SUCCESS all tests passed"
exit 0




