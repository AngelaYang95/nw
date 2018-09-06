#!/bin/sh
# Shell script for running tests.

# Compile required files.
javac Sender.java Receiver.java PLDModule.java Logger.java


# Stop and wait protocol.
in_file="test0.pdf"
out_file="test_out.pdf"
java Receiver 8000 $out_file &
java Sender 127.0.0.1 8000 $in_file 500 500 6 0 &

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




