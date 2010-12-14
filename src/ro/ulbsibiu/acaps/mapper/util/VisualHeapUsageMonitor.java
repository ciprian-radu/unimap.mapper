package ro.ulbsibiu.acaps.mapper.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

import com.sun.tools.visualvm.charts.ChartFactory;
import com.sun.tools.visualvm.charts.SimpleXYChartDescriptor;
import com.sun.tools.visualvm.charts.SimpleXYChartSupport;

/**
 * Monitors the heap usage of the Java Virtual Machine (like <a
 * href="https://visualvm.dev.java.net/">VisualVM</a> does). The user can
 * specify what code it wants to monitor. Typical usage of this class is shown
 * in the next example:
 * <p>
 * <code>
 * VisualHeapUsageMonitor monitor = new VisualHeapUsageMonitor(1024, 768);<br />
 * monitor.start();<br />
 * // put here the code to monitor<br />
 * byte[] averageHeapMemoryChart = monitor.saveImageAsByteArray();<br />
 * monitor.stop();<br />
 * </code>
 * </p>
 * 
 * Besides generating a chart with heap usage (1024 x 768 size in the above
 * example), an average of heap usage is also computed (see
 * {@link #getAverageUsedHeap()})
 * 
 * <p>
 * <b>Note: </b>The heap usage is checked with a period of (approximately) half
 * a second.
 * </p>
 * 
 * @see HeapUsageMonitor
 * 
 * @author cipi
 * 
 */
public class VisualHeapUsageMonitor extends JPanel {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(VisualHeapUsageMonitor.class);

	/** automatically generated serial version UID */
	private static final long serialVersionUID = -7144443142180420991L;

	private static final long SLEEP_TIME = 500;

	private SimpleXYChartSupport support;

	private ChartGenerator generator;

	private JFrame frame;

	private int width;

	private int height;

	/**
	 * Constructor
	 * 
	 * @param width
	 *            the width of the chart
	 * @param height
	 *            the height of the chart
	 */
	public VisualHeapUsageMonitor(int width, int height) {
		this.width = width;
		this.height = height;
		setPreferredSize(new Dimension(width, height));
		createModels();
		setLayout(new BorderLayout());
		add(support.getChart(), BorderLayout.CENTER);
	}

	private void createModels() {
		SimpleXYChartDescriptor descriptor = SimpleXYChartDescriptor.decimal(0,
				true, Integer.MAX_VALUE);

		descriptor.addLineFillItems("Heap size");
		descriptor.addLineFillItems("Used heap");

		descriptor.setDetailsItems(new String[] { "Size", "Max", "Used" });
		descriptor.setChartTitle("Heap");
		// descriptor.setXAxisDescription("<html><i>time</i></html>");
		// descriptor.setYAxisDescription("<html><i>Bytes</i></html>");

		support = ChartFactory.createSimpleXYChart(descriptor);

		generator = new ChartGenerator(support);
	}

	/**
	 * @return the average used heap memory, in bytes
	 */
	public double getAverageUsedHeap() {
		return generator.getAverageUsedHeap();
	}

	/**
	 * Starts the monitor
	 */
	public void start() {
		frame = new JFrame("Heap usage monitor");
		frame.add(this);
		frame.pack();
		// if the frame is not visible, then some text from the chart is not
		// saved in the chart image
		frame.setVisible(true);
		generator.startGenerator();
	}

	/**
	 * Stops the monitor
	 */
	public void stop() {
		generator.stopGenerator();
		frame.dispose();
	}

	private BufferedImage createImage() {
		try {
			logger.debug("Waiting "
					+ SLEEP_TIME
					+ " ms before creating the chart image (to better avoid getting an empty chart when the monitoring period is very low)");
			Thread.sleep(SLEEP_TIME);
		} catch (InterruptedException e) {
			logger.error(e);
		}
		BufferedImage bi = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		paint(g);
		return bi;
	}

	/**
	 * Creates a new File instance by converting the given pathname string into
	 * an abstract pathname. If the given string is the empty string, then the
	 * result is the empty abstract pathname. Then a heap usage chart image is
	 * save into the created file, in PNG format.
	 * 
	 * @param pathname
	 *            a pathname string
	 */
	public void saveImage(String pathname) {
		File outputfile = new File(pathname);
		try {
			ImageIO.write(createImage(), "png", outputfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the chart image, as a byte array
	 */
	public byte[] saveImageAsByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ImageIO.write(createImage(), "png", baos);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return baos.toByteArray();
	}

	/**
	 * The chart generator. It also computes the average heap usage.
	 * 
	 * @author cipi
	 * 
	 */
	private static class ChartGenerator extends Thread {
		/**
		 * Logger for this class
		 */
		private static final Logger logger = Logger
				.getLogger(ChartGenerator.class);

		private SimpleXYChartSupport support;

		private boolean running = false;

		private long totalUsedHeap = 0;

		private long usedHeapCounts = 0;

		private double averageUsedHeap = 0;

		public ChartGenerator(SimpleXYChartSupport support) {
			this.support = support;
		}

		public double getAverageUsedHeap() {
			return averageUsedHeap;
		}

		public void startGenerator() {
			running = true;
			start();
		}

		public void stopGenerator() {
			running = false;
			if (usedHeapCounts > 0) {
				averageUsedHeap = totalUsedHeap * 1.0 / usedHeapCounts;
			}
		}

		public void run() {
			while (running) {
				try {
					long[] values = new long[2];
					values[0] = MemoryUtils.getCommitedHeapMemory();
					long usedHeap = MemoryUtils.getUsedHeapMemory();
					this.totalUsedHeap += usedHeap;
					usedHeapCounts++;
					values[1] = usedHeap;
					support.addValues(System.currentTimeMillis(), values);
					support.updateDetails(new String[] { values[0] + " B",
							MemoryUtils.getMaxHeapMemory() + " B",
							values[1] + " B" });
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
					logger.error(e);
				}
			}
		}
	}

	public static void main(String[] args) {
		final VisualHeapUsageMonitor heapUsageMonitor = new VisualHeapUsageMonitor(1024,
				768);
		heapUsageMonitor.start();

		// between start and stop we have the code that we monitor
		String s = "";
		for (int i = 0; i < 1000000; i++) {
			s = Integer.toString(i);
			System.out.println(s);
		}

		heapUsageMonitor.saveImage("heap-usage.png");
		heapUsageMonitor.stop();
		System.out.println("Average used heap: "
				+ heapUsageMonitor.getAverageUsedHeap() + " Bytes");

	}

}