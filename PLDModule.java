import java.nio.*;
import java.io.*;
import java.net.*;
import java.util.*;

/* PLDModule */
public class PLDModule {
	private float pDrop;

	PLDModule(float pDrop) {
		this.pDrop = pDrop;
	}

	public int send(DatagramSocket socket, STPSegment segment, InetAddress address, int port) {
		byte[] s = segment.toByteArray();
		try {
			socket.send(new DatagramPacket(s, s.length, address, port));
		} catch(IOException e) {
			return 0;
		}
		return 1;
	}
}