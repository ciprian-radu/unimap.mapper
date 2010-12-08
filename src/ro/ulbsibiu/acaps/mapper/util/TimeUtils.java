package ro.ulbsibiu.acaps.mapper.util;

import org.apache.log4j.Logger;

import java.lang.management.*;

/**
 * Utility class for timing operations.
 * 
 * @author cipi
 * 
 */
public class TimeUtils {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(TimeUtils.class);

	private TimeUtils() {
		;
	}

	/**
	 * @return CPU time, in nanoseconds
	 */
	public static long getCpuTime() {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		if (!bean.isCurrentThreadCpuTimeSupported()) {
			logger.warn("The Java virtual machine doesn't support CPU time measurement for the current thread!");
		}
		return bean.isCurrentThreadCpuTimeSupported() ? bean
				.getCurrentThreadCpuTime() : 0L;
	}

	/**
	 * @return user time, in nanoseconds
	 */
	public static long getUserTime() {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		if (!bean.isCurrentThreadCpuTimeSupported()) {
			logger.warn("The Java virtual machine doesn't support CPU time measurement for the current thread!");
		}
		return bean.isCurrentThreadCpuTimeSupported() ? bean
				.getCurrentThreadUserTime() : 0L;
	}

	/**
	 * @return system time, in nanoseconds
	 */
	public static long getSystemTime() {
		return getCpuTime() - getUserTime();
	}
}