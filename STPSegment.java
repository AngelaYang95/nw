import java.nio.*;
import java.io.*;
import java.util.*;

/*
 * This is a utility class for parsing STPSegment.
 */
public class STPSegment implements Serializable {
	final static int HEADER_BYTES = 12;

	final static short SYN_MASK = 1;
	final static short ACK_MASK = 2;
	final static short DATA_MASK = 4;
	final static short FIN_MASK = 8;

	private int seqNum;
	private int ackNum;
	private short flags;
	private short checksum = 0;
	private byte[] data;
	private boolean rxt = false;

	public STPSegment(byte[] segment) {
		ByteBuffer buff = ByteBuffer.wrap(segment);
		this.seqNum = buff.getInt();
		this.ackNum = buff.getInt();
		this.flags = buff.getShort();
		this.checksum = buff.getShort();
		this.data = Arrays.copyOfRange(segment, HEADER_BYTES, segment.length);
	}

	public STPSegment(int seqNum, int ackNum, short flags, byte[] data) {
    this.seqNum = seqNum;
    this.ackNum = ackNum;
    this.flags = flags;
    this.data = data;
    this.checksum = calculateChecksum(this);
	}

	public byte[] toByteArray() {
    ByteBuffer segment = ByteBuffer.allocate(HEADER_BYTES + data.length);
    segment.putInt(seqNum);
    segment.putInt(ackNum);
    segment.putShort(flags);
    segment.putShort(checksum);
    segment.put(data);
		return segment.array();
	}

	public int getSeqNum() { return seqNum; }
	public int getAckNum() { return ackNum; }
	public short getFlags() { return flags; }
	public short getChecksum() { return checksum; }
	public byte[] getData() { return data; }

	public void setSeqNum(int n) { seqNum = n; }
	public void setAckNum(int n) { ackNum = n; }
	public void setFlags(short n) { flags = n; }
	public void setChecksum(short n) { checksum = n; }
	public void setData(byte[] data) { this.data = data; }

	public boolean isRxt() { return rxt; }
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
	 * Calculates the 16 bit checksum for the given segment.
	 * This excludes the checksum value in the segment.
	 */
  public static short calculateChecksum(STPSegment s) {
  	ByteBuffer buff = ByteBuffer.allocate(HEADER_BYTES + s.getData().length);
  	buff.putInt(s.getSeqNum());
  	buff.putInt(s.getAckNum());
  	buff.putShort(s.getFlags());
  	buff.put(s.getData());
  	buff.rewind();

  	int checksum = 0;
  	int overflow = 1 << 16;
  	while(buff.remaining() > 1) {
			checksum += buff.getShort();

  		if((overflow & checksum) == overflow) {
  			checksum += 1;
  			checksum ^= overflow;
  		}
  	}
  	return (short)~checksum;
  }
}