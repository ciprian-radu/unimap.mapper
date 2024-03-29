/**
 * PseudoRandom.java
 *
 * @author Juan J. Durillo
 * @version 1.0
 *
 */
package jmetal.util;

/**
 * Class representing some randoms facilities
 */
public class PseudoRandom {

	// cradu: RandomGenerator is buggy: randomperc() method doesn't generate a number between 0.0 and 1.0 always
	
//  /**
//   * generator used to obtain the random values
//   */
//  private static RandomGenerator random = null;
  
  /**
   * other generator used to obtain the random values
   */
  private static java.util.Random randomJava = null;
             
  /** 
   * Constructor.
   * Creates a new instance of PseudoRandom.
   */
  private PseudoRandom() {
    if (randomJava == null){
      //this.random = new java.util.Random((long)seed);
//      random = new RandomGenerator(null);
      randomJava = new java.util.Random();            
    }
  } // PseudoRandom
  
	/**
	 * Constructor
	 * 
	 * @param seed
	 *            the random number generator seed (can be <tt>null</tt>, in
	 *            which case the system time is used as a seed)
	 */
	private PseudoRandom(Long seed) {
//		random = new RandomGenerator(seed);
		if (seed == null) {
			randomJava = new java.util.Random();
		} else {
			randomJava = new java.util.Random(seed);
		}
	}
	
	/**
	 * Reinitializes the random generator and sets a new seed for it.
	 * 
	 * @param seed
	 *            the seed (can be <tt>null</tt>, in which case the system time
	 *            is used as a seed)
	 */
	public static void setSeed(Long seed) {
		new PseudoRandom(seed);
	}
    
  /** 
   * Returns a random int value using the Java random generator.
   * @return A random int value.
   */
  public static int randInt() {
    if (randomJava == null) {
      new PseudoRandom();
    }
    return randomJava.nextInt();
  } // randInt
    
  /** 
   * Returns a random double value using the PseudoRandom generator.
   * Returns A random double value.
   */
  public static double randDouble() {
    if (randomJava == null) {
      new PseudoRandom();
    }
//    return random.rndreal(0.0,1.0);
    return randomJava.nextDouble();
  } // randDouble
    
  /** 
   * Returns a random int value between a minimum bound and maximum bound using
   * the PseudoRandom generator.
   * @param minBound The minimum bound.
   * @param maxBound The maximum bound.
   * Return A pseudo random int value between minBound and maxBound.
   */
  public static int randInt(int minBound, int maxBound) {
    if (randomJava == null) {
      new PseudoRandom();
    }
//    return random.rnd(minBound,maxBound);
    return minBound + randomJava.nextInt(maxBound-minBound+1);
  } // randInt
    
  /** Returns a random double value between a minimum bound and a maximum bound
   * using the PseudoRandom generator.
   * @param minBound The minimum bound.
   * @param maxBound The maximum bound.
   * @return A pseudo random double value between minBound and maxBound
   */
  public static double randDouble(double minBound, double maxBound) {
    if (randomJava == null) {
      new PseudoRandom();
    }
//    return random.rndreal(minBound,maxBound);
    return minBound + (maxBound - minBound)*randomJava.nextDouble();
  } // randDouble    
} // PseudoRandom
