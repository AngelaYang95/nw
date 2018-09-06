import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;

public class ReceiverOld {
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
    DatagramPacket handshakePacket = new DatagramPacket(new byte[100], 100);
    socket.receive(handshakePacket);
    STPSegment s = getSegment(handshakePacket);
    System.out.println("received.. " + s.getSeqNum() + " " + s.getFlags());
		socket.close();
	}

	public static STPSegment getSegment(DatagramPacket packet) throws IOException {
		// byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
    ByteArrayInputStream bStream = new ByteArrayInputStream(packet.getData());
		ObjectInputStream oStream = new ObjectInputStream(bStream);
		STPSegment segment = null;
		// System.out.println(" data is length " + data.length + " " + oStream.available());
		try {
			segment = (STPSegment) oStream.readObject();
		} catch (ClassNotFoundException e) {
			System.out.println("88");
			e.printStackTrace();
		} catch(java.io.EOFException e) {
			System.out.println("99" + segment);
			e.printStackTrace();
		}
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

	public static void writeToFile(String filename, byte[] data) throws IOException {
		// Create file to write to.
    Path file = FileSystems.getDefault().getPath(filename);
		try {
	    Files.createFile(file);
		} catch (FileAlreadyExistsException x) {

		} catch (IOException x) {
	    System.err.format("createFile error: %s%n", x);
		}

  	// Write data to file.
		Files.write(file, data);
	}
}