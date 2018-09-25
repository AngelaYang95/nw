import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;

public class Receiver {
	public enum State {
		HANDSHAKE,
		CONNECTED,
		CLOSING,
	}

	public static void main(String[] args) throws Exception {
    // Check and parse command line arguments.
		if (args.length != 2) {
			System.out.println("Required arguments: port, filename");
			return;
		}

    // Initialise 
		int myPort = Integer.parseInt(args[0]);
		String filename = args[1];
    STPLogger logger = new STPLogger("Receiver_log.txt");
    DatagramSocket socket = new DatagramSocket(myPort);
    State state = State.HANDSHAKE;

    // Wait for SYN from Sender and sync sender information.
    DatagramPacket inPacket = new DatagramPacket(new byte[1500], 1500);
    socket.receive(inPacket);

    STPSegment inSegment = new STPSegment(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    InetAddress ip = inPacket.getAddress();
    int port = inPacket.getPort();
    int seqNum = 0;
    int ackNum = inSegment.getSeqNum() + 1;
    int mss = 500;
    int mws = 500;
    logger.log(inSegment, Event.RCV);

    // Send back a SYNACK segment.
    STPSegment outSegment = new STPSegment(seqNum, ackNum, (short)(STPSegment.SYN_MASK | STPSegment.ACK_MASK), new byte[0]);
    socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
    logger.log(outSegment, Event.SND);

    // Await an ACK from sender.
    socket.receive(inPacket);
    inSegment = new STPSegment(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    seqNum = inSegment.getAckNum();
    logger.log(inSegment, Event.RCV);

    state = State.CONNECTED;

    // Three way handshake complete, so create file for incoming data.
    File file = new File(filename);
    FileOutputStream out = new FileOutputStream(file);
    try {
      file.createNewFile();
    } catch(IOException e) {
    }
    
    // Begin data transfer, wait for data packets.
    PriorityQueue<STPSegment> buffer = new PriorityQueue<>((int)(mws/mss) + 1, new Comparator<STPSegment>() {
      /* Order from smallest to largest seqNum. */
      @Override
      public int compare(STPSegment o1, STPSegment o2) {
        return o1.getSeqNum() - o2.getSeqNum();
      }
    });
    while(state == State.CONNECTED) {
      DatagramPacket dataPacket = new DatagramPacket(new byte[STPSegment.HEADER_BYTES + mss], STPSegment.HEADER_BYTES + mss);
			socket.receive(dataPacket);
      inSegment = new STPSegment(Arrays.copyOfRange(dataPacket.getData(), 0, dataPacket.getLength()));

      // Dispose corrupt packet.
      if(inSegment.getChecksum() != STPSegment.calculateChecksum(inSegment)) {
        logger.log(inSegment, new Event[]{Event.RCV, Event.CORR});
        continue;
      }

      // Close connection if FIN packet received from sender.
      if(inSegment.getFlags() == STPSegment.FIN_MASK) {
        logger.log(inSegment, Event.RCV);
        ackNum += 1;
        break;
      }

      if(inSegment.getSeqNum() < ackNum || buffer.contains(inSegment)) {
        logger.log(inSegment, new Event[]{Event.RCV, Event.DUP});
      } else {
        buffer.add(inSegment);
        logger.log(inSegment, Event.RCV);
      }

      // Flush buffer for all consecutive inorder packets.
      boolean isDupAck = (inSegment.getSeqNum() != ackNum);
      while(!buffer.isEmpty()) {
        STPSegment s = buffer.peek();
        if(s.getSeqNum() != ackNum) {
          break;
        }
        try {
          out.write(s.getData());
        } catch(IOException e) {
          System.out.println(e.getMessage());
        }
        ackNum += s.getData().length;
        buffer.remove();
      }

      // Send back a cumulative acknowledgment.
      outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
      socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
      if(isDupAck) {
        logger.log(outSegment, new Event[]{Event.SND, Event.DA});
      } else {
        logger.log(outSegment, Event.SND);
      }
    }

    // Acknowledge sender's FIN packet.
    outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
    socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
    logger.log(outSegment, Event.SND);
    out.close();
    System.out.println("Transfer complete!");

    // Begin Receiver teardown by sending FIN packet to Sender.
    outSegment = new STPSegment(seqNum, ackNum, STPSegment.FIN_MASK, new byte[0]);
    socket.send(new DatagramPacket(outSegment.toByteArray(), STPSegment.HEADER_BYTES, ip, port));
    logger.log(outSegment, Event.SND);

    // Wait for finaly ACK from sender.
    socket.receive(new DatagramPacket(new byte[STPSegment.HEADER_BYTES], STPSegment.HEADER_BYTES));
    socket.close();
    logger.close();
	}
}