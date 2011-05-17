package ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.mutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jmetal.base.Solution;
import jmetal.base.operator.mutation.Mutation;
import jmetal.base.variable.Permutation;
import jmetal.util.JMException;
import jmetal.util.PseudoRandom;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.mapper.osa.OptimizedSimulatedAnnealingMapper;
import ro.ulbsibiu.acaps.mapper.sa.Core;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;

/**
 * jMetal {@link Mutation} operator that performs a swapping like
 * {@link OptimizedSimulatedAnnealingMapper} does.
 * 
 * @author cradu
 * 
 */
public class OsaMutation extends Mutation {

	/** automatically generated serial version UID */
	private static final long serialVersionUID = 3791163756585378601L;
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(OsaMutation.class);

	private Core[] cores;
	
	private NodeType[] nodes;
	
	/** the distinct cores with which each core communicates directly */
	private Set<Integer>[] coreNeighbors;
	
	/** the distinct nodes with which each node communicates directly (through a single link) */
	private Set<Integer>[] nodeNeighbors;
	
	/**
	 * the total data communicated by the cores
	 */
	private double[] coreToCommunication;
	
	/** the total amount of data communicated by all cores */
	private long totalToCommunication;
	
	/** for every core, the (from and to) communication probability density function */
	private double[][] coresCommunicationPDF;
	
	private double initialTemperature;
	
	private double temperature;
	
	/** how many mutations were made */
	private int mutations = 0;

