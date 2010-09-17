package ro.ulbsibiu.acaps.mapper.util;

import org.apache.log4j.Logger;

/**
 * Utility class for mathematical operations.
 * 
 * @author cipi
 * 
 */
public class MathUtils {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(MathUtils.class);

	private static final float MACHINE_EPSILON_FLOAT;

	static {
		float machEps = 1.0f;

		do {
			machEps /= 2.0f;
		} while ((float) (1.0 + (machEps / 2.0)) != 1.0);

		MACHINE_EPSILON_FLOAT = machEps;
	}

	private MathUtils() {
		;
	}

	/**
	 * Computes the machine's epsilon for the float data type. Refer to <a
	 * href="http://en.wikipedia.org/wiki/Machine_epsilon"
	 * >http://en.wikipedia.org/wiki/Machine_epsilon</a> for more details.
	 * 
	 * @return the machine epsilon
	 */
	public static float getMachineEpsilonFloat() {
		if (logger.isTraceEnabled()) {
			logger.trace("Machine epsilon is " + MACHINE_EPSILON_FLOAT);
		}
		return MACHINE_EPSILON_FLOAT;
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is approximately equal to <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean approximatelyEqual(float a, float b) {
		boolean apEqual = Math.abs(a - b) <= ((Math.abs(a) < Math.abs(b) ? Math.abs(b)
				: Math.abs(a)) * MACHINE_EPSILON_FLOAT);
		if (logger.isTraceEnabled()) {
			logger.trace(a + " approximately equal " + b + " = " + apEqual);
		}
		return apEqual;
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is essentially equal to <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean essentiallyEqual(float a, float b) {
		boolean esEqual = Math.abs(a - b) <= ((Math.abs(a) > Math.abs(b) ? Math.abs(b)
				: Math.abs(a)) * MACHINE_EPSILON_FLOAT);
		if (logger.isTraceEnabled()) {
			logger.trace(a + " essentially equal " + b + " = " + esEqual);
		}
		return esEqual;
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is definitely greater than
	 *         <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean definitelyGreaterThan(float a, float b) {
		boolean defGtThan = (a - b) > ((Math.abs(a) < Math.abs(b) ? Math.abs(b) : Math
				.abs(a)) * MACHINE_EPSILON_FLOAT);
		if (logger.isTraceEnabled()) {
			logger.trace(a + " definitely greater than " + b + " = " + defGtThan);
		}
		return defGtThan;
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is definitely less than <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean definitelyLessThan(float a, float b) {
		boolean defLsThan = (b - a) > ((Math.abs(a) < Math.abs(b) ? Math.abs(b) : Math
				.abs(a)) * MACHINE_EPSILON_FLOAT);
		if (logger.isTraceEnabled()) {
			logger.trace(a + " definitely less than " + b + " = " + defLsThan);
		}
		return defLsThan;
	}

}
