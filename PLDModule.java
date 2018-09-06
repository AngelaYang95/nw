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

	// Statistics
	private int dropped = 0;
	private int corrupted = 0;
	private int reordered = 0;
	private int duplicated = 0;
	private int delayed = 0;
	private int segments = 0;

	PLDModule(DatagramSocket socket, float pDrop) {
		this.socket = socket;
		this.pDrop = pDrop;
		this.random = new Random(50);
	}

	public Event send(STPSegment segment, InetAddress address, int port) throws IOException {
		byte[] s = segment.toByteArray();
		Event e = Event.SND;
		segments++;

		if(random.nextFloat() < pDrop) {
			dropped++;
			return Event.DROP;
		} 
		// if(random.nextFloat() < pDuplicate) {
		// 	duplicated++;
		// 	socket.send(new DatagramPacket(s, s.length, address, port));
		// } else if(random.nextFloat() < pCorrupt) {
		// 	// bit error
		// }

		socket.send(new DatagramPacket(s, s.length, address, port));
		return e;
	}
}