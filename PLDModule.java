import java.nio.*;
import java.io.*;
import java.net.*;
import java.util.*;

/* PLDModule */
public class PLDModule {

	// private Timer timer = new Timer();  // must close this.
	private STPSegment reorder;
	private DatagramSocket socket;
	private Random random;
	private float pDrop;
	private float pDuplicate;
	private float pCorrupt;
	private float pOrder;
	private Stat stats;

	// Statistics
	public class Stat {
		public Stat() {}
		public int dropped = 0;
		public int corrupted = 0;
		public int reordered = 0;
		public int duplicated = 0;
		public int delayed = 0;
		public int segments = 0;
	} 

	PLDModule(DatagramSocket socket, float pDrop, float pDuplicate, float pCorrupt) {
		this.socket = socket;
		this.pDrop = pDrop;
		this.pDuplicate = pDuplicate;
		this.pCorrupt = pCorrupt;
		this.random = new Random(50);
		this.stats = new Stat();
	}

	public Event send(STPSegment segment, InetAddress address, int port) throws IOException {
		byte[] s = segment.toByteArray();
		Event e = Event.SND;
		stats.segments++;

		if(random.nextFloat() < pDrop) {
			stats.dropped++;
			return Event.DROP;
		} 
		
		if(random.nextFloat() < pDuplicate) 
		{
			stats.duplicated++;
			socket.send(new DatagramPacket(s, s.length, address, port));
			e = Event.DUP;
		} else if(random.nextFloat() < pCorrupt) 
		{
			corruptPacket(segment);
			stats.corrupted++;
			e = Event.CORR;
		} else if(random.nextFloat() < pOrder) 
		{

		}

		socket.send(new DatagramPacket(s, s.length, address, port));
		return e;
	}

	public Stat getStats() {
		return stats;
	}

	/** Flips a random bit in the data segment */
	private void corruptPacket(STPSegment segment) {
		byte[] data = segment.getData();
		int pos = (int)(Math.random() * data.length);
		data[pos] = (byte)(1 - data[pos]);
		segment.setData(data);
	}
}