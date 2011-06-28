package ro.ulbsibiu.acaps.mapper.osa;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.LegalTurnSet;
import ro.ulbsibiu.acaps.mapper.sa.Core;
import ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper;
import ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;

/**
 * Optimized Simulated Annealing (OSA), <b>without clustering</b>. Read my CSCS paper
 * ("Optimized Simulated Annealing for Network-on-Chip Application Mapping") for
 * details.
 * 
 * <p>
 * Note that currently, this algorithm works only with M x N 2D mesh NoCs
 * </p>
 * 
 * @see SimulatedAnnealingMapper
 * 
 * @author cipi
 * 
 */
public class OptimizedSimulatedAnnealingWithoutClusteringMapper extends
		BandwidthConstrainedEnergyAndPerformanceAwareMapper {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(OptimizedSimulatedAnnealingWithoutClusteringMapper.class);
	
	private static final String MAPPER_ID = "osa_without_clustering";

	/** the distinct nodes with which each node communicates directly (through a single link) */
	protected Set<Integer>[] nodeNeighbors;
	
	/** the maximum neighbors a node can have */
	private int maxNodeNeighbors = 0;

	/** the distinct cores with which each core communicates directly */
	protected Set<Integer>[] coreNeighbors;
	
	/**
	 * the total data communicated by the cores
	 */
	private double[] coreToCommunication;
	
	/** the total amount of data communicated by all cores*/
	private long totalToCommunication;
	
	/** for every core, the (from and to) communication probability density function */
	protected double[][] coresCommunicationPDF;

	/** the seed for the random number generator of the initial population */
	private Long seed;
	
	/** how many mappings are evaluated */
	private long evaluations = 0;

	/**
	 * how many mapping attempts the algorithm tries per iteration. A mapping
	 * attempt means a random swap of processes (tasks) between to network nodes
	 */
	private int numberOfIterationsPerTemperature;
	
	private double temperature;
	
	/** the initial temperature */
	private Double initialTemperature = 1.0;

	/** how many consecutive moves were rejected at a certain temperature level */
	private int numberOfConsecutiveRejectedMoves;

	/** the cost of the initial mapping */
	private double initialCost;
	
	/** the cost of the current mapping */
	private double currentCost;

	/** the acceptance ratio */
	private double acceptRatio;

	private String[] bestSolution;
	
	private double bestCost = Float.MAX_VALUE;
	
	private double bestSolutionTemperature;
	
	private int bestSolutionIteration;
	
	private int mappingIteration;
	
	/**
	 * Default constructor
	 * <p>
	 * No routing table is built.
	 * </p>
	 * 
	 * @param benchmarkName
	 *            the benchmark's name
	 * @param ctgId
	 *            the CTG ID
	 * @param topologyName
	 *            the topology name
	 * @param topologySize
	 *            the topology size
	 * @param topologyDir
	 *            the topology directory is used to initialize the NoC topology
	 *            for XML files. These files are split into two categories:
	 *            nodes and links. The nodes are expected to be located into the
	 *            "nodes" subdirectory, and the links into the "links"
	 *            subdirectory
	 * @param coresNumber
	 *            the number of processes (tasks). Note that each core has only
	 *            one task associated to it
	 * @param linkBandwidth
	 *            the bandwidth of each network link
	 * @param seed
	 *            the seed for the random number generator of the initial
	 *            population
	 */
	public OptimizedSimulatedAnnealingWithoutClusteringMapper(String benchmarkName, String ctgId,
			String apcgId, String topologyName, String topologySize,
			File topologyDir, int coresNumber, double linkBandwidth,
			float switchEBit, float linkEBit, Long seed, Double initialTemperature) throws JAXBException {
		this(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, false,
				LegalTurnSet.WEST_FIRST, 1.056f, 2.831f, switchEBit, linkEBit, seed, initialTemperature);
	}

	/**
	 * Constructor
	 * 
	 * @param benchmarkName
	 *            the benchmark's name
	 * @param ctgId
	 *            the CTG ID
	 * @param apcgId
	 *            the APCG ID
	 * @param topologyName
	 *            the topology name
	 * @param topologySize
	 *            the topology size
	 * @param topologyDir
	 *            the topology directory is used to initialize the NoC topology
	 *            for XML files. These files are split into two categories:
	 *            nodes and links. The nodes are expected to be located into the
	 *            "nodes" subdirectory, and the links into the "links"
	 *            subdirectory
	 * @param coresNumber
	 *            the number of processes (tasks). Note that each core has only
	 *            one task associated to it
	 * @param linkBandwidth
	 *            the bandwidth of each network link
	 * @param buildRoutingTable
	 *            whether or not to build routing table too
	 * @param legalTurnSet
	 *            what {@link LegalTurnSet} the SA algorithm should use (this is
	 *            useful only when the routing table is built)
	 * @param bufReadEBit
	 *            energy consumption per bit read
	 * @param bufWriteEBit
	 *            energy consumption per bit write
	 * @param switchEBit
	 *            the energy consumed for switching one bit of data
	 * @param linkEBit
	 *            the energy consumed for sending one data bit
	 * @param seed
	 *            the seed for the random number generator of the initial
	 *            population (can be null)
	 * @param initialTemperature
	 *            the initial temperature (can be null, in which case it is considered to be 1)
	 * @throws JAXBException
	 */
	public OptimizedSimulatedAnnealingWithoutClusteringMapper(String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir, int coresNumber,
			double linkBandwidth, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit,
			float switchEBit, float linkEBit, Long seed, Double initialTemperature) throws JAXBException {
		
		super(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, buildRoutingTable,
				legalTurnSet, bufReadEBit, bufWriteEBit, switchEBit, linkEBit);
		
		if (initialTemperature != null) {
			this.initialTemperature = initialTemperature;
		}
		this.temperature = this.initialTemperature;
		this.seed = seed;

		nodeNeighbors = new LinkedHashSet[nodes.length];
		for (int i = 0; i < links.length; i++) {
			if (nodeNeighbors[Integer.valueOf(links[i].getFirstNode())] == null) {
				nodeNeighbors[Integer.valueOf(links[i].getFirstNode())] = new LinkedHashSet<Integer>();
			}
			if (nodeNeighbors[Integer.valueOf(links[i].getSecondNode())] == null) {
				nodeNeighbors[Integer.valueOf(links[i].getSecondNode())] = new LinkedHashSet<Integer>();
			}
			nodeNeighbors[Integer.valueOf(links[i].getFirstNode())].add(Integer.valueOf(links[i].getSecondNode()));
			nodeNeighbors[Integer.valueOf(links[i].getSecondNode())].add(Integer.valueOf(links[i].getFirstNode()));
		}
		for (int i = 0; i < nodeNeighbors.length; i++) {
			if (nodeNeighbors[i].size() > maxNodeNeighbors) {
				maxNodeNeighbors = nodeNeighbors[i].size();
			}
		}
		if (logger.isDebugEnabled()) {
			for (int i = 0; i < nodeNeighbors.length; i++) {
				logger.debug("Node " + i + " communicates with " + nodeNeighbors[i].size() + " nodes");
			}
			logger.debug("The maximum nodes a node has is " + maxNodeNeighbors);
		}
	}
	
	@Override
	public String getMapperId() {
		return MAPPER_ID;
	}

	private void mapCoresToNocNodesRandomly() {
		Random rand;
		if (seed == null) {
			rand = new Random();
		} else {
			rand = new Random(seed);
		}
		for (int i = 0; i < cores.length; i++) {
			int k = Math.abs(rand.nextInt()) % nodes.length;
			while (Integer.valueOf(nodes[k].getCore()) != -1) {
				k = Math.abs(rand.nextInt()) % nodes.length;
			}
			cores[i].setNodeId(k);
			nodes[k].setCore(Integer.toString(i));
		}

//		// this maps the cores like NoCMap does
//		int[] coreMap = new int[] { 11, 13, 10, 8, 12, 0, 9, 1, 2, 4, 14, 15,
//				5, 3, 7, 6 };
//		for (int i = 0; i < cores.length; i++) {
//			cores[i].setNodeId(coreMap[i]);
//			nodes[coreMap[i]].setCore(Integer.toString(i));
//		}
		
//		// VOPD mapping that leads to optimal mapping when using random swapping
//		int[] coreMap = new int[] { 14, 3, 9, 15, 4, 13, 5, 1, 6, 0, 8, 7, 2, 12, 11, 10 };
//		for (int i = 0; i < cores.length; i++) {
//			cores[i].setNodeId(coreMap[i]);
//			nodes[coreMap[i]].setCore(Integer.toString(i));
//		}
//		
//		// optimal VOPD mapping
//		int[] coreMap = new int[] { 14, 13, 15, 11, 10, 1, 0, 4, 8, 12, 9, 5, 7, 6, 2, 3 };
//		for (int i = 0; i < cores.length; i++) {
//			cores[i].setNodeId(coreMap[i]);
//			nodes[coreMap[i]].setCore(Integer.toString(i));
//		}
		
//		// sub-optimal VOPD mapping, only is float is used instead of double (for mapping cost calculation)
//		int[] coreMap = new int[] { 13, 12, 14, 6, 5, 2, 3, 7, 11, 15, 10, 9, 1, 8, 4, 0 };
//		for (int i = 0; i < cores.length; i++) {
//			cores[i].setNodeId(coreMap[i]);
//			nodes[coreMap[i]].setCore(Integer.toString(i));
//		}
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
	protected double uniformRandomVariable() {
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
	protected long uniformIntegerRandomVariable(long imin, long imax) {
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

	/**
	 * the acceptance function
	 * 
	 * @param deltac
	 *            the <b>normalized</b> cost (energy) variation
	 * 
	 * @return <tt>true</tt> for accept, <tt>false</tt>, otherwise
	 */
	private boolean accept(double deltac) {
		double pa = -1; // probability of acceptance
		boolean accept = false;
		double r = -1;

		if (MathUtils.definitelyLessThan((float) deltac, 0)
				|| MathUtils.approximatelyEqual((float) deltac, 0)) {
			accept = true;
		} else {
//			// normalized exponential form
//			pa = Math.exp((double) (-deltac) / temperature);
			
			// inverse normalized exponential form
			pa = 1 / (1 + Math.exp((double) (deltac) / temperature));
			
			r = uniformRandomVariable();
			if (MathUtils.definitelyLessThan((float) r, (float)pa)
					|| MathUtils.approximatelyEqual((float) r, (float)pa)) {
				accept = true;
			} else {
				accept = false;
			}
		}
		if (logger.isTraceEnabled()) {
			logger.trace("deltac " + deltac + " temp " + temperature + " r " + r
					+ " pa " + pa + " accept " + accept);
		}
		return accept;
	}

	/**
	 * this does the actual evolution of the placement by annealing at a fixed
	 * temperature <tt>t</tt>.
	 */
	private double annealAtTemperature() {
		int acceptCount = 0;
		double totalDeltaCost = 0;
		numberOfConsecutiveRejectedMoves = 0;
		
		int unit = numberOfIterationsPerTemperature / 10;

		// this is the main loop doing moves. We do 'attempts' moves in all,
		// then quit at this temperature

		if (logger.isTraceEnabled()) {
			logger.trace("number of iterations per temperature = " + numberOfIterationsPerTemperature);
		}
//		List<String[]> uniqueMappings = new ArrayList<String[]>(); 
//		List<Integer> uniqueMappingsFrequencies = new ArrayList<Integer>();
		for (int m = 1; m <= numberOfIterationsPerTemperature; m++) {
			int[] movedNodes = move();
//			printCurrentMapping();
			
//			// computes the unique mappings (start)
//			boolean isNewMapping = true;
//			for (int i = 0; i < uniqueMappings.size(); i++) {
//				boolean found = true;
//				logger.assertLog(this.nodes.length == uniqueMappings.get(i).length, null);
//				for (int j = 0; j < uniqueMappings.get(i).length; j++) {
//					if (!uniqueMappings.get(i)[j].equals(this.nodes[j].getCore())) {
//						found = false;
//						break;
//					}
//				}
//				if (found) {
//					isNewMapping = false;
//					uniqueMappingsFrequencies.set(i, uniqueMappingsFrequencies.get(i) + 1);
//				}
//			}
//			if (isNewMapping) {
//				String[] map = new String[this.nodes.length];
//				for (int i = 0; i < this.nodes.length; i++) {
//					map[i] = this.nodes[i].getCore();
//				}
//				uniqueMappings.add(map);
//				uniqueMappingsFrequencies.add(1);
//			}
//			// computes the unique mappings (end)
			
			int node1 = movedNodes[0];
			int node2 = movedNodes[1];

			double newCost = calculateTotalCost();
			evaluations++;

			double deltaCost = newCost - currentCost;
			if (logger.isTraceEnabled()) {
				logger.trace("deltaCost " + deltaCost + " newCost " + newCost
						+ " currentCost " + currentCost);
			}
			// we normalize deltac
	        double deltac = deltaCost / initialCost;
			// Note that we use machine epsilon to perform the following
			// comparison between the float numbers
	        if (MathUtils.approximatelyEqual((float)deltac, 0)) {
	            deltac = 0;
	        }
			if (MathUtils.definitelyLessThan((float)deltac, 0) || accept(deltac)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Accepting...");
				}
				if (MathUtils.definitelyLessThan((float)newCost, (float)bestCost)) {
					if (logger.isDebugEnabled()) {
						logger.debug("new cost < best cost (" + newCost + " < " + bestCost + ")");
					}
					bestSolutionTemperature = temperature;
					bestSolutionIteration = mappingIteration;
					bestCost = newCost;
					bestSolution = new String[nodes.length];
					for (int i = 0; i < nodes.length; i++) {
						bestSolution[i] = nodes[i].getCore();
					}
					numberOfConsecutiveRejectedMoves = 0;
				} else {
					numberOfConsecutiveRejectedMoves++;
				}
				acceptCount++;
				totalDeltaCost += deltaCost;
				currentCost = newCost;
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Rolling back nodes " + node1 + " and " + node2);
				}
				swapProcesses(node1, node2); // roll back
				numberOfConsecutiveRejectedMoves++;
			}
			if (m % unit == 0) {
				// This is just to print out the process of the algorithm
				System.out.print("#");
			}
		}
		System.out.println();
		
//		// prints the unique mappings
//		System.out.println("Found " + uniqueMappings.size() + " unique mappings (from a total of " + numberOfIterationsPerTemperature + " mappings)");
////		System.out.println("with the following frequencies:");
////		for (int i = 0; i < uniqueMappings.size(); i++) {
////			if (uniqueMappingsFrequencies.get(i) > 1) {
////				for (int j = 0; j < uniqueMappings.get(i).length; j++) {
////					System.out.print(uniqueMappings.get(i)[j] + " ");
////				}
////				System.out.println("frequency: " + uniqueMappingsFrequencies.get(i));
////			}
////		}
		
		acceptRatio = ((double) acceptCount) / numberOfIterationsPerTemperature;

		return totalDeltaCost;
	}
	
	public void setNumberOfIterationsPerTemperature() {
		numberOfIterationsPerTemperature = (nodes.length * (nodes.length - 1)) / 2 - ((nodes.length - cores.length - 1) * (nodes.length - cores.length)) / 2;
//		numberOfIterationsPerTemperature = cores.length * (nodes.length - 1);
//		numberOfIterationsPerTemperature = nodes.length - 2; // diameter of the 2D mesh 
	}
	
	public double getInitialTemperature() {
		return initialTemperature;
	}
	
	public double getFinalTemperature() {
		return 1e-3;
	}
	
	public void decreaseTemperature() {
		// geometric temperature schedule (with ratio q = 0.9)
		temperature = 0.9 * temperature;
	}
	
	public boolean terminate() {
		if (logger.isDebugEnabled()) {
			logger.debug("current temperature < final temperature (" + getFinalTemperature() + ") "
					+ (MathUtils.definitelyLessThan((float) temperature, (float) getFinalTemperature())));
			logger.debug("numberOfConsecutiveRejectedMoves >= numberOfIterationsPerTemperature "
					+ (numberOfConsecutiveRejectedMoves >= numberOfIterationsPerTemperature)
					+ " ("
					+ numberOfConsecutiveRejectedMoves
					+ " < "
					+ numberOfIterationsPerTemperature + ")");
		}
		return MathUtils.definitelyLessThan((float)temperature, (float)getFinalTemperature()) 
			&& numberOfConsecutiveRejectedMoves >= numberOfIterationsPerTemperature;
	}

	protected int doMapping() {
		double totalDeltaCost;

		// set up the global control parameters for this annealing run
		mappingIteration = 0;
		initialCost = calculateTotalCost();
		evaluations++;
		currentCost = initialCost;

		setNumberOfIterationsPerTemperature();

		/* here is the temperature cooling loop of the annealer */
		while(!terminate()) {
			System.out.println("Round " + mappingIteration + ":");
			System.out.println("Current Annealing temperature " + temperature);

			totalDeltaCost = annealAtTemperature();
			
//			System.exit(-1);

			System.out.println("total delta cost " + totalDeltaCost);
			System.out.println("Current cost " + currentCost);
			System.out.println("Accept ratio " + acceptRatio);
			
//			printCurrentMapping();

			// save the relevant info to test for frozen after the NEXT
			// temperature.
			mappingIteration++;
			decreaseTemperature();
		}
		// return the best mapping found during the entire annealing process!!! (not the last mapping found)
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore("-1");
		}
		for (int i = 0; i < cores.length; i++) {
			cores[i].setNodeId(-1);
		}
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore(bestSolution[i]);
			if (!"-1".equals(bestSolution[i])) {
				cores[Integer.valueOf(bestSolution[i])].setNodeId(i);
			}
		}
		if (buildRoutingTable) {
			programRouters();
		}
		return 1;
	}

	/**
	 * Changes the current mapping by moving a core from one node to another.
	 * This implies that two nodes are (randomly) changed.
	 * 
	 * @return the IDs of the two changed nodes
	 */
	protected int[] move() {
		return makeRandomSwap();
	}
	
	/**
	 * Randomly picks two nodes and swaps them
	 * 
	 * @return an array with exactly 2 integers
	 */
	private int[] makeRandomSwap() {
		int node1 = (int) uniformIntegerRandomVariable(0, nodes.length - 1);
		int node2 = -1;

		int cnt = 0;
		while (true) {
			cnt++;
			// select two nodes to swap
			node2 = (int) uniformIntegerRandomVariable(0, nodes.length - 1);
			if (node1 != node2
					&& (!"-1".equals(nodes[node1].getCore()) || !"-1"
							.equals(nodes[node2].getCore()))) {
				break;
			}
		}
		if (cnt > 1 && logger.isTraceEnabled()) {
			logger.trace("The nodes to swap were randomly found after " + cnt + " trials");
		}

		// Swap the processes attached to these two nodes
		swapProcesses(node1, node2);
		return new int[] { node1, node2 };
	}
	
	/**
	 * Randomly selects a node (n1) that has a core (c1) mapped to it. Core c1
	 * is allowed to be placed only onto the NoC nodes that have enough
	 * neighbors so that all c1's communications have a corresponding node
	 * neighbor. If c1 has more communications than the maximum neighbors of a
	 * node, the node with a maximum neighborhood are used. This node is swapped
	 * randomly with another node (n2), unless c1 receives data from just a core
	 * (c2). In this case, c1 will be placed in one of the neighbors of n2 that
	 * doesn't have a core mapped to it. If there isn't an empty neighbor, we
	 * search for a neighbor with a core which doesn't communicate with c2. If
	 * we still don't find a neighbor, we fall back to random swapping. Note
	 * that is multiple neighbors are found, the chosen one is determined
	 * randomly.
	 * 
	 * @return the two swapped nodes
	 */
	private int[] makeTopologicalMove() {
		int node1;
		int node2;
		
		int core1 = selectCore();
		if (core1 == -1) {
			logger.fatal("Unable to select any core for moving!");
			System.exit(-1);
		}
		node1 = cores[core1].getNodeId();
		
		if (logger.isDebugEnabled()) {
			logger.debug("Selected node " + node1 + " for moving. It has core " + core1);
			logger.debug("Node " + node1 + " communicates with nodes " + nodeNeighbors[node1]);
			logger.debug("Core " + core1 + " communicates with cores " + coreNeighbors[core1]);
		}
		
		// check where core1 could be placed so that its number of
		// communications with other cores can be satisfied by the number of
		// node neighbors (one node neighbor for each communication)
		//
		// obviously, node node1 will not be among core1's allowed nodes
		//
		// also, core1 cannot be placed on a node that has a core with a number
		// of communications (with other cores) that can not be satisfied by the
		// number of core1's node neighbors
		List<Integer> core1AllowedNodes = new ArrayList<Integer>();
		for (int i = 0; i < nodeNeighbors.length; i++) {
			if (i != node1
					&& (nodeNeighbors[i].size() >= coreNeighbors[core1].size()
							|| nodeNeighbors[i].size() == maxNodeNeighbors)) {
				if ("-1".equals(nodes[i].getCore())) {
					core1AllowedNodes.add(i);
					if (logger.isDebugEnabled()) {
						logger.debug("Core " + core1 + " is allowed to be placed on node " + i);
					}
				} else {
					int currentCore = Integer.valueOf(nodes[i].getCore());
					int currentCoreNeighbors = coreNeighbors[currentCore]
							.size();
					if (nodeNeighbors[node1].size() >= currentCoreNeighbors
							|| nodeNeighbors[node1].size() == maxNodeNeighbors) {
						core1AllowedNodes.add(i);
						if (logger.isDebugEnabled()) {
							logger.debug("Core " + core1
									+ " is allowed to be placed on node " + i);
						}
					} else {
						if (logger.isDebugEnabled()) {
							logger.debug("Core " + core1
									+ " is not allowed to be placed on node "
									+ i + " because this node has core "
									+ currentCore
									+ ", which communicates with "
									+ currentCoreNeighbors
									+ " other cores (and node " + node1
									+ " is connected with only "
									+ nodeNeighbors[node1].size()
									+ " other nodes)");
						}
					}
				}
			}
		}
		
		// check if core1 receives data only from one core
		long[] fromCommunication = cores[core1].getFromCommunication();
		int fromCoresCount = 0;
		int core2 = -1; // core2 has meaning only in the (fromCoresCount == 1) if
		for (int i = 0; i < fromCommunication.length; i++) {
			if (fromCommunication[i] > 0) {
				fromCoresCount++;
				core2 = i;
				if (fromCoresCount > 1) {
					// no need to search further on
					break;
				}
			}
		}
		if (fromCoresCount == 1) {
			if (logger.isDebugEnabled()) {
				logger.debug("Core " + core1 + " receives data only from core "
						+ core2 + ". Trying to compactly place core " + core1
						+ " (onto a neighbor node of core " + core2 + ")");
			}
			int core2Node = cores[core2].getNodeId(); // the node that has core2
			logger.assertLog(core2Node >= 0, "Couldn't find the node to which core " + core2 + " is placed!");
			if (logger.isDebugEnabled()) {
				logger.debug("Core " + core2 + " is placed onto node " + core2Node);
			}
			// determine the neighboring nodes of node core2Node that are among the allowed nodes for core1
			Set<Integer> core2NodeNeighborsAllowedForCore1 = new LinkedHashSet<Integer>();
			for (Integer neighbor : nodeNeighbors[core2Node]) {
				if (core1AllowedNodes.contains(neighbor)) {
					core2NodeNeighborsAllowedForCore1.add(neighbor);
					if (logger.isDebugEnabled()) {
						logger.debug("Node " + neighbor + " is a neighbor of node " + core2Node + 
								" and also one of the nodes allowed for core " + core1);
					}
				}
			}
			// determine the neighboring nodes of node core2Node which are unoccupied
			List<Integer> unoccupiedNodes = null;
			if (cores.length < nodes.length) {
				unoccupiedNodes = new ArrayList<Integer>(core2NodeNeighborsAllowedForCore1.size());
				for (Iterator<Integer> iterator = core2NodeNeighborsAllowedForCore1.iterator(); iterator.hasNext();) {
					Integer neighbor = iterator.next();
					if ("-1".equals(nodes[neighbor].getCore())) {
						if (logger.isDebugEnabled()) {
							logger.debug("Node " + neighbor + " is unoccupied");
						}
						unoccupiedNodes.add(neighbor);
					}
				}
			}
			if (unoccupiedNodes != null && unoccupiedNodes.size() > 0) {
				int r = (int) uniformIntegerRandomVariable(0, unoccupiedNodes.size() - 1);
				node2 = unoccupiedNodes.get(r);
				if (logger.isDebugEnabled()) {
					logger.debug("Core " + core1 + " will be moved from node " + node1 + " to the unoccupied node " + node2);
				}
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("All neighbors of node " + core2Node + 
							" are occupied or are not among the allowed nodes for core " + core1 + ". " +
							"Searching for neighboring nodes of node " + core2Node + 
							" which have cores that do not communicate with core " + core1);
				}
				// determine the neighboring nodes of node core2Node which have cores that do not communicate with core2
				List<Integer> notCommunicatingNodes = new ArrayList<Integer>(core2NodeNeighborsAllowedForCore1.size());
				for (Iterator<Integer> iterator = core2NodeNeighborsAllowedForCore1.iterator(); iterator.hasNext();) {
					Integer neighbor = iterator.next();
					Core core = cores[Integer.valueOf(nodes[neighbor].getCore())];
					if (core.getToCommunication()[core2] == 0 && core.getFromCommunication()[core2] == 0) {
						if (logger.isDebugEnabled()) {
							logger.debug("Node " + neighbor + " has core " + core.getCoreId() + 
									" (APCG " + core.getApcgId() + ") that does not communicate with core " + core2);
						}
						notCommunicatingNodes.add(neighbor);
					}
					
				}
				if (notCommunicatingNodes.size() > 0) {
					int r = (int) uniformIntegerRandomVariable(0, notCommunicatingNodes.size() - 1);
					node2 = notCommunicatingNodes.get(r);
					if (logger.isDebugEnabled()) {
						logger.debug("Core " + core1
								+ " receives data only from core " + core2
								+ " and it will be moved from node " + node1
								+ " to the node " + node2 + ", a neighbor of node "
								+ core2Node + " that has core "
								+ nodes[core2Node].getCore());
					}
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("No suitable neighbor node found. Falling back to random swap " +
								"(restricted to allowed nodes for core " + core1 + " )...");
					}
					// core1 will be placed onto one of the allowed nodes
					if (core1AllowedNodes.size() == 0) {
						node2 = node1;
						logger.warn("No nodes are allowed for core " + core1
								+ ". We pretend we make a move by swapping node "
								+ node1 + " with node " + node2);
					} else {
						int i = (int) uniformIntegerRandomVariable(0, core1AllowedNodes.size() - 1);
						node2 = core1AllowedNodes.get(i);
						if (logger.isDebugEnabled()) {
							logger.debug("Core " + core1 + " will be moved from node " + node1 + " to the allowed node " + node2);
						}
					}
				}
			}
		} else {
			// core1 will be placed onto one of the allowed nodes
			if (core1AllowedNodes.size() == 0) {
				node2 = node1;
				logger.warn("No nodes are allowed for core " + core1
						+ ". We pretend we make a move by swapping node "
						+ node1 + " with node " + node2);
			} else {
				int i = (int) uniformIntegerRandomVariable(0, core1AllowedNodes.size() - 1);
				node2 = core1AllowedNodes.get(i);
				if (logger.isDebugEnabled()) {
					logger.debug("Core " + core1 + " will be moved from node " + node1 + " to the allowed node " + node2);
				}
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
	 * Randomly chooses a core based on the probability distribution function
	 * determined by the communications among cores
	 * 
	 * @return the chosen core
	 */
	protected int selectCore() {
		int core = -1;
		double p = uniformRandomVariable();
		double sum = 0;
		for (int i = 0; i < cores.length; i++) {
//			sum += coreToCommunication[i] / totalToCommunication;
			
//			sum += (meanPDF + temperature * (coreToCommunication[i] - meanPDF)) / totalToCommunication;
			
			// as the temperature decreases, the probabilities equalize more and more
			sum += ((totalToCommunication * 1.0 / cores.length) + (temperature / getInitialTemperature())
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

	private int[] makeVariableGrainSizeMove() {
		int node1;
		int node2;
		
		int core1 = selectCore();
		if (core1 == -1) {
			logger.fatal("Unable to select any core for moving!");
			System.exit(-1);
		}
		node1 = cores[core1].getNodeId();
		
		if (logger.isDebugEnabled()) {
			logger.debug("Selected core " + core1 + " for moving. It is on node " + node1);
		}
		
		int core2 = -1;
		do {
			core2 = selectCore();
		} while (core2 == core1);
		if (core2 == -1) {
			logger.fatal("Unable to select any core for moving!");
			System.exit(-1);
		}
		node2 = cores[core2].getNodeId();
		
		if (logger.isDebugEnabled()) {
			logger.debug("Core " + core1 + " will be swapped with core " + core2 + ", which is on node " + node2);
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
	protected void swapProcesses(int t1, int t2) {
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

	private void computeCoreToCommunicationPDF() {
		totalToCommunication = 0;
		coreToCommunication = new double[cores.length];
		
		for (int i = 0; i < cores.length; i++) {
			long[] toCommunication = cores[i].getToCommunication();
			for (int j = 0; j < toCommunication.length; j++) {
				if (toCommunication[j] > 0) {
					coreToCommunication[i] += toCommunication[j];
				}
			}
			totalToCommunication += coreToCommunication[i];
		}
		logger.assertLog(totalToCommunication > 0, "No core is communicating any data!");
		double total = 0;
		for (int i = 0; i < cores.length; i++) {
			if (logger.isDebugEnabled()) {
				logger.debug("Core " + cores[i].getCoreId() + " (APCG "
						+ cores[i].getApcgId() + ") communicates "
						+ coreToCommunication[i] + ", which is "
						+ 100 * (coreToCommunication[i] / totalToCommunication)
						+ "% of the total communication volume");
			}
			total += 100 * (coreToCommunication[i] / totalToCommunication);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Total probability is " + total + "%");
		}
	}
	
	private void computeCoresCommunicationPDF() {
		coresCommunicationPDF = new double[cores.length][cores.length];
		
		for (int i = 0; i < cores.length; i++) {
			long totalCommunication = 0;
			long[] toCommunication = cores[i].getToCommunication();
			long[] fromCommunication = cores[i].getFromCommunication();
			logger.assertLog(toCommunication.length == fromCommunication.length, null);
			
			for (int j = 0; j < cores.length; j++) {
				coresCommunicationPDF[i][j] = toCommunication[j] + fromCommunication[j];
				totalCommunication += toCommunication[j];
				totalCommunication += fromCommunication[j];
			}
			for (int j = 0; j < cores.length; j++) {
				if (totalCommunication > 0) {
					coresCommunicationPDF[i][j] = coresCommunicationPDF[i][j] / totalCommunication;
				}
			}
		}
	}
	
	private void computeCoreNeighbors() {
		coreNeighbors = new LinkedHashSet[cores.length];
		for (int i = 0; i < cores.length; i++) {
			coreNeighbors[i] = new LinkedHashSet<Integer>();
			long[] fromCommunication = cores[i].getFromCommunication();
			for (int j = 0; j < fromCommunication.length; j++) {
				if (fromCommunication[j] > 0) {
					coreNeighbors[i].add(j);
				}
			}
			long[] toCommunication = cores[i].getToCommunication();
			for (int j = 0; j < toCommunication.length; j++) {
				if (toCommunication[j] > 0) {
					coreNeighbors[i].add(j);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Core " + cores[i].getCoreId() + "(APCG "
						+ cores[i].getApcgId() + ") communicates with "
						+ coreNeighbors[i].size() + " cores");
			}
		}
	}
	
	@Override
	protected void doBeforeMapping() {
		computeCoreNeighbors();
		computeCoreToCommunicationPDF();
		computeCoresCommunicationPDF();
		mapCoresToNocNodesRandomly();
		printCurrentMapping();
	}

	@Override
	protected void doBeforeSavingMapping() {
		logger.info("Best mapping found at round " + bestSolutionIteration + 
				", temperature " + bestSolutionTemperature + ", with cost " + bestCost);
		logger.info("A number of " + evaluations + " mappings were evaluated");
	}

	private void parseTrafficConfig(String filePath, double linkBandwidth)
			throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(filePath)));

		int id = -1;

		String line;
		while ((line = br.readLine()) != null) {
			// line starting with "#" are comments
			if (line.startsWith("#")) {
				continue;
			}

			if (line.contains("@NODE")) {
				try {
					id = Integer.valueOf(line.substring("@NODE".length() + 1));
				} catch (NumberFormatException e) {
					logger.error("The node from line '" + line + "' is not a number", e);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("ID = " + id);
				}
			}

			if (line.contains("packet_to_destination_rate")) {
				String substring = line.substring(
						"packet_to_destination_rate".length() + 1).trim();
				int dstId = -1;
				try {
					dstId = Integer.valueOf(substring.substring(0,
							substring.indexOf("\t")));
					if (logger.isTraceEnabled()) {
						logger.trace(" dst ID = " + dstId);
					}
				} catch (NumberFormatException e) {
					logger.error("The destination from line '" + line + "' is not a number", e);
				}
				double rate = 0;
				try {
					rate = Double.valueOf(substring.substring(substring
							.indexOf("\t") + 1));
					if (logger.isTraceEnabled()) {
						logger.trace(" rate = " + rate);
					}
				} catch (NumberFormatException e) {
					logger.error("The rate from line '" + line + "' is not a number", e);
				}

				if (rate > 1) {
					logger.fatal("Invalid rate!");
					System.exit(0);
				}
				cores[id].getToCommunication()[dstId] = (int) (rate * 1000000);
				cores[id].getToBandwidthRequirement()[dstId] = (int) (rate * 3 * linkBandwidth);
				cores[dstId].getFromCommunication()[id] = (int) (rate * 1000000);
				cores[dstId].getFromBandwidthRequirement()[id] = (int) (rate * 3 * linkBandwidth);
			}
		}

		br.close();
	}

	public static void main(String[] args) throws TooFewNocNodesException,
			IOException, JAXBException, ParseException {
		final float switchEBit = 0.284f;
		final float linkEBit = 0.449f;
		final float bufReadEBit = 1.056f;
		final float bufWriteEBit = 2.831f;
		
		final String cliArgs[] = args;
		
		MapperInputProcessor mapperInputProcessor = new MapperInputProcessor() {
			
			@Override
			public void useMapper(String benchmarkFilePath,
					String benchmarkName, String ctgId, String apcgId,
					List<CtgType> ctgTypes, List<ApcgType> apcgTypes,
					boolean doRouting, LegalTurnSet lts, double linkBandwidth,
					Long seed) throws JAXBException, TooFewNocNodesException,
					FileNotFoundException {
				logger.info("Using an Optimized Simulated Annealing without clustering mapper for "
						+ benchmarkFilePath + "ctg-" + ctgId + " (APCG " + apcgId + ")");
				
				OptimizedSimulatedAnnealingWithoutClusteringMapper osaMapper;
				int cores = 0;
				for (int k = 0; k < apcgTypes.size(); k++) {
					cores += apcgTypes.get(k).getCore().size();
				}
				int hSize = (int) Math.ceil(Math.sqrt(cores));
				hSize = Math.max(2, hSize); // using at least a 2x2 2D mesh
				String meshSize;
				// we allow rectangular 2D meshes as well
				if (hSize * (hSize - 1) >= cores) {
					meshSize = hSize + "x" + (hSize - 1);
				} else {
					meshSize = hSize + "x" + hSize;
				}
				logger.info("The algorithm has " + cores + " cores to map => working with a 2D mesh of size " + meshSize);
				// working with a 2D mesh topology
				String topologyName = "mesh2D";
				String topologyDir = ".." + File.separator + "NoC-XML"
						+ File.separator + "src" + File.separator
						+ "ro" + File.separator + "ulbsibiu"
						+ File.separator + "acaps" + File.separator
						+ "noc" + File.separator + "topology"
						+ File.separator + topologyName + File.separator
						+ meshSize;
				
				CommandLineParser parser = new PosixParser();
				Double initialTemperature = null;
				try {
					CommandLine cmd = parser.parse(getCliOptions(), cliArgs);
					initialTemperature = Double.valueOf(cmd.getOptionValue("t", "1.0"));
				} catch (NumberFormatException e) {
					logger.fatal(e);
					System.exit(0);
				} catch (ParseException e) {
					logger.fatal(e);
					System.exit(0);
				}
				
				String[] parameters = new String[] {
						"linkBandwidth",
						"switchEBit",
						"linkEBit",
						"bufReadEBit",
						"bufWriteEBit",
						"routing",
						"seed",
						"initialTemperature",
						};
				String values[] = new String[] {
						Double.toString(linkBandwidth),
						Float.toString(switchEBit), Float.toString(linkEBit),
						Float.toString(bufReadEBit),
						Float.toString(bufWriteEBit),
						null,
						seed == null ? null : Long.toString(seed),
						initialTemperature == null ? null : Double.toString(initialTemperature),
						};
				if (doRouting) {
					values[values.length - 3] = "true" + "-" + lts.toString();
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// OSA with routing
					osaMapper = new OptimizedSimulatedAnnealingWithoutClusteringMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							true, lts, bufReadEBit,
							bufWriteEBit, switchEBit, linkEBit, seed, initialTemperature);
				} else {
					values[values.length - 3] = "false";
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// OSA without routing
					osaMapper = new OptimizedSimulatedAnnealingWithoutClusteringMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							switchEBit, linkEBit, seed, initialTemperature);
				}
	
	//			// read the input data from a traffic.config file (NoCmap style)
	//			osaMapper(
	//					"telecom-mocsyn-16tile-selectedpe.traffic.config",
	//					linkBandwidth);
				
				for (int k = 0; k < apcgTypes.size(); k++) {
					// read the input data using the Unified Framework's XML interface
					osaMapper.parseApcg(apcgTypes.get(k), ctgTypes.get(k));
				}
				
	//			// This is just for checking that bbMapper.parseTrafficConfig(...)
	//			// and parseApcg(...) have the same effect
	//			osaMapper.printCores();
	
				String[] mappingXml = osaMapper.map();
				File dir = new File(benchmarkFilePath + "ctg-" + ctgId);
				dir.mkdirs();
				String routing = "";
				if (doRouting) {
					routing = "_routing";
				}
				String mappingXmlFilePath = benchmarkFilePath + "ctg-" + ctgId
						+ File.separator + "mapping-" + apcgId + "_"
						+ osaMapper.getMapperId() + routing + ".xml";
				PrintWriter pw = new PrintWriter(mappingXmlFilePath);
				logger.info("Saving the mapping XML file" + mappingXmlFilePath);
				pw.write(mappingXml[0]);
				pw.close();
	
				logger.info("The generated mapping is:");
				osaMapper.printCurrentMapping();
				
				osaMapper.analyzeIt();
			}
		};
		
		mapperInputProcessor.getCliOptions().addOption("t", "temperature", true, "the initial temperature");
		
		mapperInputProcessor.processInput(args);
	}
}
