import java.nio.*;
import java.io.*;
import java.util.*;

public class Logger {
	private File file;
	private PrintWriter printer;

	public Logger(String filename) {
		File file = new File(filename);
		try {
			file.createNewFile();
			printer = new PrintWriter(new FileOutputStream(file));
		} catch(IOException e) {
		}
	}

	public void log(Event e, double time, int seqNum, int numBytes, int ackNum) {
		printer.printf("%-20s %-20f %-4d %-4d %-4d\n", e.toString(), time, seqNum, numBytes, ackNum);
		printer.flush();
	}

	public void logStats(int fileSize, int numSeg, PLDModule.Stat stat, int retransmits, int fastRetransmits, int dupAcks) {
		String format = "%-20s %-10d\n";
		printer.printf("=============================================================\n");
		printer.printf(format, "Size of the file(in Bytes)", fileSize);
		printer.printf(format, "Segments transmitted (including drop & RXT)", numSeg);
		printer.printf(format, "Number of Segments handled by PLD", stat.segments);
		printer.printf(format, "Number of Segments dropped", stat.dropped);
		printer.printf(format, "Number of Segments Corrupted", stat.corrupted);
		printer.printf(format, "Number of Segments Re-ordered", stat.reordered);
		printer.printf(format, "Number of Segments Duplicated", stat.duplicated);
		printer.printf(format, "Number of Segments Delayed", stat.delayed);
		printer.printf(format, "Number of Retransmissions due to TIMEOUT", retransmits);
		printer.printf(format, "Number of FAST RETRANSMISSION", fastRetransmits);
		printer.printf(format, "Number of DUP ACKS received", dupAcks);
		printer.printf("=============================================================\n");
	}

	public void close() {
		printer.close();
	}
}