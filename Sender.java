import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;

/**
 * STP sender that reads data from a given file and reliably
 * transfers this data to a receiver.
 */
public class Sender {
  public enum State {
    CONNECTED,
    CLOSING,
  }

	public final static int ESTIMATED_RTT = 500;
	public final static int DEV_RTT = 250;

	public static void main(String[] args) throws Exception {
		if (args.length < 14) {
			System.out.println("Missing arguments...");
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
		int seed = Integer.parseInt(args[13]);

		// Initialise sender stat values.
		int sBytes = 0, sSegments = 0, sTimeoutRXTs = 0, sFastRXTs = 0, sDupAcks = 0;

		// Initialise sender and Datagram socket for sending UDP packets.
		STPLogger logger = new STPLogger("Sender_log.txt");
    DatagramSocket socket = new DatagramSocket();
		PLDModule pld = new PLDModule(logger, socket, pDrop, pDuplicate, pCorrupt, pOrder, maxOrder, pDelay, maxDelay, seed);
    int nextSeqNum = 0, ackNum = 0;

    // Initiate handshake
		STPSegment outSegment = new STPSegment(nextSeqNum, ackNum, STPSegment.SYN_MASK, new byte[0]);
		byte[] outData = STPSegment.serialize(outSegment);
    socket.send(new DatagramPacket(outData, outData.length, ip, port));
    logger.log(outSegment, Event.SND);
    sSegments++;

    // Await SYNACK and acknowledge.
    DatagramPacket inPacket = new DatagramPacket(new byte[1500], 1500);
    socket.receive(inPacket);
    STPSegment inSegment = STPSegment.deserialize(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    nextSeqNum = inSegment.getAckNum();
    ackNum = inSegment.getSeqNum() + 1;
    logger.log(inSegment, Event.RCV);

    // Send final handshake ACK.
		outSegment = new STPSegment(nextSeqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
		outData = STPSegment.serialize(outSegment);
    socket.send(new DatagramPacket(outData, outData.length, ip, port));
    logger.log(outSegment, Event.SND);
    sSegments++;

    // Begin transferring file data.
    State state = State.CONNECTED;
		BufferedInputStream br = new BufferedInputStream(new FileInputStream(filename));
    List<STPSegment> window = new LinkedList<>();
		double estimatedRTT = ESTIMATED_RTT;
		double devRTT = DEV_RTT;
    int sendBase = nextSeqNum, dupAcks = 0;
    STPTimer timer = new STPTimer();
    STPSegment sampler = null;

    while(state == State.CONNECTED || (state == State.CLOSING && !window.isEmpty())) {

  		while(state == State.CONNECTED && nextSeqNum - sendBase < mws) {
  			byte[] data = new byte[Math.min(mss, mws - (nextSeqNum - sendBase))];
  			int bytesRead = br.read(data, 0, data.length);
  			if(bytesRead == -1) {
  				state = State.CLOSING;
  				break;
  			}
				data = Arrays.copyOfRange(data, 0, bytesRead);
				STPSegment segment = new STPSegment(nextSeqNum, ackNum, STPSegment.DATA_MASK, data);
				pld.send(segment, ip, port);
				window.add(segment);
				nextSeqNum += bytesRead;
				sBytes += bytesRead;
				sSegments++;
				if(sampler == null) {
					timer.restart();
					sampler = segment;
				}
  		}

    	try {
				socket.setSoTimeout((int)(estimatedRTT + 4 * devRTT));
	   		socket.receive(inPacket);
	   		inSegment = STPSegment.deserialize(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));

	   		if(sampler != null && sampler.getSeqNum() < inSegment.getAckNum()) {
	   			long sampleRTT = timer.getElapsedTime();
			    estimatedRTT = (1 - 0.125) * estimatedRTT + 0.125 * sampleRTT;
			    devRTT = (1 - 0.25) * devRTT + 0.25 * Math.abs(estimatedRTT - sampleRTT);
			    sampler = null;
	   		}

	   		if(inSegment.getAckNum() > sendBase) {
		    	Iterator<STPSegment> it = window.iterator();
			    while(it.hasNext() && it.next().getSeqNum() < inSegment.getAckNum()) {
	    			it.remove();
			    }
    			sendBase = inSegment.getAckNum();
					dupAcks = 0;
	    		logger.log(inSegment, Event.RCV);

	   		} else {
	   			dupAcks++;
	   			sDupAcks++;
	   			logger.log(inSegment, new Event[]{Event.RCV, Event.DA});

		   		if(dupAcks == 3) {
	 			 		STPSegment fastRXT = window.get(0);
		    		fastRXT.setRxt(true);
		    		pld.send(fastRXT, ip, port);
	    			sSegments++;
	    			sFastRXTs++;
		    		dupAcks = 0;
		    		sampler = null;
		   		}
	   		}
    	} catch(SocketTimeoutException e) {
  			window.get(0).setRxt(true);
  			pld.send(window.get(0), ip, port);
  			sTimeoutRXTs++;
  			sSegments++;
  			sampler = null;
    	}
    }
    System.out.println("Data transfer complete! Closing connection...");

    // Initiate connection teardown.
    outSegment = new STPSegment(nextSeqNum, ackNum, STPSegment.FIN_MASK, new byte[0]);
    outData = STPSegment.serialize(outSegment);
    socket.send(new DatagramPacket(outData, outData.length, ip, port));
    logger.log(outSegment, Event.SND);
    sSegments++;

		// Make sure it is an ACK for the fin.
    while(true) {
	    socket.receive(inPacket);
	    inSegment = STPSegment.deserialize(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
			logger.log(inSegment, Event.RCV);
			if(nextSeqNum < inSegment.getAckNum()) {
	    	nextSeqNum= inSegment.getAckNum();
				ackNum = inSegment.getSeqNum() + 1;
				break;
			}
    }

    // Await FIN packet from Receiver.
    socket.receive(inPacket);
    inSegment = STPSegment.deserialize(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    nextSeqNum= inSegment.getAckNum();
		ackNum = inSegment.getSeqNum() + 1;
		logger.log(inSegment, Event.RCV);

		// Acknowledge Receiver FIN packet.
		outSegment = new STPSegment(nextSeqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
		outData = STPSegment.serialize(outSegment);
    socket.send(new DatagramPacket(outData, outData.length, ip, port));
    logger.log(outSegment, Event.SND);
    sSegments++;

    // Close our connection.
    logger.logSenderStats(sBytes, sSegments, pld.getStats(), sTimeoutRXTs, sFastRXTs, sDupAcks);
    pld.close();
    socket.close();
    logger.close();
	}
}