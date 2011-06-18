package ro.ulbsibiu.acaps.mapper.ga.jmetal.base;

import ro.ulbsibiu.acaps.mapper.ga.jmetal.AlgorithmTracker;
import jmetal.base.Algorithm;

/**
 * Adds an {@link AlgorithmTracker} to {@link Algorithm}.
 * 
 * @author cipi
 *
 */
public abstract class TrackedAlgorithm extends Algorithm {

	protected AlgorithmTracker algorithmTracker;

	public void setAlgorithmTracker (AlgorithmTracker algorithmTracker) {
		this.algorithmTracker = algorithmTracker;
	}
}
