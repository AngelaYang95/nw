import java.nio.*;
import java.io.*;
import java.net.*;
import java.util.*;

/* PLDModule */
public class PLDModule {

	private DatagramSocket socket;
	private Random random;
	private STPLogger logger;
	private Timer timer = new Timer();

	private float pDrop;
	private float pDuplicate;
	private float pCorrupt;
	private float pOrder;
	private int maxOrder;
	private float pDelay;
	private int maxDelay;
	private STPSegment reorderSegment = null;
	private int reorderWait;
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

	PLDModule(STPLogger logger, DatagramSocket socket, float pDrop, float pDuplicate, float pCorrupt, float pOrder, 
			int maxOrder, float pDelay, int maxDelay) {
		this.logger = logger;
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

	/* 
	 * Mimics network turbulence and sends packet through UDP socket. 
   * Returns an Event. 
	 */
	public void send(STPSegment segment, InetAddress ip, int port) throws IOException {
		byte[] segmentBytes = segment.toByteArray();
		DatagramPacket packet = new DatagramPacket(segmentBytes, segmentBytes.length, ip, port);
		stats.segments++;

		// Send reordered packet.
		if(reorderSegment != null) { 
			if(reorderWait == 0) {
				segmentBytes = reorderSegment.toByteArray();
				socket.send(new DatagramPacket(segmentBytes, segmentBytes.length, ip, port));
				logger.log(reorderSegment, new Event[]{Event.SND, Event.RORD});
				reorderSegment = null;
			}
			reorderWait--;
		}

		if(random.nextFloat() < pDrop) {
			stats.dropped++;
			logger.log(segment, Event.DROP);
			return;
		} 
		if(random.nextFloat() < pDuplicate) {
			stats.duplicated++;
			socket.send(packet);
			logger.log(segment, Event.SND);
			socket.send(packet);
			logger.log(segment, Event.DUP);
			return;
		} 
		if(random.nextFloat() < pCorrupt) {
			stats.corrupted++;
			corruptPacket(packet);
			socket.send(packet);
			logger.log(segment, Event.CORR);
			return;
		}
		if(random.nextFloat() < pOrder && reorderSegment == null) {
			stats.reordered++;
			reorderSegment = segment;
			reorderWait = maxOrder;
			return;
		}
		if(random.nextFloat() < pDelay) {
			stats.delayed++;
			timer.schedule(new TimerTask() {
				public void run() {
					try {
						socket.send(packet);
						logger.log(segment, new Event[]{Event.SND, Event.DELY});
					} catch(IOException e) {
						System.out.println(e.getMessage());
					}
				}
			}, maxDelay);
		} else {
			socket.send(packet);
			logger.log(segment, Event.SND);
		}
	}

	public Stat getStats() {
		return stats;
	}

	public void close() {
		timer.cancel();
	}

	/** Flips a random bit in the data segment */
	private void corruptPacket(DatagramPacket packet) {
		byte[] data = packet.getData();
		int pos = (int)(Math.random() * data.length);
		data[pos] = (byte)(1 - data[pos]);
	}
}