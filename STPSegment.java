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
    this.checksum = calculateChecksum();
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
	public void setData(byte[] data) { this.data = data; }

  private short calculateChecksum() {
  	ByteBuffer buff = ByteBuffer.wrap(toByteArray());
  	int checksum = 0;
  	int overflow = 1 << 16;

  	// This includes checksum... remove checksum.
  	while(buff.remaining() != 0) {
  		if(buff.remaining() == 1) {
  			checksum += (short)(buff.get()) << 8;
  		} else {
  			checksum += buff.getShort();
  		}

  		if((overflow & checksum) == overflow) {
  			checksum += 1;
  			checksum ^= overflow;
  		}
  	}
  	return (short)~checksum;
  }
}