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

		int myPort = Integer.parseInt(args[0]);
		String filename = args[1];

		// Datagram socket for receiving and sending UDP packets.
    // Wait for SYN from sender. We assume that packets won't be dropped here.
    Logger logger = new Logger("Receiver_log.txt");
    DatagramSocket socket = new DatagramSocket(myPort);
    State state = State.HANDSHAKE;
    DatagramPacket handshakePacket = new DatagramPacket(new byte[1024], 1024);

    // Parse and sync sender information.
    socket.receive(handshakePacket);
    STPSegment responseSegment = new STPSegment(Arrays.copyOfRange(handshakePacket.getData(), 0, handshakePacket.getLength()));
    InetAddress ip = handshakePacket.getAddress();
    int port = handshakePacket.getPort();
    int seqNum = 10;
    int ackNum = responseSegment.getSeqNum() + 1;
    int mss = 500;

    File file = new File(filename);
    FileOutputStream out = new FileOutputStream(file);
    try {
    	file.createNewFile();
    } catch(IOException e) {
    }

    // Send back a synack and create the file.
		socket.send(buildPacket(seqNum, ackNum, STPSegment.SYN_MASK | STPSegment.ACK_MASK, ip, port));

		// Await an ACK from sender.
    socket.receive(handshakePacket);
    responseSegment = new STPSegment(Arrays.copyOfRange(handshakePacket.getData(), 0, handshakePacket.getLength()));
    seqNum = responseSegment.getAckNum();
    ackNum = responseSegment.getSeqNum() + 1;
    state = State.CONNECTED;

    // Begin DATA TRANSFER await data packets.
    DatagramPacket dataPacket = new DatagramPacket(new byte[STPSegment.HEADER_BYTES + mss], STPSegment.HEADER_BYTES + mss);
    while(state == State.CONNECTED) {
			socket.receive(dataPacket);

			responseSegment = new STPSegment(Arrays.copyOfRange(dataPacket.getData(), 0, dataPacket.getLength()));
	    if(responseSegment.getFlags() == STPSegment.FIN_MASK) {
    		state = State.CLOSING;
    		ackNum += 1;
	    } else if(responseSegment.getSeqNum() == ackNum) {
  			ackNum += responseSegment.getData().length;
  			try {
  				out.write(responseSegment.getData());
  			} catch(IOException e) {

  			}
	    }
	    socket.send(buildPacket(seqNum, ackNum, STPSegment.ACK_MASK, ip, port));
		}
    System.out.println("Transfer complete!");

		// Begin server CLOSING / teardown by sending fin.
		// Again we assume that teardown packets do not go through PLD.
		socket.send(buildPacket(seqNum, ackNum, STPSegment.FIN_MASK, ip, port));

		// Wait for ACK from sender and ACK back.
    socket.receive(new DatagramPacket(new byte[STPSegment.HEADER_BYTES], STPSegment.HEADER_BYTES));
		socket.close();
		out.close();
	}

	public static DatagramPacket buildPacket(int seqNum, int ackNum, int flags, InetAddress ip, int port) {
		STPSegment segment = new STPSegment(seqNum, ackNum, flags, new byte[0]);
		byte[] s = segment.toByteArray();
		return new DatagramPacket(s, s.length, ip, port);
	}
}