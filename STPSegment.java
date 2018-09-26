import java.nio.*;
import java.io.*;
import java.util.*;

/*
 * This is a utility class for STPSegments.
 * The class is serialised via bytebuffers for better perf.
 */
public class STPSegment implements Serializable {
	
	public final static int HEADER_BYTES = 12;
	public final static short SYN_MASK = 1;
	public final static short ACK_MASK = 2;
	public final static short DATA_MASK = 4;
	public final static short FIN_MASK = 8;

	private int seqNum;
	private int ackNum;
	private short flags;
	private short checksum = 0;
	private byte[] data;
	private boolean rxt = false;

	public STPSegment(int seqNum, int ackNum, short flags, byte[] data) {
    this.seqNum = seqNum;
    this.ackNum = ackNum;
    this.flags = flags;
    this.checksum = calculateChecksum(this);
    this.data = data;
	}

	public int getSeqNum() { return seqNum; }

	public int getAckNum() { return ackNum; }

	public short getFlags() { return flags; }

	public short getChecksum() { return checksum; }

	public byte[] getData() { return data; }

	public boolean isRxt() { return rxt; }

	public void setSeqNum(int n) { seqNum = n; }

	public void setAckNum(int n) { ackNum = n; }

	public void setFlags(short n) { flags = n; }

	public void setChecksum(short n) { checksum = n; }

	public void setData(byte[] data) { this.data = data; }

	public void setRxt(boolean rxt) { this.rxt = rxt; }
	
	@Override
	public boolean equals(Object other) {
		if(other == null) {
			return false;
		}

		if(!(other instanceof STPSegment)) {
			return false;
		}

		STPSegment segment = (STPSegment) other;
    return this.getSeqNum() == segment.getSeqNum();
	}

	/* 
	 * Deserialise a byte array back to an STPSegment. 
	 */
	public static STPSegment deserialize(byte[] bytes) {
		ByteBuffer buff = ByteBuffer.wrap(bytes);
		int seqNum = buff.getInt();
		int ackNum = buff.getInt();
		short flags = buff.getShort();
		short checksum = buff.getShort();
		byte[] data = Arrays.copyOfRange(bytes, HEADER_BYTES, bytes.length);
		STPSegment segment = new STPSegment(seqNum, ackNum, flags, data);
		segment.setChecksum(checksum);
		return segment;
	}

	/*
	 * Manually serialize an STPSegment to a byte array. 
	 */
	public static byte[] serialize(STPSegment segment) {
    ByteBuffer data = ByteBuffer.allocate(HEADER_BYTES + segment.getData().length);
    data.putInt(segment.getSeqNum());
    data.putInt(segment.getAckNum());
    data.putShort(segment.getFlags());
    data.putShort(segment.getChecksum());
    data.put(segment.getData());
		return data.array();
	}

	/* 
	 * Calculates the 16 bit checksum for the given segment.
	 * This excludes the checksum value in the segment.
	 */
  public static short calculateChecksum(STPSegment s) {
  	return 0;
  	// ByteBuffer buff = ByteBuffer.allocate(HEADER_BYTES + s.getData().length);
  	// buff.putInt(s.getSeqNum());
  	// buff.putInt(s.getAckNum());
  	// buff.putShort(s.getFlags());
  	// buff.put(s.getData());
  	// buff.rewind();

  	// int checksum = 0;
  	// int overflow = 1 << 16;
  	// while(buff.remaining() > 1) {
			// checksum += buff.getShort();

  	// 	if((overflow & checksum) == overflow) {
  	// 		checksum += 1;
  	// 		checksum ^= overflow;
  	// 	}
  	// }
  	// return (short)~checksum;
  }
}