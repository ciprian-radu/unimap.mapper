package ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import jmetal.base.Solution;
import jmetal.base.operator.crossover.Crossover;
import jmetal.base.solutionType.PermutationSolutionType;
import jmetal.base.variable.Permutation;
import jmetal.util.JMException;
import jmetal.util.PseudoRandom;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.mapper.sa.Core;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;

/**
 * jMetal {@link Crossover} operator that generates two children from the
 * similarities of their two parents. For each parent, a distance vector is
 * computed. Then, a similarity is performed between the two vectors. The cores
 * that are similar (i.e. they are equally far from the communicating cores, in
 * both parents) are kept in the same positions in the 2 generated children. The
 * rest of the cores are greedy mapped (starting with the cores that have the
 * highest value in the distance vector). Child 1 is formed from parent 1 and
 * child 2 is made from parent 2. Essentially, using the similarity function,
 * both children try to inherit what's common for both their parents.<br />
 * How good the similarity is may be controlled through MAX_SIMILARITY_DISTANCE.
 * This is a constant that says how distant away may the similar cores be from
 * their communicating cores.
 * <p>
 * <b>NOTE</b>: the type of those variables must be VariableType_.Permutation.
 * </p>
 * 
 * @author cradu
 */
public abstract class MappingSimilarityCrossover extends Crossover {

	private static final int MAX_SIMILARITY_DISTANCE = Integer.MAX_VALUE;
//	private static final int MAX_SIMILARITY_DISTANCE = 2;
//	private static final int MAX_SIMILARITY_DISTANCE = 1;
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(MappingSimilarityCrossover.class);

	private static Class<?> PERMUTATION_SOLUTION = PermutationSolutionType.class;
	
	private Core[] cores;
	
	private NodeType[] nodes;
	
	/** the distinct cores with which each core communicates directly */
	private Set<Integer>[] coreNeighbors;
	
	/** the distinct nodes with which each node communicates directly (through a single link) */
	private Set<Integer>[] nodeNeighbors;

	/**
	 * Constructor
	 */
	public MappingSimilarityCrossover() {
		if (logger.isDebugEnabled()) {
			logger.debug("Maximum similarity distance is set to " + MAX_SIMILARITY_DISTANCE);
		}
	}

	public void setCores(Core[] cores) {
		this.cores = cores;
	}

	public void setNodes(NodeType[] nodes) {
		this.nodes = nodes;
	}

	public void setCoreNeighbors(Set<Integer>[] coreNeighbors) {
		this.coreNeighbors = coreNeighbors;
	}

	public void setNodeNeighbors(Set<Integer>[] nodeNeighbors) {
		this.nodeNeighbors = nodeNeighbors;
	}
	
	/**
	 * Computes the number oh hops between two NoC nodes (requires NoC topology
	 * knowledge)
	 * 
	 * @param node1
	 *            the first NoC node
	 * @param node2
	 *            the second NoC node
	 * @return the distance between the two NoC nodes
	 */
	public abstract int computeDistance(int node1, int node2);

	private int[] computeDistanceVector(int[] chromosome) {
		int[] d = new int[cores.length];
		
		if (logger.isDebugEnabled()) {
			logger.debug("Computing the distance vector for chromosome " + Arrays.toString(chromosome));
		}
		
		for (int i = 0; i < chromosome.length; i++) {
			// the number of cores might be smaller than the number of nodes
			if (chromosome[i] != -1) {
				// the distinct cores with which core "chromosome[i]"
				// communicates directly
				Set<Integer> cNeighbors = coreNeighbors[chromosome[i]];
				int sum = 0;
				for (Integer cNeighbor : cNeighbors) {
					int cNeighborNode = -1;
					for (int j = 0; j < chromosome.length; j++) {
						if (cNeighbor == chromosome[j]) {
							cNeighborNode = j;
						}
					}
					logger.assertLog(cNeighborNode != -1,
							"Couldn't determine on what NoC node is core "
									+ cNeighbor);
					sum += computeDistance(i, cNeighborNode);
				}
				d[chromosome[i]] = sum;
				if (logger.isDebugEnabled()) {
					logger.debug("Core " + chromosome[i] + " (NoC node " + i
							+ ") has a distance D[" + chromosome[i] + "] = "
							+ d[chromosome[i]]);
				}
			}
		}
		
		return d;
	}
	
