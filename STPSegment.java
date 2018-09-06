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

	private int seqNum;
	private int ackNum;
	private int flags;
	private byte[] data;

	public STPSegment(byte[] segment) {
		ByteBuffer buff = ByteBuffer.wrap(segment);
		this.seqNum = buff.getInt();
		this.ackNum = buff.getInt();
		this.flags = buff.getInt();
		this.data = Arrays.copyOfRange(segment, HEADER_BYTES, segment.length);
	}

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

	// public static STPSegment getSegment(DatagramPacket packet) throws IOException {
	// 	byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
	// 	ObjectInputStream oStream = new ObjectInputStream(new ByteArrayInputStream(data));
	// 	STPSegment segment = null;
	// 	// try {
	// 	// 	segment = (STPSegment) oStream.readObject();
	// 	// } catch (ClassNotFoundException e) {
	// 	// 	e.printStackTrace();
	// 	// }
	// 	return segment;
	// }

	// public static byte[] serialize(STPSegment segment) throws IOException {
	// 	ByteArrayOutputStream bStream = new ByteArrayOutputStream();
	// 	ObjectOutputStream oStream = new ObjectOutputStream(bStream);
	// 	oStream.writeObject(segment);
	// 	byte[] s = bStream.toByteArray();
	// 	// oStream.close();
	// 	// bStream.close();
	// 	return s;
	// }
}