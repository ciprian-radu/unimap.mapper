package ro.ulbsibiu.acaps.mapper.sa;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.CoreType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.TaskType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CommunicationType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MapType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.LegalTurnSet;
import ro.ulbsibiu.acaps.mapper.util.HeapUsageMonitor;
import ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.mapper.util.TimeUtils;
import ro.ulbsibiu.acaps.noc.xml.link.LinkType;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;
import ro.ulbsibiu.acaps.noc.xml.node.ObjectFactory;
import ro.ulbsibiu.acaps.noc.xml.node.RoutingTableEntryType;
import ro.ulbsibiu.acaps.noc.xml.node.TopologyParameterType;

/**
 * Simulated Annealing algorithm for Network-on-Chip (NoC) application mapping.
 * The implementation is based on the one from <a
 * href="http://www.ece.cmu.edu/~sld/wiki/doku.php?id=shared:nocmap">NoCMap</a>
 * 
 * <p>
 * Note that currently, this algorithm works only with M x N 2D mesh NoCs
 * </p>
 * 
 * @author cipi
 * 
 */
public class SimulatedAnnealingMapper extends BandwidthConstrainedEnergyAndPerformanceAwareMapper {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(SimulatedAnnealingMapper.class);
	
	private static final String MAPPER_ID = "sa";

	/** tolerance for the cost function of the algorithm */
	private static final int TOLERANCE = 1;

	/** how many temperature variations the algorithm should try */
	private static final int TEMPS = 5;

	/**
	 * the minimum acceptance ratio (number of viable IP core mappings vs. the
	 * total number of tried mappings)
	 */
	private static final double MINACCEPT = 0.001;

	/** the seed for the random number generator of the initial population */
	private Long seed;
	
	/** how many mappings are evaluated */
	private long evaluations = 0;

	/**
	 * how many mapping attempts the algorithm tries per iteration. A mapping
	 * attempt means a random swap of processes (tasks) between to network nodes
	 */
	private int attempts;

	/** how many zero cost mappings where accepted */
	private int zeroCostAcceptance;

	/**
	 * specifies if the algorithm must be stopped "manually". This is typically
	 * done when the zero cost acceptance is 10
	 */
	private boolean needStop;

	/** the cost of the current mapping */
	private double currentCost;

	/** the acceptance ratio */
	private double acceptRatio;