	private int[] computeSimilarityVector(int[] d1, int[] d2) {
		logger.assertLog(d1 != null && d2 != null && d1.length == d2.length, "Invalid distance vectors!");
		
		int[] s = new int[d1.length];
		
		for (int i = 0; i < s.length; i++) {
			if (d1[i] == d2[i]) {
				if (logger.isDebugEnabled()) {
					logger.debug("Found similarity for core " + i + "; distance = " + d1[i]);
					logger.debug("This core communicates with " + coreNeighbors[i].size() + " cores");
					logger.debug("With a maximum similarity distance of " + MAX_SIMILARITY_DISTANCE + 
							", the maximum allowed distance is " + MAX_SIMILARITY_DISTANCE * coreNeighbors[i].size());
				}
				if (MAX_SIMILARITY_DISTANCE == Integer.MAX_VALUE 
						|| d1[i] <= MAX_SIMILARITY_DISTANCE * coreNeighbors[i].size()) {
					s[i] = 1;
				} else {
					s[i] = 0;
				}
			} else {
				s[i] = 0;
			}
			if (logger.isDebugEnabled()) {
				if (s[i] == 1) {
					logger.debug("Similarity detected for core " + i);
				}
			}
		}
		
		if (logger.isTraceEnabled()) {
			logger.trace("Computed similarity vector: " + Arrays.toString(s));
		}
		
		return s;
	}
	
