package ro.ulbsibiu.acaps.mapper.es;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.LegalTurnSet;
import ro.ulbsibiu.acaps.mapper.sa.Core;
import ro.ulbsibiu.acaps.mapper.sa.SimulatedAnnealingMapper;
import ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor;
import ro.ulbsibiu.acaps.mapper.util.MathUtils;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;

/**
 * This {@link Mapper} is inspired from the {@link SimulatedAnnealingMapper}.
 * The difference is that it searches for the best mapping by generating all the
 * possible mappings.
 * 
 * <p>
 * Note that currently, this algorithm works only with M x N 2D mesh NoCs
 * </p>
 * 
 * @author cipi
 * 
 */
public class ExhaustiveSearchMapper extends BandwidthConstrainedEnergyAndPerformanceAwareMapper {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(ExhaustiveSearchMapper.class);
	
	private static final String MAPPER_ID = "es";

	private double bestCost = Float.MAX_VALUE;
	
	private String[] bestMapping = null;
	
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
	 */
	public ExhaustiveSearchMapper(String benchmarkName, String ctgId,
			String apcgId, String topologyName, String topologySize,
			File topologyDir, int coresNumber, double linkBandwidth,
			float switchEBit, float linkEBit) throws JAXBException {
		this(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, false,
				LegalTurnSet.WEST_FIRST, 1.056f, 2.831f, switchEBit, linkEBit);
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
	 * @throws JAXBException
	 */
	public ExhaustiveSearchMapper(String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir, int coresNumber,
			double linkBandwidth, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit,
			float switchEBit, float linkEBit) throws JAXBException {
		
		super(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, buildRoutingTable,
				legalTurnSet, bufReadEBit, bufWriteEBit, switchEBit, linkEBit);
		
	}
	
	@Override
	public String getMapperId() {
		return MAPPER_ID;
	}

	// TODO translate the following comment into English
	// (it describes how combinatorial arrangements of n elements taken as k can
	// be generated iteratively, in lexicographical order)
	//
	// generarea aranjamentelor in ordine
	// lexicografica printr-un procedeu iterativ. Se pleaca de la multimea
	// (vectorul) a=(1, 2, ...,
	// m).Fie un aranjament oarecare a=(a1, a2, ..., am). Pentru a se genera
	// succesorul
	// acestuia in ordine lexicografica se procedeaza astfel:
	// Se determina indicele i pentru care ai poate fi marit (cel mai mare
	// indice). Un element ai
	// nu poate fi marit daca valorile care sunt mai mari decit el respectiv
	// ai+1, ai+2, ..., n nu
	// sunt disponibile, adica apar pe alte pozitii in aranjament. Pentru a se
	// determina usor
	// elementele disponibile se introduce un vector DISP cu n elemente, astfel
	// incit DISP(i)
	// este 1 daca elemntul i apare in aranjamentul curent si 0 in caz contrar.
	// Se observa ca in momentul determinarii indicelui este necesar ca
	// elementul curent care
	// se doreste a fi marit trebuie sa se faca disponibil. Dupa ce acest
	// element a fost gasit,
	// acesta si elementele urmatoare se inlocuiesc cu cele mai mici numere
	// disponibile. In
	// cazul in care s-a ajuns la vectorul (n-m+1, ..., n-1, n) procesul de
	// generare al
	// aranjamentelor se opreste.
	// De exemplu, pentru n=5 si m=3 si a=(2 4 1) avem DISP=(0,0,1,0,1), iar
	// succesorii sai
	// sunt in ordine (2 4 1), (2 4 3), (2 4 5), (3 1 2), (3 1 4), s.a.m.d.
	
	private void init(int n, int[] a, boolean[] available) {
		for (int i = 0; i < a.length; i++) {
			a[i] = i;
			available[i] = false;
		}
		for (int i = a.length; i < n; i++) {
			available[i] = true;
		}
	}
	
	private boolean generate(int n, int[] a, boolean[] available) {
		int i = a.length - 1;
		boolean found = false;
		while (i >= 0 && !found) {
			available[a[i]] = true;
			int j = a[i] + 1;
			while (j < n && !found) {
				if (available[j]) {
					a[i] = j;
					available[j] = false;
					int k = 0;
					for (int l = i + 1; l < a.length; l++) {
						while(!available[k]) {
							k++;
						}
						a[l] = k;
						available[k] = false;
					}
					found = true;
				} else {
					j++;
				}
			}
			i--;
		}
		return found;
	}
	
	/**
	 * Computes n! / (n - k)!, where n is the number of NoC nodes and k is the
	 * number of cores. This number is the total number of possible mappings.
	 * 
	 * @param i
	 *            the nodes number
	 * @return #nodes! / (#nodes - #cores)!
	 */
	private long countPossibleMappings(int i) {
		long p = 1;
		if (i > nodes.length - cores.length) {
			p = i * countPossibleMappings(i - 1);
		}
		return p;
	}
	
	/**
	 * Generates all possible mappings
	 * 
	 * @param possibleMappings the number of possible mappings
	 * 
	 * @see #countPossibleMappings(int)
	 */
	private void searchExhaustively(long possibleMappings) {
		boolean initialized = false;
		int[] a = new int[cores.length];
		boolean[] available = new boolean[nodes.length];
		boolean found = false;
		long counter = 0;
		final int STEP = 10;
		int stepCounter = 0;

		long userStart = 0;
		do {
			if (logger.isDebugEnabled()) {
				userStart = System.nanoTime();
			}
			if (!initialized) {
				init(nodes.length, a, available);
				found = true;
				initialized = true;
			} else {
				found = generate(nodes.length, a, available);
			}
			if (found) {
				counter++;
				if (logger.isDebugEnabled()) {
					logger.debug("Generated mapping number " + counter + " (of "
							+ possibleMappings + " possible mappings). "
							+ (counter * 100.0 / possibleMappings)
							+ "% of the entire search space currently explored.");
				} else {
					if (MathUtils.definitelyGreaterThan((float)(counter * 100.0 / possibleMappings), stepCounter)) {
						logger.info("Generated mapping number " + counter + " (of "
								+ possibleMappings + " possible mappings). "
								+ (counter * 100.0 / possibleMappings)
								+ "% of the entire search space currently explored.");
						stepCounter += STEP;
						logger.info("Next message will be show after a " + stepCounter + "% search progress");
					}
				}
				if (logger.isDebugEnabled()) {
					StringBuffer sb = new StringBuffer();
					for (int j = 0; j < a.length; j++) {
						sb.append(a[j] + " ");
					}
					logger.debug(sb);
				}
				
				for (int i = 0; i < nodes.length; i++) {
					nodes[i].setCore("-1");
				}
				for (int i = 0; i < cores.length; i++) {
					cores[i].setNodeId(-1);
				}
				for (int i = 0; i < a.length; i++) {
					nodes[a[i]].setCore(Integer.toString(i));
					cores[i].setNodeId(a[i]);
				}
				double cost = calculateTotalCost();
				if (MathUtils.definitelyLessThan((float) cost, (float) bestCost)) {
					bestCost = cost;
					bestMapping = new String[nodes.length];
					for (int i = 0; i < nodes.length; i++) {
						bestMapping[i] =  nodes[i].getCore();
					}
				}
			}
			if (logger.isDebugEnabled()) {
				long userEnd = System.nanoTime();
				logger.debug("Mapping generated in " + (userEnd - userStart) / 1.0e6 + " ms");
			}
		} while (found);
		
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore("-1");
		}
		for (int i = 0; i < cores.length; i++) {
			cores[i].setNodeId(-1);
		}
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore(bestMapping[i]);
			if (!"-1".equals(bestMapping[i])) {
				cores[Integer.valueOf(bestMapping[i])].setNodeId(i);
			}
		}
		
