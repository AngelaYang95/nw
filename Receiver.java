import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;

public class Receiver {
	enum State {
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
    DatagramSocket socket = new DatagramSocket(myPort);
    State state = State.HANDSHAKE;
    DatagramPacket handshakePacket = new DatagramPacket(new byte[STPSegment.HEADER_BYTES], STPSegment.HEADER_BYTES);
    socket.receive(handshakePacket);

    // Parse and sync sender information.
    InetAddress ip = handshakePacket.getAddress();
    int port = handshakePacket.getPort();
    int seqNum = 10;
    int ackNum = STPSegment.getSeqNum(handshakePacket.getData()) + 1;
    int mss = 500;
    
    // Send back a synack and create the file.
		socket.send(buildPacket(seqNum, ackNum, STPSegment.SYN_MASK | STPSegment.ACK_MASK, ip, port));
    Path file = FileSystems.getDefault().getPath(filename);
		try {
	    Files.createFile(file);
		} catch (FileAlreadyExistsException x) {
			// Don't do anything.
		} catch (IOException x) {
	    System.err.format("createFile error: %s%n", x);
		}

		// Await an ACK from sender.
    socket.receive(handshakePacket);
    seqNum = STPSegment.getAckNum(handshakePacket.getData());
    ackNum = STPSegment.getSeqNum(handshakePacket.getData()) + 1;
    state = State.CONNECTED;

    // Begin DATA TRANSFER await data packets.
    DatagramPacket dataPacket = new DatagramPacket(new byte[STPSegment.HEADER_BYTES + mss], STPSegment.HEADER_BYTES + mss);
    while(state == State.CONNECTED) {
			socket.receive(dataPacket);

			byte[] segment = dataPacket.getData();
	    if(STPSegment.getFlags(segment) == STPSegment.FIN_MASK) {
    		state = State.CLOSING;
    		ackNum += 1;
	    } else if(STPSegment.getSeqNum(segment) == ackNum) {
	    	byte[] data = STPSegment.getData(segment, STPSegment.HEADER_BYTES, dataPacket.getLength());
  			ackNum += data.length;
  			Files.write(file, data);
	    }
	    socket.send(buildPacket(seqNum, ackNum, STPSegment.ACK_MASK, ip, port));
		}
    System.out.println("Transfer complete!");

		// Begin server CLOSING / teardown by sending fin.
		// Again we assume that teardown packets do not go through PLD.
		socket.send(buildPacket(seqNum, ackNum, STPSegment.FIN_MASK, ip, port));

		// Wait for ACK from sender.
    socket.receive(new DatagramPacket(new byte[STPSegment.HEADER_BYTES], STPSegment.HEADER_BYTES));

		socket.close();
	}

	public static DatagramPacket buildPacket(int seqNum, int ackNum, int flags, InetAddress ip, int port) {
		STPSegment segment = new STPSegment(seqNum, ackNum, flags, new byte[0]);
		byte[] s = segment.toByteArray();
		return new DatagramPacket(s, s.length, ip, port);
	}
}