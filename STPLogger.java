import java.nio.*;
import java.io.*;
import java.util.*;
import java.text.DecimalFormat;

public class STPLogger {

	private File file;
	private PrintWriter printer;
	private long startTime = 0;

	public STPLogger(String filename) {
		File file = new File(filename);
		try {
			file.createNewFile();
			printer = new PrintWriter(new FileOutputStream(file));
		} catch(IOException e) {
		}
	}

	public void log(STPSegment s, Event... events) {
		printer.printf("%-20s %-20.2f %-4s %-4d %-4d %-4d\n", eventsToString(events, s.isRxt()), getTime(), 
				flagsToString(s.getFlags()), s.getSeqNum(), s.getData().length, s.getAckNum());
		printer.flush();
	}

	public double getTime() {
		if(startTime == 0) startTime = System.currentTimeMillis();
		return (double)(System.currentTimeMillis() - startTime) / 1000;
	}

	/* Concats the string rep. for the list of events. */
	public String eventsToString(Event[] events, boolean isRxt) {
		StringJoiner sj = new StringJoiner("/");
		for(Event e : events) {
			sj.add(e.toString());
		}
		if(isRxt) {
			sj.add(Event.RXT.toString());
		}
		return sj.toString();
	}

	/* Extracts the packet code from the flags. */
	public String flagsToString(int flags) {
		StringJoiner sj = new StringJoiner("/");
		if((flags & STPSegment.SYN_MASK) == STPSegment.SYN_MASK) {
			sj.add("S");
		}
		if((flags & STPSegment.ACK_MASK) == STPSegment.ACK_MASK) {
			sj.add("A");
		}
		if((flags & STPSegment.DATA_MASK) == STPSegment.DATA_MASK) {
			sj.add("D");
		}
		if((flags & STPSegment.FIN_MASK) == STPSegment.FIN_MASK) {
			sj.add("F");
		}
		return sj.toString();
	}

	public void logSenderStats(int fileSize, int numSeg, PLDModule.Stat stat, int retransmits, int fastRetransmits, int dupAcks) {
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
		printer.flush();
	}

	public void logReceiverStats(int bytes, int segs, int dataSeg, int bitErrors, int dupDataSegs, int dupAcks) {
		String format = "%-20s %-10d\n";
		printer.printf("=============================================================\n");
		printer.printf(format, "Amount of data received (bytes)", bytes);
		printer.printf(format, "Total Segments Received", segs);
		printer.printf(format, "Data segments received", dataSeg);
		printer.printf(format, "Data segments with Bit Errors", bitErrors);
		printer.printf(format, "Duplicate data segments received", dupDataSegs);
		printer.printf(format, "Duplicate ACKs sent ", dupAcks);
		printer.printf("=============================================================\n");
		printer.flush();
	}
	
	public void close() {
		printer.close();
	}
}