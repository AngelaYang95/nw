import java.nio.*;
import java.io.*;
import java.util.*;

public class Logger {
	private File file;
	private PrintWriter printer;

	public Logger(String filename) {
		File file = new File(filename);
		try {
			file.createNewFile();
			printer = new PrintWriter(new FileOutputStream(file));
		} catch(IOException e) {
		}
	}

	public void log(Event e, long time) {
		printer.printf("%s %d\n", e.toString(), time);
		printer.flush();
	}

	public void close() {
		printer.close();
	}
}