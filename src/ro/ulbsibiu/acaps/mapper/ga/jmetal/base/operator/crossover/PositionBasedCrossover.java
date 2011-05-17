package ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover;

import java.util.Arrays;
import java.util.Properties;
import jmetal.base.*;
import jmetal.base.operator.crossover.Crossover;
import jmetal.base.variable.*;
import jmetal.util.Configuration;
import jmetal.util.JMException;
import jmetal.util.PseudoRandom;

/**
 * This class allows to apply a Position based crossover operator using two
 * parent solutions. It is a jMetal {@link Crossover} operator. NOTE: the type
 * of those variables must be VariableType_.Permutation.
 * 
 * @author shaikat
 */
public class PositionBasedCrossover extends Crossover {

	/** automatically generated serial version UID */
	private static final long serialVersionUID = 6065180546012402023L;

	private static Class<?> PERMUTATION_SOLUTION;

	/**
	 * Constructor
	 */
	public PositionBasedCrossover() {
		try {
			PERMUTATION_SOLUTION = Class
					.forName("jmetal.base.solutionType.PermutationSolutionType");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // catch
	} // PositionBasedCrossover

	/**
	 * Constructor
	 */
	public PositionBasedCrossover(Properties properties) {
		this();
	} // PositionBasedCrossover

	/**
	 * Perform the crossover operation
	 * 
	 * @param probability
	 *            Crossover probability
	 * @param parent1
	 *            The first parent
	 * @param parent2
	 *            The second parent
	 * @return An array containig the two offsprings
	 * @throws JMException
	 */
	public Solution[] doCrossover(double probability, Solution parent1,
			Solution parent2) throws JMException {

		Solution[] offspring = new Solution[2];

		offspring[0] = new Solution(parent1);
		offspring[1] = new Solution(parent2);

		if (PERMUTATION_SOLUTION.isAssignableFrom(parent1.getType().getClass())
				&& PERMUTATION_SOLUTION.isAssignableFrom(parent1.getType().getClass())) {

			int permutationLength;

			permutationLength = ((Permutation) parent1.getDecisionVariables()[0])
					.getLength();

			int parent1Vector[] = ((Permutation) parent1.getDecisionVariables()[0]).vector_;
			int parent2Vector[] = ((Permutation) parent2.getDecisionVariables()[0]).vector_;
			int offspring1Vector[] = ((Permutation) offspring[0]
					.getDecisionVariables()[0]).vector_;
			int offspring2Vector[] = ((Permutation) offspring[1]
					.getDecisionVariables()[0]).vector_;

			if (PseudoRandom.randDouble() < probability) {

				for (int i = 0; i < offspring1Vector.length; i++) {
					offspring1Vector[i] = offspring2Vector[i] = Integer.MIN_VALUE;
				}

				// 25 percent of the gene is used as positions
				int numberOfPositions = (int) ((permutationLength / 100.0) * 25);

				// PermutationUtility obj = new PermutationUtility();
				// int[] setOfPositions = obj.intPermutation(numberOfPositions);

				int[] setOfPositions = new int[numberOfPositions];

				for (int i = 0; i < setOfPositions.length; i++)
					setOfPositions[i] = Integer.MIN_VALUE;

				for (int i = 0; i < setOfPositions.length; i++) {
					int number;
					// be sure that position is not repeated
					boolean track;
					do {
						track = false;
						number = PseudoRandom.randInt(0, permutationLength - 1);
						for (int k = 0; k < setOfPositions.length; k++)
							if (number == setOfPositions[k]) {
								track = true;
								break;
							}

					} while (track);

					setOfPositions[i] = number;

				}

				int[] tempParent = new int[permutationLength];

				tempParent = Arrays.copyOf(parent2Vector, parent2Vector.length);

				/*
				 * copy the content of the specific position(setOfPosition) from
				 * parent1 to child1. Also set Integer.MIN_VALUE in position of parent2
				 * (tempParent) where the content of the parent1 of the specific
				 * position (setOfPosition) is matched
				 */
				for (int i = 0; i < setOfPositions.length; i++) {
					offspring1Vector[setOfPositions[i]] = parent1Vector[setOfPositions[i]];
					int j;
					for (j = 0; j < tempParent.length; j++) {
						if (tempParent[j] == parent1Vector[setOfPositions[i]]) {
							tempParent[j] = Integer.MIN_VALUE;
							break;
						}
					}

				}
				/*
				 * starting from left to right of parent2(tempParent), copy
				 * content from parent2 to the rest of the position of child1 by
				 * skipping Integer.MIN_VALUE
				 */
				for (int i = 0; i < tempParent.length; i++) {

					if (tempParent[i] == Integer.MIN_VALUE)
						continue;
					else {
						for (int j = 0; j < offspring1Vector.length; j++) {
							if (offspring1Vector[j] != Integer.MIN_VALUE)
								continue;
							else {
								offspring1Vector[j] = tempParent[i];
								break;
							}
						}
					}

				}
				// child1 finished

				// child2 start

				for (int i = 0; i < setOfPositions.length; i++)
					setOfPositions[i] = Integer.MIN_VALUE;

				for (int i = 0; i < setOfPositions.length; i++) {
					int number;
					// be sure that position is not repeated
					boolean track;
					do {
						track = false;
						number = PseudoRandom.randInt(0, permutationLength - 1);
						for (int k = 0; k < setOfPositions.length; k++)
							if (number == setOfPositions[k]) {
								track = true;
								break;
							}

					} while (track);

					setOfPositions[i] = number;

				}

				tempParent = Arrays.copyOf(parent1Vector, parent1Vector.length);

				for (int i = 0; i < setOfPositions.length; i++) {
					offspring2Vector[setOfPositions[i]] = parent2Vector[setOfPositions[i]];
					int j;
					for (j = 0; j < tempParent.length; j++) {
						if (tempParent[j] == parent2Vector[setOfPositions[i]]) {
							tempParent[j] = Integer.MIN_VALUE;
							break;
						}
					}

				}

				for (int i = 0; i < tempParent.length; i++) {

					if (tempParent[i] == Integer.MIN_VALUE)
						continue;
					else {
						for (int j = 0; j < offspring2Vector.length; j++) {
							if (offspring2Vector[j] != Integer.MIN_VALUE)
								continue;
							else {
								offspring2Vector[j] = tempParent[i];
								break;
							}
						}
					}
				}

			}// if
		}// if
		else {
			Configuration.logger_
					.severe("PositionBasedCrossover.doCrossover: invalid type");
			Class<?> cls = java.lang.String.class;
			String name = cls.getName();
			throw new JMException("Exception in " + name + ".doCrossover()");
		}// else
		return offspring;
	}// doCrossover

	/**
	 * Executes the operation
	 * 
	 * @param object
	 *            An object containing an array of two solutions
	 * @throws JMException
	 */
	public Object execute(Object object) throws JMException {
		Solution[] parents = (Solution[]) object;
		Double crossoverProbability = null;

		if (!PERMUTATION_SOLUTION.isAssignableFrom(parents[0].getType().getClass())
				|| !PERMUTATION_SOLUTION.isAssignableFrom(parents[1].getType().getClass())) {

			Configuration.logger_
					.severe("PositionBasedCrossover.execute: the solutions "
							+ "are not of the right type. The type should be '"
							+ PERMUTATION_SOLUTION + "', but "
							+ parents[0].getType() + " and "
							+ parents[1].getType() + " are obtained");
		}

		// crossoverProbability = (Double)parameters_.get("probability");
		crossoverProbability = (Double) getParameter("probability");

		if (parents.length < 2) {
			Configuration.logger_
					.severe("PositionBasedCrossover.execute: operator needs two "
							+ "parents");
			Class<?> cls = java.lang.String.class;
			String name = cls.getName();
			throw new JMException("Exception in " + name + ".execute()");
		} else if (crossoverProbability == null) {
			Configuration.logger_
					.severe("PositionBasedCrossover.execute: probability not "
							+ "specified");
			Class<?> cls = java.lang.String.class;
			String name = cls.getName();
			throw new JMException("Exception in " + name + ".execute()");
		}

		Solution[] offspring = doCrossover(crossoverProbability.doubleValue(),
				parents[0], parents[1]);

		return offspring;
	} // execute

}