	private int countSimilarityZeros(int[] s) {
		int c = 0;
		
		for (int i = 0; i < s.length; i++) {
			if (s[i] == 0) {
				c++;
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("The similarity vector " + Arrays.toString(s) + " has " + c + " zeros");
		}
		
		return c;
	}
	
	private void greedyMapChild(int[] child, int[] parent, int[] d, int[] s) {
		Arrays.fill(child, -1);
		// mapped array keeps track of the mapped cores
		boolean[] mapped = new boolean[cores.length];
		Arrays.fill(mapped, false);
		int mappedCores = 0;
		
		for (int i = 0; i < s.length; i++) {
			if (s[i] == 1 && parent[i] != -1) {
				child[parent[i]] = i;
				if (logger.isDebugEnabled()) {
					logger.debug("Core " + i + " is premapped due to similarity function");
				}
				mapped[i] = true;
				mappedCores++;
			}
		}
		
		int maxDistance = 0;
		for (int i = 0; i < d.length; i++) {
			if (!mapped[i] && d[i] > maxDistance) {
				maxDistance = d[i];
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Starting greedy mapping from a distance of " + maxDistance + " between cores");
		}
		
		int currentDistance = maxDistance;
		while (mappedCores < cores.length) {
			if (logger.isDebugEnabled()) {
				logger.debug("There are still " + (cores.length - mappedCores) + " cores to map");
			}
			List<Integer> coresToMap = new ArrayList<Integer>();
			for (int i = 0; i < d.length; i++) {
				if (!mapped[i] && d[i] == currentDistance) {
					coresToMap.add(i);
					if (logger.isDebugEnabled()) {
						logger.debug("Core " + i + " has distance " + currentDistance);
					}
				}
			}
			if (coresToMap.size() > 0) {
				if (logger.isDebugEnabled()) {
					logger.debug("Mapping cores with distance " + currentDistance);
				}
				
				final boolean[] mappedCopy = mapped;
				// sorting cores based on how many of their neighbors (i.e. communicating cores) are already mapped
				Collections.sort(coresToMap, new Comparator<Integer>() {

					@Override
					public int compare(Integer c1, Integer c2) {
						Set<Integer> c1Neighbors = coreNeighbors[c1];
						int c1PlacedNeighbors = 0;
						for (Integer c1Neighbor : c1Neighbors) {
							if (mappedCopy[c1Neighbor]) {
								c1PlacedNeighbors++;
							}
						}
						Set<Integer> c2Neighbors = coreNeighbors[c2];
						int c2PlacedNeighbors = 0;
						for (Integer c2Neighbor : c2Neighbors) {
							if (mappedCopy[c2Neighbor]) {
								c2PlacedNeighbors++;
							}
						}
						return new Integer(c1PlacedNeighbors).compareTo(new Integer(c2PlacedNeighbors));
					}
					
				});
				
				for (int i = coresToMap.size() - 1; i >= 0; i--) {
					if (logger.isDebugEnabled()) {
						logger.debug("Mapping core " + coresToMap.get(i));
					}
//					int nodeToUse = -1;
					int minDistance = Integer.MAX_VALUE;
					int[] distances = new int[child.length];
					Arrays.fill(distances, -1); // a distance of -1 means the core cannot be placed on that node
					for (int j = 0; j < child.length; j++) {
						if (child[j] == -1) {
							Set<Integer> cNeighbors = coreNeighbors[coresToMap.get(i)];
							int distance = 0; // a distance of 0 means the core neighbors are not yet mapped
							for (Integer cNeighbor : cNeighbors) {
								if (mapped[cNeighbor]) {
									int node = -1;
									for (int k = 0; k < child.length; k++) {
										if (child[k] == cNeighbor) {
											node = k;
											break;
										}
									}
									if (node == -1) {
										System.err.println();
									}
									logger.assertLog(node != -1,
											"Couldn't determine on what NoC node is placed core "
													+ cNeighbor);
									distance += computeDistance(j, node);
								}
							}
							if (distance < minDistance) {
								minDistance = distance;
//								nodeToUse = j;
							}
							distances[j] = distance;
						}
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Distances from communicating cores for core "
								+ coresToMap.get(i)
								+ ": "
								+ Arrays.toString(distances));
						logger.debug("(a distance of -1 means the core cannot be placed on that node)");
						logger.debug("(a distance of 0 means the core neighbors are not yet mapped)");
						logger.debug("Minimum distance is " + minDistance);
						logger.debug("Randomly picking a NoC node that gives the minimum distance");
					}
					List<Integer> minDistances = new ArrayList<Integer>();
					for (int j = 0; j < distances.length; j++) {
						if (distances[j] == minDistance) {
							minDistances.add(j);
						}
					}
					int rand = PseudoRandom.randInt(0, minDistances.size() - 1);
					int nodeToUse = minDistances.get(rand);
					child[nodeToUse] = coresToMap.get(i);
					mapped[coresToMap.get(i)] = true;
					mappedCores++;
					if (logger.isDebugEnabled()) {
						logger.debug("Core " + coresToMap.get(i) + " was mapped onto NoC node " + nodeToUse);
					}
				}
				
			}
			currentDistance--;
		}
	}
	
	/**
	 * Perform the crossover operation
	 * 
	 * @param probability
	 *            Crossover probability
	 * @param parent1
	 *            The first parent
	 * @param parent2
	 *            The second parent
	 * @return An array containing the two offsprings
	 * @throws JMException
	 */
	public Solution[] doCrossover(double probability, Solution parent1,
			Solution parent2) throws JMException {

		Solution[] offspring = new Solution[2];

		offspring[0] = new Solution(parent1);
		offspring[1] = new Solution(parent2);

		if (PERMUTATION_SOLUTION.isAssignableFrom(parent1.getType().getClass())
				&& PERMUTATION_SOLUTION.isAssignableFrom(parent1.getType().getClass())) {
			if (logger.isDebugEnabled()) {
				logger.trace("Starting crossover");
			}
			
			int parent1Vector[] = ((Permutation) parent1.getDecisionVariables()[0]).vector_;
			int parent2Vector[] = ((Permutation) parent2.getDecisionVariables()[0]).vector_;
			int offspring1Vector[] = ((Permutation) offspring[0].getDecisionVariables()[0]).vector_;
			int offspring2Vector[] = ((Permutation) offspring[1].getDecisionVariables()[0]).vector_;

			if (PseudoRandom.randDouble() < probability) {
				for (int i = 0; i < offspring1Vector.length; i++) {
					offspring1Vector[i] = offspring2Vector[i] = -1;
				}
				int[] d1 = computeDistanceVector(parent1Vector);
				int[] d2 = computeDistanceVector(parent2Vector);
				int[] s = computeSimilarityVector(d1, d2);
				int zeros = countSimilarityZeros(s);
				if (zeros <= 1) {
					double fitness1 = parent1.getObjective(0);
					double fitness2 = parent2.getObjective(0);
					if (logger.isDebugEnabled()) {
						logger.debug("Parent 1 has fitness " + fitness1);
						logger.debug("Parent 2 has fitness " + fitness2);
					}
					if (MathUtils.approximatelyEqual((float)fitness1, (float)fitness2)) {
						for (int i = 0; i < offspring1Vector.length; i++) {
							offspring1Vector[i] = parent1Vector[i];
						}
						for (int i = 0; i < offspring2Vector.length; i++) {
							offspring2Vector[i] = parent2Vector[i];
						}
						if (logger.isDebugEnabled()) {
							logger.debug("Generated two children. The first is identical with one parent and the second is identical with the other parent");
						}
					} else {
						if (MathUtils.definitelyLessThan((float)fitness1, (float)fitness2)) {
							for (int i = 0; i < offspring1Vector.length; i++) {
								offspring1Vector[i] = parent1Vector[i];
							}
							for (int i = 0; i < offspring2Vector.length; i++) {
								offspring2Vector[i] = parent1Vector[i];
							}
							if (logger.isDebugEnabled()) {
								logger.debug("Generated two twin children, both identical to parent 1 (because is has a lower fitness than parent 2)");
							}
						} else {
							for (int i = 0; i < offspring1Vector.length; i++) {
								offspring1Vector[i] = parent2Vector[i];
							}
							for (int i = 0; i < offspring2Vector.length; i++) {
								offspring2Vector[i] = parent2Vector[i];
							}
							if (logger.isDebugEnabled()) {
								logger.debug("Generated two twin children, both identical to parent 2 (because is has a lower fitness than parent 1)");
							}
						}
					}
				} else {
					// maintain the similar cores and map the rest using greedy mapping
					greedyMapChild(offspring1Vector, parent1Vector, d1, s);
					greedyMapChild(offspring2Vector, parent2Vector, d2, s);
				}
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Crossover probability determined no crossover will be performed this time");
				}
			}
		} else {
			String message = "At least one of the two parents are not permutation type individuals";
			logger.error(message);
			throw new JMException(message);
		}
		return offspring;
	}

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
			logger.error("The solutions are not of the right type. The type should be '"
					+ PERMUTATION_SOLUTION
					+ "', but "
					+ parents[0].getType()
					+ " and " + parents[1].getType() + " are obtained");
		}

		crossoverProbability = (Double) getParameter("probability");

		if (parents.length < 2) {
			String message = "This crossover operator needs two parents";
			logger.error(message);
			throw new JMException(message);
		} else if (crossoverProbability == null) {
			String message = "This crossover operator needs two parents";
			logger.error(message);
			throw new JMException(message);
		}

		Solution[] offspring = doCrossover(crossoverProbability.doubleValue(),
				parents[0], parents[1]);

		return offspring;
	}

}
