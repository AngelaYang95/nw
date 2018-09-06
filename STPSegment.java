import java.nio.*;
import java.io.*;
import java.util.*;

/*
 * This is a utility class for parsing STPSegment.
 */
public class STPSegment implements Serializable {
	final static int HEADER_BYTES = 12;

	final static int SYN_MASK = 1;
	final static int ACK_MASK = 2;
	final static int DATA_MASK = 4;
	final static int FIN_MASK = 8;
	
	final static int SEQ_OFFSET = 0;
	final static int ACK_OFFSET = 4;
	final static int FLAGS_OFFSET = 8;

	private int seqNum = 0;
	private int ackNum = 0;
	private int flags = 0;
	private byte[] data;

	public STPSegment(int seqNum, int ackNum, int flags, byte[] data) {
    this.seqNum = seqNum;
    this.ackNum = ackNum;
    this.flags = flags;
    this.data = data;
	}

	public byte[] toByteArray() {
    ByteBuffer segment = ByteBuffer.allocate(HEADER_BYTES + data.length);
    segment.putInt(seqNum);
    segment.putInt(ackNum);
    segment.putInt(flags);
    segment.put(data);
		return segment.array();
	}

	public int getSeqNum() { return seqNum; }
	public int getAckNum() { return ackNum; }
	public int getFlags() { return flags; }
	public byte[] getData() { return data; }

	public void setSeqNum(int n) { seqNum = n; }
	public void setAckNum(int n) { ackNum = n; }
	public void setFlags(int n) { flags = n; }

	public static int getSeqNum(byte[] s) {
		return ByteBuffer.wrap(s).getInt(SEQ_OFFSET);
	}

	public static int getAckNum(byte[] s) {
		return ByteBuffer.wrap(s).getInt(ACK_OFFSET);
	}

	public static int getFlags(byte[] s) {
		return ByteBuffer.wrap(s).getInt(FLAGS_OFFSET);
	}

	public static byte[] getData(byte[] s, int start, int end) {
		return Arrays.copyOfRange(s, start, end);
	}
}