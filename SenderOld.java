import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;

public class SenderOld {
	public enum State {
		HANDSHAKE,
		CONNECTED,
		CLOSING,
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 5) {
			System.out.println("Missing arguments..");
			return;
		}

		// Parse parameters.
		InetAddress ip = InetAddress.getByName(args[0]);
		int port = Integer.parseInt(args[1]);
		String filename = args[2];
		int mws = Integer.parseInt(args[3]);
		int mss = Integer.parseInt(args[4]);
		int gamma = Integer.parseInt(args[5]);
		float pDrop = Float.parseFloat(args[6]);

		// Initialise sender and Datagram socket for sending UDP packets.
		PLDModule pld = new PLDModule(pDrop);
    DatagramSocket socket = new DatagramSocket();
    DatagramPacket response = new DatagramPacket(new byte[12], 12);
    STPSegment responseSegment;
    int seqNum = 0, ackNum = 0;
    
    // Initiate handshake
    System.out.println("Sending...");
    socket.send(buildPacket(seqNum, ackNum, 1, ip, port));

    System.out.println("DONE");
    // Close our connection.
    socket.close();
	}

	public static STPSegment getSegment(DatagramPacket packet) throws IOException {
		byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
    ByteArrayInputStream bStream = new ByteArrayInputStream(data);
		ObjectInputStream oStream = new ObjectInputStream(bStream);
		STPSegment segment = null;
		try {
			segment = (STPSegment) oStream.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return segment;
	}

	public static byte[] serialize(STPSegment segment) throws IOException {
		ByteArrayOutputStream bStream = new ByteArrayOutputStream();
		ObjectOutputStream oStream = new ObjectOutputStream(bStream);
		oStream.writeObject(segment);
		byte[] s = bStream.toByteArray();
		oStream.close();
		System.out.println("he...   " + s.length);
		return s;
	}

	public static DatagramPacket buildPacket(int seqNum, int ackNum, int flags, InetAddress ip, int port) throws IOException {
		STPSegment segment = new STPSegment(seqNum, ackNum, flags, new byte[0]);
		byte[] s = serialize(segment);
		return new DatagramPacket(s, s.length, ip, port);
	}
}