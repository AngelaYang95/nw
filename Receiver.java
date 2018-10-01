import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;

/**
 * STP receiver that writes received packets data to a given file.
 */
public class Receiver {
  public final static int MAX_PACKET = 5000;
  public enum State {
    CONNECTED,
    CLOSING,
  }
  
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.out.println("Required arguments: port, filename");
      return;
    }
    int myPort = Integer.parseInt(args[0]);
    String filename = args[1];

    // Initialise receiver stat values.
    int sBytes = 0, sSegs = 0, sDataSegs = 0, sBitErrors = 0, sDupSegs = 0, sDupAcks = 0;
    STPLogger logger = new STPLogger("Receiver_log.txt");
    DatagramSocket socket = new DatagramSocket(myPort);

    // Await an SYN from sender.
    DatagramPacket inPacket = new DatagramPacket(new byte[MAX_PACKET], MAX_PACKET);
    socket.receive(inPacket);

    InetAddress ip = inPacket.getAddress();
    int port = inPacket.getPort();
    STPSegment inSegment = STPSegment.deserialize(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    int seqNum = 0;
    int ackNum = inSegment.getSeqNum() + 1;
    logger.log(inSegment, Event.RCV);
    sBytes += inSegment.getData().length;
    sSegs++;

    // Send back a SYNACK segment.
    STPSegment outSegment = new STPSegment(seqNum, ackNum, (short)(STPSegment.SYN_MASK | STPSegment.ACK_MASK), new byte[0]);
    byte[] outData = STPSegment.serialize(outSegment);
    socket.send(new DatagramPacket(outData, outData.length, ip, port));
    logger.log(outSegment, Event.SND);

    // Await an ACK from sender.
    socket.receive(inPacket);
    inSegment = STPSegment.deserialize(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
    seqNum = inSegment.getAckNum();
    logger.log(inSegment, Event.RCV);
    sBytes += inSegment.getData().length;
    sSegs++;

    // Three way handshake complete, so create file for incoming data.
    File file = new File(filename);
    FileOutputStream out = new FileOutputStream(file);
    try {
      file.createNewFile();
    } catch(IOException e) {
    }
    
    // Begin data transfer, wait for data packets.
    State state = State.CONNECTED;
    PriorityQueue<STPSegment> buffer = new PriorityQueue<>(10, new Comparator<STPSegment>() {
      /* Order from smallest to largest seqNum. */
      @Override
      public int compare(STPSegment o1, STPSegment o2) {
        return o1.getSeqNum() - o2.getSeqNum();
      }
    });

    while(true) {
      socket.receive(inPacket);
      inSegment = STPSegment.deserialize(Arrays.copyOfRange(inPacket.getData(), 0, inPacket.getLength()));
      sSegs++;

      // Discard corrupt segment.
      if(inSegment.getChecksum() != STPSegment.calculateChecksum(inSegment)) {
        logger.log(inSegment, new Event[]{Event.RCV, Event.CORR});
        sBitErrors++;
        continue;

      // Receiver teardown acknowledged by Sender. FINISH program.
      } else if(state == State.CLOSING && inSegment.getFlags() == STPSegment.ACK_MASK) {
        logger.log(inSegment, Event.RCV);
        break;

      // Sender has sent a FIN packet.
      } else if(inSegment.getFlags() == STPSegment.FIN_MASK) {
        logger.log(inSegment, Event.RCV);
        sBytes += inSegment.getData().length;
        ackNum += 1;

        // Acknowledge sender teardown.
        outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
        outData = STPSegment.serialize(outSegment);
        socket.send(new DatagramPacket(outData, outData.length, ip, port));
        logger.log(outSegment, Event.SND);
        out.close();
        
        // Begin Receiver teardown by sending FIN packet to Sender.
        state = State.CLOSING;
        outSegment = new STPSegment(seqNum, ackNum, STPSegment.FIN_MASK, new byte[0]);
        outData = STPSegment.serialize(outSegment);
        socket.send(new DatagramPacket(outData, outData.length, ip, port));
        logger.log(outSegment, Event.SND);

      // Duplicate packet received from Sender, discard and ack.
      } else if(inSegment.getSeqNum() < ackNum || buffer.contains(inSegment)) {
        logger.log(inSegment, Event.RCV);
        sBytes += inSegment.getData().length;
        sDupSegs++;
        sDataSegs++;

        outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
        outData = STPSegment.serialize(outSegment);
        socket.send(new DatagramPacket(outData, outData.length, ip, port));
        logger.log(outSegment, new Event[]{Event.SND, Event.DA});
        sDupAcks++;

      // Data packet received write inorder packets to file.
      } else if(inSegment.getSeqNum() == ackNum) {
        buffer.add(inSegment);
        logger.log(inSegment, Event.RCV);
        sBytes += inSegment.getData().length;
        sDataSegs++;

        while(!buffer.isEmpty()) {
          STPSegment s = buffer.peek();
          if(s.getSeqNum() != ackNum) {
            break;
          }
          try {
            out.write(s.getData());
            ackNum += s.getData().length;
            buffer.remove();
          } catch(IOException e) {

          }
        }
        outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
        outData = STPSegment.serialize(outSegment);
        socket.send(new DatagramPacket(outData, outData.length, ip, port));
        logger.log(outSegment, Event.SND);

      // Buffer out of order data packet.
      } else {
        sBytes += inSegment.getData().length;
        buffer.add(inSegment);
        logger.log(inSegment, Event.RCV);

        outSegment = new STPSegment(seqNum, ackNum, STPSegment.ACK_MASK, new byte[0]);
        outData = STPSegment.serialize(outSegment);
        socket.send(new DatagramPacket(outData, outData.length, ip, port));
        logger.log(outSegment, new Event[] {Event.SND, Event.DA});
        sDupAcks++;
      }
    }

    logger.logReceiverStats(sBytes, sSegs, sDataSegs, sBitErrors, sDupSegs, sDupAcks);
    socket.close();
    logger.close();
	}
}