package ro.ulbsibiu.acaps.mapper.util;

import org.apache.log4j.Logger;

/**
 * Monitors the heap usage of the Java Virtual Machine. The user can specify
 * what code it wants to monitor. Typical usage of this class is shown in the
 * next example:
 * <p>
 * <code>
 * HeapUsageMonitor monitor = new HeapUsageMonitor();<br />
 * monitor.startMonitor();<br />
 * // put here the code to monitor<br />
 * monitor.stopMonitor();<br />
 * System.out.println("Average heap used : " + monitor.getAverageUsedHeap() + " Bytes");
 * </code>
 * </p>
 * 
 * An average of heap usage is computed (see {@link #getAverageUsedHeap()})
 * 
 * <p>
 * <b>Note: </b>The heap usage is checked with a period of (approximately) half
 * a second.
 * </p>
 * 
 * @see VisualHeapUsageMonitor
 * 
 * @author cipi
 * 
 */
public class HeapUsageMonitor extends Thread {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(HeapUsageMonitor.class);

	private static final long SLEEP_TIME = 500;

	private boolean running = false;

	private long totalUsedHeap = 0;

	private long usedHeapCounts = 0;

	private double averageUsedHeap = 0;

	@Override
	public void run() {
		while (running) {
			try {
				totalUsedHeap += MemoryUtils.getUsedHeapMemory();
				usedHeapCounts++;
				Thread.sleep(SLEEP_TIME);
			} catch (InterruptedException e) {
				logger.error(e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#start()
	 */
	@Override
	public synchronized void start() {
		running = true;
		super.start();
	}

	/**
	 * Starts this monitor. It is equivalent with {@link #start()}
	 */
	public void startMonitor() {
		start();
	}

	/**
	 * Stops this monitor
	 */
	public void stopMonitor() {
		running = false;
		if (usedHeapCounts > 0) {
			averageUsedHeap = totalUsedHeap * 1.0 / usedHeapCounts;
		}
	}

	/**
	 * @return the average used heap memory, in bytes
	 */
	public double getAverageUsedHeap() {
		logger.assertLog(!running,
				"This monitor is still running. Stop it first!");
		return averageUsedHeap;
	}

	public static void main(String[] args) {
		HeapUsageMonitor heapUsageMonitor = new HeapUsageMonitor();
		heapUsageMonitor.startMonitor();

		// between start and stop we have the code that we monitor
		String s = "";
		for (int i = 0; i < 1000000; i++) {
			s = Integer.toString(i);
			System.out.println(s);
		}

		heapUsageMonitor.stopMonitor();
		System.out.println("Average heap used : "
				+ heapUsageMonitor.getAverageUsedHeap() + " Bytes");
	}
}
