import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;

public class Sender {
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
    DatagramPacket response = new DatagramPacket(new byte[STPSegment.HEADER_BYTES+mss], STPSegment.HEADER_BYTES+mss);
    STPSegment responseSegment;
    int seqNum = 0, ackNum = 0;
    
    // Initiate handshake
		State state = State.HANDSHAKE;
    socket.send(buildPacket(seqNum, ackNum, STPSegment.SYN_MASK, ip, port));

    // Await SYNACK and acknowledge.
    socket.receive(response);
    seqNum = STPSegment.getAckNum(response.getData());
    ackNum = STPSegment.getSeqNum(response.getData()) + 1;

    // Goes into data transfer part.
    socket.send(buildPacket(seqNum, ackNum, STPSegment.ACK_MASK, ip, port));
    state = State.CONNECTED;
    seqNum++;
    ackNum++;

    // Read data from file to buffer. DO as you go. 
    // you can only read up to MWS at a time.
		BufferedInputStream br = new BufferedInputStream(new FileInputStream(filename));
    List<STPSegment> window = new LinkedList<>();
		double estimatedRTT = 500;
		double devRTT = 250;
    while(state == State.CONNECTED || (state == State.CLOSING && !window.isEmpty())) {

    	if(window.isEmpty()) {
    		byte[] data = new byte[Math.min(mws, mss)];
    		int bytesRead = br.read(data, 0, data.length);
    		if(bytesRead == -1) {
    			state = State.CLOSING;
    			br.close();
    			continue;
    		}
    		data = Arrays.copyOfRange(data, 0, bytesRead);
    		System.out.println("Bytes read are " + (char)data[0] + (char)data[1]);
    		window.add(new STPSegment(seqNum, ackNum, STPSegment.DATA_MASK, data));
    	}

    	// Send out packets left in window.
    	for(STPSegment segment : window) {
    		segment.setAckNum(ackNum);
	    	pld.send(socket, segment, ip, port);
    	}

    	// Await for acknowledgemnet from receiver.
    	long sendTime = System.currentTimeMillis();
	    try {
	    	socket.setSoTimeout((int)(estimatedRTT + gamma * devRTT));
	   		socket.receive(response);
	    } catch(SocketTimeoutException e) {
	    	System.out.println("Timeout retransmitting...");
	    	continue;
	    }
	    long sampleRTT = System.currentTimeMillis() - sendTime;
	    estimatedRTT = (1 - 0.125) * estimatedRTT + 0.125 * sampleRTT;
	    devRTT = (1 - 0.25) * devRTT + 0.25 * Math.abs(sampleRTT - estimatedRTT);

	    // Process received ack.
	    byte[] segment = response.getData();
    	if(seqNum < STPSegment.getAckNum(segment)) {
    		seqNum = STPSegment.getAckNum(segment);
    		ackNum = STPSegment.getSeqNum(segment) + 1;
    		window.remove(0);
    	}
    }
    System.out.println("Data transfer complete! Closing connection...");

    // Send FIN and await acknowledgement.
    socket.send(buildPacket(seqNum, ackNum, STPSegment.FIN_MASK, ip, port));
    socket.receive(response);
    seqNum = STPSegment.getAckNum(response.getData());
    ackNum = STPSegment.getSeqNum(response.getData()) + 1;

    // Await FIN and return ack.
    socket.receive(response);
    seqNum = STPSegment.getAckNum(response.getData());
    ackNum = STPSegment.getSeqNum(response.getData()) + 1;

    socket.send(buildPacket(seqNum, ackNum, STPSegment.ACK_MASK, ip, port));

    // Close our connection.
    socket.close();
	}

	public static STPSegment getSegment(DatagramPacket packet) throws IOException {
		byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
		ObjectInputStream oStream = new ObjectInputStream(new ByteArrayInputStream(data));
		STPSegment segment = null;
		// try {
		// 	segment = (STPSegment) oStream.readObject();
		// } catch (ClassNotFoundException e) {
		// 	e.printStackTrace();
		// }
		return segment;
	}

	public static byte[] serialize(STPSegment segment) throws IOException {
		ByteArrayOutputStream bStream = new ByteArrayOutputStream();
		ObjectOutputStream oStream = new ObjectOutputStream(bStream);
		oStream.writeObject(segment);
		byte[] s = bStream.toByteArray();
		// oStream.close();
		// bStream.close();
		return s;
	}

	public static DatagramPacket buildPacket(int seqNum, int ackNum, int flags, InetAddress ip, int port) {
		STPSegment segment = new STPSegment(seqNum, ackNum, flags, new byte[0]);
		byte[] s = segment.toByteArray();
		return new DatagramPacket(s, s.length, ip, port);
	}
}