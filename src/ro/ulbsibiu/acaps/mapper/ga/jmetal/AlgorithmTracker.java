package ro.ulbsibiu.acaps.mapper.ga.jmetal;

import jmetal.base.Solution;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.TrackedAlgorithm;

/**
 * Allows tracking the intermediate solutions produced by an algorithm.
 * 
 * @author cipi
 * 
 */
public interface AlgorithmTracker {

	/**
	 * This method will be called each time an intermediate solution will be
	 * obtained by a {@link TrackedAlgorithm}
	 * 
	 * @param parameterName
	 *            the name of the algorithm parameter which is varied when the
	 *            intermediate solutions are tracked (e.g.: generations number
	 *            for a genetic algorithm)
	 * @param parameterValue
	 *            the value of the varied parameter, when this intermediate
	 *            solution was obtained
	 * @param solution
	 *            the intermediate solution
	 */
	public void processIntermediateSolution(String parameterName,
			String parameterValue, Solution solution);
}