	/**
	 * Default constructor
	 */
	public OsaMutation() {
		;
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

	public void setInitialTemperature(double initialTemperature) {
		this.initialTemperature = initialTemperature;
		this.temperature = initialTemperature;
	}

	public void setCoreToCommunication(double[] coreToCommunication) {
		this.coreToCommunication = coreToCommunication;
	}

	public void setTotalToCommunication(long totalToCommunication) {
		this.totalToCommunication = totalToCommunication;
	}

	public void setCoresCommunicationPDF(double[][] coresCommunicationPDF) {
		this.coresCommunicationPDF = coresCommunicationPDF;
	}

	/**
	 * Performs the operation
	 * 
	 * @param probability
	 *            mutation probability
	 * @param solution
	 *            the solution to mutate
	 * @throws JMException
	 */
	public void doMutation(double probability, Solution solution)
			throws JMException {
		try {
			if (solution.getDecisionVariables()[0].getVariableType() == Class
					.forName("jmetal.base.variable.Permutation")) {

				int permutation[] = ((Permutation) solution.getDecisionVariables()[0]).vector_;

				if (PseudoRandom.randDouble() < probability) {
					logger.trace("Performing mutation");
					int[] movedNodes = makeAttractionMove();
					int node1 = movedNodes[0];
					int node2 = movedNodes[1];
					// make the swap available in the permutation
					int temp = permutation[node1];
					permutation[node1] = permutation[node2];
					permutation[node2] = temp;
					
					if (mutations == getNumberOfIterationsPerTemperature()) {
						logger.debug("Decreasing temperature because the number of mutations reached "
								+ mutations
								+ " (the number of iterations per temperature level)");
						decreaseTemperature();
						mutations = 0;
					} else {
						mutations++;
					}
				} else {
					logger.trace("Mutation is not performed");
				}
				
			} else {
				logger.error("invalid type " + ""
						+ solution.getDecisionVariables()[0].getVariableType());

				throw new JMException("Exception in "
						+ OsaMutation.class.getName() + ".doMutation()");
			}
		} catch (ClassNotFoundException e) {
			logger.error(e);
		}
	}

	/**
	 * Executes the operation
	 * 
	 * @param object
	 *            An object containing the solution to mutate
	 * @return an object containing the mutated solution
	 * @throws JMException
	 */
	public Object execute(Object object) throws JMException {
		Solution solution = (Solution) object;

		Double probability = (Double) getParameter("probability");
		if (probability == null) {
			logger.error("Probability not specified");
			throw new JMException("Exception in " + OsaMutation.class.getName()
					+ ".execute()");
		}

		this.doMutation(probability.doubleValue(), solution);
		return solution;
	}
	
	private int getNumberOfIterationsPerTemperature() {
		return (nodes.length * (nodes.length - 1)) / 2 - ((nodes.length - cores.length - 1) * (nodes.length - cores.length)) / 2;
	}
	
	public void decreaseTemperature() {
		// geometric temperature schedule (with ratio q = 0.9)
		temperature = 0.9 * temperature;
		logger.info("Temperature was decreased to " + temperature);
	}
	
	/**
	 * Randomly chooses a core based on the probability distribution function
	 * determined by the communications among cores
	 * 
	 * @return the chosen core
	 */
	private int selectCore() {
		int core = -1;
		double p = uniformRandomVariable();
		double sum = 0;
		for (int i = 0; i < cores.length; i++) {
//			sum += coreToCommunication[i] / totalToCommunication;
			
//			sum += (meanPDF + temperature * (coreToCommunication[i] - meanPDF)) / totalToCommunication;
			
			// as the temperature decreases, the probabilities equalize more and more
			sum += ((totalToCommunication * 1.0 / cores.length) + (temperature / initialTemperature)
					* (coreToCommunication[i] - (totalToCommunication * 1.0 / cores.length)))
					/ totalToCommunication;
			if (MathUtils.definitelyLessThan((float)p, (float)sum) 
					|| MathUtils.approximatelyEqual((float)p, (float)sum)) {
				core = i;
				break; // essential!
			}
		}
		return core;
	}
	
	private int[] makeAttractionMove() {
		int node1;
		int node2;

		// do {
		// node1 = (int) uniformIntegerRandomVariable(0, nodesNumber - 1);
		// } while ("-1".equals(nodes[node1].getCore()));
		//
		// int core1 = Integer.valueOf(nodes[node1].getCore());

		int core1 = selectCore();
		if (core1 == -1) {
			logger.fatal("Unable to select any core for moving!");
			System.exit(-1);
		}
		node1 = cores[core1].getNodeId();

		if (logger.isDebugEnabled()) {
			logger.debug("Selected node " + node1 + " for moving. It has core "
					+ core1);
			logger.debug("Node " + node1 + " communicates with nodes "
					+ nodeNeighbors[node1]);
			logger.debug("Core " + core1 + " communicates with cores "
					+ coreNeighbors[core1]);
		}

		double[] core1CommunicationPDF = coresCommunicationPDF[core1];
		int core2 = -1;
		double p = uniformRandomVariable();
		double sum = 0;
		for (int i = 0; i < core1CommunicationPDF.length; i++) {
			sum += core1CommunicationPDF[i];
			if (MathUtils.definitelyLessThan((float) p, (float) sum)
					|| MathUtils.approximatelyEqual((float) p, (float) sum)) {
				core2 = i;
				break; // essential!
			}
		}
		if (core2 == -1) {
			if (MathUtils.approximatelyEqual((float) sum, 0)) {
				core2 = (int) uniformIntegerRandomVariable(0, cores.length - 1);
				if (logger.isDebugEnabled()) {
					logger.debug("Core "
							+ core1
							+ " doesn't communicate with any core. It will be swapped with core "
							+ core2 + " (randomly chosen)");
				}
			} else {
				logger.fatal("Unable to determine a core with which core " + core1
						+ " will swap");
			}
		}
		int core2Node = cores[core2].getNodeId();
		List<Integer> core1AllowedNodes = new ArrayList<Integer>(
				nodeNeighbors[core2Node]);
		core1AllowedNodes.remove(new Integer(node1));

		if (core1AllowedNodes.size() == 0) {
			node2 = node1;
			logger.warn("No nodes are allowed for core " + core1
					+ ". We pretend we make a move by swapping node " + node1
					+ " with node " + node2);
		} else {
			int i = (int) uniformIntegerRandomVariable(0,
					core1AllowedNodes.size() - 1);
			node2 = core1AllowedNodes.get(i);

			// node2 = -1;
			// double[] core2CommunicationPDF = coresCommunicationPDF[core2];
			// double min = Float.MAX_VALUE; // needs to be float, not double
			// for (int i = 0; i < core1AllowedNodes.size(); i++) {
			// Integer core1AllowedNode = core1AllowedNodes.get(i);
			// String core = nodes[core1AllowedNode].getCore();
			// if (MathUtils.definitelyLessThan(
			// (float) core2CommunicationPDF[cores[Integer
			// .valueOf(core)].getCoreId()], (float) min)) {
			// min = core2CommunicationPDF[cores[Integer.valueOf(core)]
			// .getCoreId()];
			// node2 = core1AllowedNode;
			// }
			// }

			if (logger.isDebugEnabled()) {
				logger.debug("Core " + core1 + " will be moved from node "
						+ node1 + " to the allowed node " + node2);
			}
		}
		logger.assertLog(
				node1 != -1 && node2 != -1 && node1 != node2,
				"At least one node is not defined (i.e. = -1) or the two nodes are identical; node1 = "
						+ node1 + ", node2 = " + node2);
		if (logger.isDebugEnabled()) {
			logger.debug("Swapping nodes " + node1 + " and " + node2);
		}
		swapProcesses(node1, node2);

		return new int[] { node1, node2 };
	}
	
	/**
	 * Swaps the processes from nodes with IDs t1 and t2
	 * 
	 * @param t1
	 *            the ID of the first node
	 * @param t2
	 *            the ID of the second node
	 */
	private void swapProcesses(int t1, int t2) {
		NodeType node1 = nodes[t1];
		NodeType node2 = nodes[t2];
		logger.assertLog(node1 != null, null);
		logger.assertLog(node2 != null, null);

		int p1 = Integer.valueOf(node1.getCore());
		int p2 = Integer.valueOf(node2.getCore());
		
		logger.assertLog(t1 == Integer.valueOf(node1.getId()), null);
		logger.assertLog(t2 == Integer.valueOf(node2.getId()), null);
		logger.assertLog(p1 == Integer.valueOf(node1.getCore()), null);
		logger.assertLog(p2 == Integer.valueOf(node2.getCore()), null);
		
		if (logger.isTraceEnabled()) {
			logger.trace("Swapping process " + p1 + " of node " + t1
					+ " with process " + p2 + " of node " + t2);
		}
		
		node1.setCore(Integer.toString(p2));
		node2.setCore(Integer.toString(p1));
		if (p1 != -1) {
			Core process = cores[p1];
			if (process == null) {
				process = new Core(p1, null, t2);
			} else {
				process.setNodeId(t2);
			}
		}
		if (p2 != -1) {
			Core process = cores[p2];
			if (process == null) {
				process = new Core(p2, null, t1);
			} else {
				process.setNodeId(t1);
			}
		}
	}

	/** seed for the {@link #uniformRandomVariable()} method */
	private int urvSeed = 1234567;
	
	// ways to gen Random Vars with specific distributions
	/**
	 * Simple random number generator based on the linear-congruential method
	 * using parameters from example D, p 40, Knuth Vol 2.
	 * 
	 * @return a real number uniformly distributed on [0,1]. This version has
	 *         the advantage that it should behave the same on different
	 *         machines, since the generator and starting point are explicitly
	 *         specified.
	 */
	private double uniformRandomVariable() {
		// one small problem: the sequence we use can produce integers larger
		// than the word size used, i.e. they can wrap around negative. We wimp
		// out on this matter and just make them positive again.

		final int A = 147453245;
		final int C = 226908347;
		final int M = 1073741824;

		urvSeed = ((A * urvSeed) + C) % M;
		if (urvSeed < 0) {
			urvSeed = -urvSeed;
		}
		double u = (((double) urvSeed) / ((double) M));
		if (logger.isTraceEnabled()) {
			logger.trace(u);
		}
		return u;
	}
	
	/**
	 * @return a random INTEGER in [imin, imax]
	 */
	private long uniformIntegerRandomVariable(long imin, long imax) {
		double u;
		int m;

		u = uniformRandomVariable();
		m = (int) imin + ((int) Math.floor((double) (imax + 1 - imin) * u));
		if (logger.isTraceEnabled()) {
			logger.trace("Generated integer random number from interval [" + imin
					+ ", " + imax + "] = " + m);
		}
		return m;
	}
	
}
