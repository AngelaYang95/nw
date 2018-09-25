import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

// Refactor to multi-thread.
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
		float pOrder = Float.parseFloat(args[9]);
		int maxOrder = Integer.parseInt(args[10]);
		float pDelay = Float.parseFloat(args[11]);
		int maxDelay = Integer.parseInt(args[12]);

		// Initialise sender and Datagram socket for sending UDP packets.
		STPLogger logger = new STPLogger("Sender_log.txt");
    DatagramSocket socket = new DatagramSocket();
		PLDModule pld = new PLDModule(logger, socket, pDrop, pDuplicate, pCorrupt, pOrder, maxOrder, pDelay, maxDelay);
    DatagramPacket inPacket = new DatagramPacket(new byte[STPSegment.HEADER_BYTES + mss], STPSegment.HEADER_BYTES+mss);
    int seqNum = 0, ackNum = 0;
    
    // Initiate handshake
		STPSegment outSegment = new STPSegment(seqNum, ackNum, STPSegment.SYN_MASK, new byte[0]);
    socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
    logger.log(outSegment, Event.SND);

    // Await SYNACK and acknowledge.
    socket.receive(inPacket);
    STPSegment inSegment = new STPSegment(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    seqNum = inSegment.getAckNum();
    ackNum = inSegment.getSeqNum() + 1;
    logger.log(inSegment, Event.RCV);

    // Send final handshake ACK.
		outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
    socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
    logger.log(outSegment, Event.SND);
    State state = State.CONNECTED;

    // Begin transferring file data.
		BufferedInputStream br = new BufferedInputStream(new FileInputStream(filename));
    List<STPSegment> window = new LinkedList<>();
		double estimatedRTT = 500;
		double devRTT = 250;
    int sendBase = seqNum;
    
    while(state == State.CONNECTED || !window.isEmpty()) {

    	// Read in mws of data into the send window.
    	while(state != State.CLOSING && seqNum - sendBase < mws) {
    		byte[] data = new byte[mss];
    		int bytesRead = br.read(data, 0, data.length);
    		if(bytesRead == -1) {
    			state = State.CLOSING;
    			br.close();
    			break;
    		}
    		data = Arrays.copyOfRange(data, 0, bytesRead);
    		window.add(new STPSegment(seqNum, ackNum, STPSegment.DATA_MASK, data));
    		seqNum += bytesRead;
    	}

    	// Send segments in the send window through the PLDModule.
    	// long sendTime = System.currentTimeMillis();
			STPTimer timer = new STPTimer();
			timer.restart();
    	for(STPSegment segment : window) {
	    	pld.send(segment, ip, port);
    	}

    	// Wait for acknowledgements from receiver.
    	int dupAcks = 0;
    	while(window.size() != 0) {
		    try {
		    	socket.setSoTimeout((int)(estimatedRTT + gamma * devRTT));
		   		socket.receive(inPacket);
		    } catch(SocketTimeoutException e) {
		    	Iterator<STPSegment> it = window.iterator();
			    while(it.hasNext()) it.next().setRxt(true);
		    	break;
		    }

		    inSegment = new STPSegment(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
		    if(inSegment.getAckNum() <= sendBase) { 	
					dupAcks++;
		    	logger.log(inSegment, new Event[]{Event.RCV, Event.DA});
		    	if(dupAcks == 3) {
		    		window.get(0).setRxt(true);
		    		pld.send(window.get(0), ip, port);
		    		dupAcks = 0;
		    	}
		    } else {
			    long sampleRTT = Math.max(timer.getElapsedTime(), 1);
			    estimatedRTT = (1 - 0.125) * estimatedRTT + 0.125 * sampleRTT;
			    devRTT = (1 - 0.25) * devRTT + 0.25 * Math.abs(sampleRTT - estimatedRTT);
			    
		    	Iterator<STPSegment> it = window.iterator();
			    while(it.hasNext() && it.next().getSeqNum() < inSegment.getAckNum()) {
	    			it.remove();
	    			sendBase = inSegment.getAckNum();
			    }
			    dupAcks = 0;
	    		logger.log(inSegment, Event.RCV);
		    }
    	}
    }
    System.out.println("Data transfer complete! Closing connection...");

    // Initiate connection teardown.
    outSegment = new STPSegment(seqNum, ackNum, STPSegment.FIN_MASK, new byte[0]);
    socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
    logger.log(outSegment, Event.SND);

    socket.receive(inPacket);
    inSegment = new STPSegment(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    seqNum = inSegment.getAckNum();
		ackNum = inSegment.getSeqNum() + 1;
		logger.log(inSegment, Event.RCV);

    // Await teardown from Receiver.
    socket.receive(inPacket);
    inSegment = new STPSegment(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    seqNum = inSegment.getAckNum();
		ackNum = inSegment.getSeqNum() + 1;
		logger.log(inSegment, Event.RCV);

		outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
    socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
    logger.log(outSegment, Event.SND);

    // Close our connection.
    logger.logStats(0, 0, pld.getStats(), 0, 0, 0);
    pld.close();
    socket.close();
    logger.close();
	}
}