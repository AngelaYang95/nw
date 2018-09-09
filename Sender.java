import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
		float pDuplicate = Float.parseFloat(args[7]);
		float pCorrupt = Float.parseFloat(args[8]);

		// Initialise sender and Datagram socket for sending UDP packets.
		Logger logger = new Logger("Sender_log.txt");
    DatagramSocket socket = new DatagramSocket();
		PLDModule pld = new PLDModule(socket, pDrop, pDuplicate, pCorrupt);
    DatagramPacket response = new DatagramPacket(new byte[STPSegment.HEADER_BYTES+mss], STPSegment.HEADER_BYTES+mss);
    STPSegment responseSegment;
    int seqNum = 0, ackNum = 0;
    
    // Initiate handshake
		State state = State.HANDSHAKE;
    socket.send(buildPacket(seqNum, ackNum, STPSegment.SYN_MASK, ip, port));
    System.out.println("Handshake..");

    // Await SYNACK and acknowledge.
    socket.receive(response);
    responseSegment = new STPSegment(Arrays.copyOfRange(response.getData(), 0, response.getLength()));
    seqNum = responseSegment.getAckNum();
    ackNum = responseSegment.getSeqNum() + 1;

    // Goes into data transfer part.
    socket.send(buildPacket(seqNum, ackNum, STPSegment.ACK_MASK, ip, port));
    state = State.CONNECTED;
    seqNum++;
    ackNum++;

    System.out.println("datering..");
    // Read data from file to buffer. DO as you go. 
    // you can only read up to MWS at a time.
    // Timer timer = new Timer();
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
    		window.add(new STPSegment(seqNum, ackNum, STPSegment.DATA_MASK, data));
    	} else {
    		// this is retransmission
    	}

    	// Send out packets left in window and log details.
    	long sendTime = System.currentTimeMillis();
    	for(STPSegment segment : window) {
    		System.out.println("Sending... seq=" + segment.getSeqNum() + " ack=" + segment.getAckNum());
    		segment.setAckNum(ackNum);
	    	Event e = pld.send(segment, ip, port);
	    	logger.log(e, (double)(sendTime/1000));
    	}

    	// Await for acknowledgement from receiver
    	while(true) {
		    try {
		    	socket.setSoTimeout((int)(estimatedRTT + gamma * devRTT));
		   		socket.receive(response);
		    } catch(SocketTimeoutException e) {
		    	// Increment retransmission count.
		    	System.out.println("Timeout retransmitting...");
		    	break;
		    }
		    // timer.cancel();

		    double sampleRTT = Math.max(Math.abs(System.currentTimeMillis() - sendTime), 1);
		    estimatedRTT = (1 - 0.125) * estimatedRTT + 0.125 * sampleRTT;
		    devRTT = (1 - 0.25) * devRTT + 0.25 * Math.abs(sampleRTT - estimatedRTT);

		    // Process received ack.
		    responseSegment = new STPSegment(Arrays.copyOfRange(response.getData(), 0, response.getLength()));

	    	System.out.println("comparing.." + seqNum + " --- " + responseSegment.getAckNum() + "----" + responseSegment.getSeqNum());
	    	if(seqNum < responseSegment.getAckNum()) {
	    		seqNum = responseSegment.getAckNum();
	    		ackNum = responseSegment.getSeqNum() + 1;
	    		window.remove(0);
	    	} else {
	    		// dupl ack received.
	    	}
	    	if(window.isEmpty()) {
	    		break;
	    	}
    	}
    }
    System.out.println("Data transfer complete! Closing connection...");

    // Send FIN and await acknowledgement.
    socket.send(buildPacket(seqNum, ackNum, STPSegment.FIN_MASK, ip, port));
    socket.receive(response);
    responseSegment = new STPSegment(Arrays.copyOfRange(response.getData(), 0, response.getLength()));
    seqNum = responseSegment.getAckNum();
		ackNum = responseSegment.getSeqNum() + 1;

		System.out.println("Ack waiiting...");
    // Await FIN and return ack.
    socket.receive(response);
    responseSegment = new STPSegment(Arrays.copyOfRange(response.getData(), 0, response.getLength()));
    seqNum = responseSegment.getAckNum();
		ackNum = responseSegment.getSeqNum() + 1;

    socket.send(buildPacket(seqNum, ackNum, STPSegment.ACK_MASK, ip, port));

    // Close our connection.
    socket.close();
    logger.close();
	}

	public static DatagramPacket buildPacket(int seqNum, int ackNum, short flags, InetAddress ip, int port) {
		STPSegment segment = new STPSegment(seqNum, ackNum, flags, new byte[0]);
		byte[] s = segment.toByteArray();
		return new DatagramPacket(s, s.length, ip, port);
	}
}