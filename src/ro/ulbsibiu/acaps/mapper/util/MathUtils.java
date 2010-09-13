package ro.ulbsibiu.acaps.mapper.util;

/**
 * Utility class for mathematical operations.
 * 
 * @author cipi
 * 
 */
public class MathUtils {

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
		return MACHINE_EPSILON_FLOAT;
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is approximately equal to <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean approximatelyEqual(float a, float b) {
		return Math.abs(a - b) <= ((Math.abs(a) < Math.abs(b) ? Math.abs(b)
				: Math.abs(a)) * MACHINE_EPSILON_FLOAT);
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is essentially equal to <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean essentiallyEqual(float a, float b) {
		return Math.abs(a - b) <= ((Math.abs(a) > Math.abs(b) ? Math.abs(b)
				: Math.abs(a)) * MACHINE_EPSILON_FLOAT);
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is definitely greater than
	 *         <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean definitelyGreaterThan(float a, float b) {
		return (a - b) > ((Math.abs(a) < Math.abs(b) ? Math.abs(b) : Math
				.abs(a)) * MACHINE_EPSILON_FLOAT);
	}

	/**
	 * @param a
	 * @param b
	 * @return <tt>true</tt>, if <tt>a</tt> is definitely less than <tt>b</tt><br />
	 *         <tt>false, otherwise</tt>
	 */
	public static boolean definitelyLessThan(float a, float b) {
		return (b - a) > ((Math.abs(a) < Math.abs(b) ? Math.abs(b) : Math
				.abs(a)) * MACHINE_EPSILON_FLOAT);
	}

	public static void main(String[] args) {
		System.out.println("Machine epsilon: "
				+ MathUtils.getMachineEpsilonFloat());
	}

}
