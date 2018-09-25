/*
 * Timer for the Sender class.
 */
class STPTimer {
	long startTime = 0;

	public STPTimer() {}

	public void start() {
		startTime = System.currentTimeMillis();
	}

	public void restart() {
		startTime = System.currentTimeMillis();
	}

	public long getElapsedTime() {
		return System.currentTimeMillis() - startTime;
	}
}