	/** how many zero cost mappings are currently accepted */
	private int zeroTempCnt = 0;

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
	public SimulatedAnnealingMapper(String benchmarkName, String ctgId,
			String apcgId, String topologyName, String topologySize,
			File topologyDir, int coresNumber, double linkBandwidth,
			float switchEBit, float linkEBit, Long seed) throws JAXBException {
		this(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, false,
				LegalTurnSet.WEST_FIRST, 1.056f, 2.831f, switchEBit, linkEBit, seed);
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
	 *            population
	 * @throws JAXBException
	 */
	public SimulatedAnnealingMapper(String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir, int coresNumber,
			double linkBandwidth, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit,
			float switchEBit, float linkEBit, Long seed) throws JAXBException {
		
		super(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, buildRoutingTable,
				legalTurnSet, bufReadEBit, bufWriteEBit, switchEBit, linkEBit);
		
		this.seed = seed;

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

	/**
	 * the usual metropolis accept criterion
	 * 
	 * @param deltac
	 *            the cost (energy) variation
	 * @param temperature
	 *            the temperature
	 * 
	 * @return <tt>true</tt> for accept, <tt>false</tt>, otherwise
	 */
	private boolean accept(double deltac, double temperature) {
		double pa = -1; // probability of acceptance
		boolean accept = false;
		double r = -1;
		// annealing accept criterion
		if (MathUtils.approximatelyEqual((float) deltac, 0)) {
			// accept it, but record the number of zero cost acceptance
			zeroCostAcceptance++;
		}

		if (MathUtils.definitelyLessThan((float) deltac, 0)
				|| MathUtils.approximatelyEqual((float) deltac, 0)) {
			accept = true;
		} else {
			pa = Math.exp((double) (-deltac) / temperature);
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
	private double annealAtTemperature(double t) {
		int acceptCount = 0;
		double totalDeltaCost = 0;

		int unit = attempts / 10;

		// clear the zeroCostAcceptance
		zeroCostAcceptance = 0;

		// this is the main loop doing moves. We do 'attempts' moves in all,
		// then quit at this temperature

		if (logger.isTraceEnabled()) {
			logger.trace("attempts = " + attempts);
		}
//		List<String[]> uniqueMappings = new ArrayList<String[]>(); 
//		List<Integer> uniqueMappingsFrequencies = new ArrayList<Integer>();
		for (int m = 1; m < attempts; m++) {
			int[] swappedNodes = makeRandomSwap();
			
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
			
			int node1 = swappedNodes[0];
			int node2 = swappedNodes[1];
			double newCost = calculateTotalCost();
			evaluations++;
			double deltaCost = newCost - currentCost;
			if (logger.isTraceEnabled()) {
				logger.trace("deltaCost " + deltaCost + " newCost " + newCost
						+ " currentCost " + currentCost);
			}
	        double deltac = deltaCost / currentCost;
			// Note that we use machine epsilon to perform the following
			// comparison between the float numbers
	        if (MathUtils.approximatelyEqual((float)deltac, 0)) {
	            deltac = 0;
	        } else {
	            deltac = deltac * 100;
	        }
			if (accept(deltac, t)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Accepting...");
				}
				acceptCount++;
				totalDeltaCost += deltaCost;
				currentCost = newCost;
			} else {
				if (logger.isTraceEnabled()) {
					logger.trace("Rolling back nodes " + node1 + " and " + node2);
				}
				swapProcesses(node1, node2); // roll back
			}
			if (m % unit == 0) {
				// This is just to print out the process of the algorithm
				System.out.print("#");
			}
		}
		System.out.println();
		
//		// prints the unique mappings
//		System.out.println("Found " + uniqueMappings.size() + " unique mappings (from a total of " + attempts + " mappings)");
////		System.out.println("with the following frequencies:");
////		for (int i = 0; i < uniqueMappings.size(); i++) {
////			if (uniqueMappingsFrequencies.get(i) > 1) {
////				for (int j = 0; j < uniqueMappings.get(i).length; j++) {
////					System.out.print(uniqueMappings.get(i)[j] + " ");
////				}
////				System.out.println("frequency: " + uniqueMappingsFrequencies.get(i));
////			}
////		}
		
		acceptRatio = ((double) acceptCount) / attempts;

		if (zeroCostAcceptance == acceptCount) {
			zeroTempCnt++;
		}
		else {
			zeroTempCnt = 0;
		}

		if (zeroTempCnt == 10) {
			logger.info("The last 10 accepted mappings have zero cost. The algorithm will stop.");
			needStop = true;
		}

		return totalDeltaCost;
	}

	protected int doMapping() {
		double cost3, cost2;
		boolean done;
		double tol3, tol2, temp;
		double deltaCost;

		// set up the global control parameters for this annealing run
		int tempCount = 0;
		cost3 = 999999999;
		cost2 = 999999999;
		currentCost = cost2;

		attempts = nodes.length * nodes.length * 100;
		// attempts = nodesNumber * 10;

		// Determine initial temperature by accepting all moves and
		// calculate variance.
		/*
		 * compute initial temperature anneal_at_temp(10000.0, costcurrent,
		 * &acceptratio, 1); temp = 20.0 * VAR; init_anneal();
		 */

		temp = 100;

		/* here is the temperature cooling loop of the annealer */
		done = false;
		do {
			needStop = false;

			System.out.println("Round " + tempCount + ":");
			System.out.println("Current Annealing temperature " + temp);

			deltaCost = annealAtTemperature(temp);
			
//			System.exit(-1);

			System.out.println("total delta cost " + deltaCost);
			System.out.println("Current cost " + currentCost);
			System.out.println("Accept ratio " + acceptRatio);
			
//			printCurrentMapping();

			// OK, if we got here the cost function is working fine. We can
			// now look at whether we are frozen, or whether we should cool some
			// more. We basically just look at the last 2 temperatures, and
			// see if the cost is not changing much (that's the TOLERANCE test)
			// and if the we have done enough temperatures (that's the TEMPS
			// test), and if the accept ratio fraction is small enough (that is
			// the MINACCEPT test). If all are satisfied, we quit.

			tol3 = ((double) cost3 - (double) cost2) / (double) cost3;
			if (tol3 < 0) {
				tol3 = -tol3;
			}
			tol2 = ((double) cost2 - (double) currentCost) / (double) cost2;
			if (tol2 < 0) {
				tol2 = -tol2;
			}

			logger.debug("tol3 < TOLERANCE ? "
					+ MathUtils.definitelyLessThan((float) tol3, TOLERANCE)
					+ " (tol3 " + tol3 + " TOLERANCE " + TOLERANCE +")");
			logger.debug("tol2 < TOLERANCE ? "
					+ MathUtils.definitelyLessThan((float) tol2, TOLERANCE)
					+ " (tol2 " + tol2 + " TOLERANCE " + TOLERANCE + ")");
			logger.debug("tempCount > TEMP ? " + (tempCount > TEMPS)
					+ " (tempCount " + tempCount + " TEMPS " + TEMPS + ")");
			logger.debug("acceptRatio < MINACCEPT ? "
					+ MathUtils.definitelyLessThan((float) acceptRatio,
							(float) MINACCEPT) + " (acceptRatio " + acceptRatio
					+ " MINACCEPT " + MINACCEPT + ")");
			logger.debug("needStop "+ needStop);
			
			if (MathUtils.definitelyLessThan((float) tol3, TOLERANCE)
					&& MathUtils.definitelyLessThan((float) tol2, TOLERANCE)
					&& tempCount > TEMPS
					&& (MathUtils.definitelyLessThan((float) acceptRatio,
							(float) MINACCEPT) || needStop)) {
				done = true;
			} else {
				// save the relevant info to test for frozen after the NEXT
				// temperature.
				cost3 = cost2;
				cost2 = currentCost;
				temp = 0.9 * temp;
				tempCount++;
			}
		} while (!done);
		if (buildRoutingTable) {
			programRouters();
		}
		return 1;
	}

	/**
	 * Randomly picks two nodes and swaps them
	 * 
	 * @return an array with exactly 2 integers
	 */
	private int[] makeRandomSwap() {
		int node1 = (int) uniformIntegerRandomVariable(0, nodes.length - 1);
		int node2 = -1;

		while (true) {
			// select two nodes to swap
			node2 = (int) uniformIntegerRandomVariable(0, nodes.length - 1);
			if (node1 != node2
					&& (!"-1".equals(nodes[node1].getCore()) || !"-1"
							.equals(nodes[node2].getCore()))) {
				break;
			}
		}

		// Swap the processes attached to these two nodes
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

	@Override
	protected void doBeforeMapping() {
		mapCoresToNocNodesRandomly();
		printCurrentMapping();
	}

	@Override
	protected void doBeforeSavingMapping() {
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
		
		MapperInputProcessor mapperInputProcessor = new MapperInputProcessor() {
			
			@Override
			public void useMapper(String benchmarkFilePath,
					String benchmarkName, String ctgId, String apcgId,
					List<CtgType> ctgTypes, List<ApcgType> apcgTypes,
					boolean doRouting, LegalTurnSet lts, double linkBandwidth,
					Long seed) throws JAXBException, TooFewNocNodesException,
					FileNotFoundException {
				logger.info("Using a Simulated annealing mapper for "
						+ benchmarkFilePath + "ctg-" + ctgId + " (APCG " + apcgId + ")");
				
				SimulatedAnnealingMapper saMapper;
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
				
				String[] parameters = new String[] {
						"linkBandwidth",
						"switchEBit",
						"linkEBit",
						"bufReadEBit",
						"bufWriteEBit",
						"routing",
						"seed"};
				String values[] = new String[] {
						Double.toString(linkBandwidth),
						Float.toString(switchEBit), Float.toString(linkEBit),
						Float.toString(bufReadEBit),
						Float.toString(bufWriteEBit),
						null,
						seed == null ? null : Long.toString(seed)};
				if (doRouting) {
					values[values.length - 2] = "true" + "-" + lts.toString();
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// SA with routing
					saMapper = new SimulatedAnnealingMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							true, lts, bufReadEBit,
							bufWriteEBit, switchEBit, linkEBit, seed);
				} else {
					values[values.length - 2] = "false";
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// SA without routing
					saMapper = new SimulatedAnnealingMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							switchEBit, linkEBit, seed);
				}
	
	//			// read the input data from a traffic.config file (NoCmap style)
	//			saMapper(
	//					"telecom-mocsyn-16tile-selectedpe.traffic.config",
	//					linkBandwidth);
				
				for (int k = 0; k < apcgTypes.size(); k++) {
					// read the input data using the Unified Framework's XML interface
					saMapper.parseApcg(apcgTypes.get(k), ctgTypes.get(k));
				}
				
	//			// This is just for checking that bbMapper.parseTrafficConfig(...)
	//			// and parseApcg(...) have the same effect
	//			bbMapper.printCores();
	
				String[] mappingXml = saMapper.map();
				File dir = new File(benchmarkFilePath + "ctg-" + ctgId);
				dir.mkdirs();
				String routing = "";
				if (doRouting) {
					routing = "_routing";
				}
				String mappingXmlFilePath = benchmarkFilePath + "ctg-" + ctgId
						+ File.separator + "mapping-" + apcgId + "_"
						+ saMapper.getMapperId() + routing + ".xml";
				PrintWriter pw = new PrintWriter(mappingXmlFilePath);
				logger.info("Saving the mapping XML file" + mappingXmlFilePath);
				pw.write(mappingXml[0]);
				pw.close();
	
				logger.info("The generated mapping is:");
				saMapper.printCurrentMapping();
				
				saMapper.analyzeIt();
			}
		};
		mapperInputProcessor.processInput(args);
	}
}
