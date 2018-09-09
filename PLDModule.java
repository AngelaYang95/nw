import java.nio.*;
import java.io.*;
import java.net.*;
import java.util.*;

/* PLDModule */
public class PLDModule {

	// private Timer timer = new Timer();  // must close this.
	private DatagramSocket socket;
	private Random random;
	private float pDrop;
	private float pDuplicate;
	private float pCorrupt;
	private float pOrder;
	private int maxOrder;
	private float pDelay;
	private int maxDelay;
	private DatagramPacket reorder = null;
	private int reorderCount;
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

	PLDModule(DatagramSocket socket, float pDrop, float pDuplicate, float pCorrupt, float pOrder, 
			int maxOrder, float pDelat, int maxDelay) {
		this.socket = socket;
		this.pDrop = pDrop;
		this.pDuplicate = pDuplicate;
		this.pCorrupt = pCorrupt;
		this.pOrder = pOrder;
		this.maxOrder = maxOrder;
		this.pDelay = pDelay;
		this.maxDelay = maxDelay;
		this.random = new Random(50);
		this.stats = new Stat();
	}

	public Event send(STPSegment segment, InetAddress address, int port) throws IOException {
		byte[] s = segment.toByteArray();
		stats.segments++;

		if(reorder != null) {
			reorderCount++;
			if(reorderCount == maxOrder) {
				System.out.println("Sending reordered " + reorderCount);
				socket.send(reorder);
				reorderCount = 0;
				reorder = null;
			}
		}

		if(random.nextFloat() < pDrop) {
			stats.dropped++;
			return Event.DROP;
		} 

		if(random.nextFloat() < pDuplicate) {
			stats.duplicated++;
			socket.send(new DatagramPacket(s, s.length, address, port));
			socket.send(new DatagramPacket(s, s.length, address, port));
			return Event.DUP;
		} 

		if(random.nextFloat() < pCorrupt) {
			stats.corrupted++;
			DatagramPacket p = new DatagramPacket(s, s.length, address, port);
			corruptPacket(p);
			socket.send(p);
			return Event.CORR;
		}

		if(random.nextFloat() < pOrder && reorder == null) {
			System.out.println("---- Reordering " + segment.getSeqNum());
			stats.reordered++;
			reorder = new DatagramPacket(s, s.length, address, port);
			reorderCount = 0;
			return Event.RORD;
		}

		if(random.nextFloat() < pDelay) {
			return Event.DELY;
		}

		// Regular send event.
		System.out.println("Sending reordered " + segment.getSeqNum());
		socket.send(new DatagramPacket(s, s.length, address, port));
		return Event.SND;
	}

	public Stat getStats() {
		return stats;
	}

	/** Flips a random bit in the data segment */
	private void corruptPacket(DatagramPacket packet) {
		byte[] data = packet.getData();
		int pos = (int)(Math.random() * data.length);
		data[pos] = (byte)(1 - data[pos]);
	}
}