		if (buildRoutingTable) {
			programRouters();
		}
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
		long possibleMappings = countPossibleMappings(nodes.length);
		logger.info("This search space contains " + nodes.length + "! / " + "("
				+ nodes.length + " - " + cores.length + ")! = "
				+ possibleMappings + " possible mappings!");
	}

	@Override
	protected int doMapping() {
		long possibleMappings = countPossibleMappings(nodes.length);
		searchExhaustively(possibleMappings);
		return 1;
	}

	@Override
	protected void doBeforeSavingMapping() {
		;
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
		final double linkBandwidth = 256E9;
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
				logger.info("Using an Exhaustive search mapper for "
						+ benchmarkFilePath + "ctg-" + ctgId + " (APCG " + apcgId + ")");
				
				ExhaustiveSearchMapper esMapper;
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
						"routing"};
				String values[] = new String[] {
						Double.toString(linkBandwidth),
						Float.toString(switchEBit), Float.toString(linkEBit),
						Float.toString(bufReadEBit),
						Float.toString(bufWriteEBit),
						null};
				if (doRouting) {
					values[values.length - 1] = "true" + "-" + lts.toString();
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// with routing
					esMapper = new ExhaustiveSearchMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							true, lts, bufReadEBit,
							bufWriteEBit, switchEBit, linkEBit);
				} else {
					values[values.length - 1] = "false";
					MapperDatabase.getInstance().setParameters(parameters, values);
					
					// without routing
					esMapper = new ExhaustiveSearchMapper(
							benchmarkName, ctgId, apcgId,
							topologyName, meshSize, new File(
									topologyDir), cores, linkBandwidth,
							switchEBit, linkEBit);
				}
	
	//			// read the input data from a traffic.config file (NoCmap style)
	//			saMapper(
	//					"telecom-mocsyn-16tile-selectedpe.traffic.config",
	//					linkBandwidth);
				
				for (int k = 0; k < apcgTypes.size(); k++) {
					// read the input data using the Unified Framework's XML interface
					esMapper.parseApcg(apcgTypes.get(k), ctgTypes.get(k));
				}
				
	//			// This is just for checking that bbMapper.parseTrafficConfig(...)
	//			// and parseApcg(...) have the same effect
	//			bbMapper.printCores();
	
				String[] mappingXml = esMapper.map();
				File dir = new File(benchmarkFilePath + "ctg-" + ctgId);
				dir.mkdirs();
				String routing = "";
				if (doRouting) {
					routing = "_routing";
				}
				String mappingXmlFilePath = benchmarkFilePath + "ctg-" + ctgId
						+ File.separator + "mapping-" + apcgId + "_"
						+ esMapper.getMapperId() + routing + ".xml";
				PrintWriter pw = new PrintWriter(mappingXmlFilePath);
				logger.info("Saving the mapping XML file" + mappingXmlFilePath);
				pw.write(mappingXml[0]);
				pw.close();
	
				logger.info("The generated mapping is:");
				esMapper.printCurrentMapping();
				
				esMapper.analyzeIt();
				
			}
		};
		mapperInputProcessor.processInput(args);
	}
}
