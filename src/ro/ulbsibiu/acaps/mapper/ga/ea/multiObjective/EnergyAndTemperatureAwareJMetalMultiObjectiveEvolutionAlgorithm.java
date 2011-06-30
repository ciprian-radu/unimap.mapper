package ro.ulbsibiu.acaps.mapper.ga.ea.multiObjective;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBException;

import jmetal.base.Problem;
import jmetal.base.Solution;
import jmetal.base.SolutionSet;
import jmetal.base.Variable;
import jmetal.base.operator.mutation.SwapMutation;
import jmetal.base.operator.selection.Selection;
import jmetal.base.operator.selection.SelectionFactory;
import jmetal.base.solutionType.PermutationSolutionType;
import jmetal.base.variable.Permutation;
import jmetal.util.JMException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.CoreType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.TaskType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.ga.ea.EnergyAwareJMetalEvolutionaryAlgorithmMapper;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.JMetalEvolutionaryAlgorithmMapper;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.JMetalEvolutionaryAlgorithmMapper.JMetalAlgorithm;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.TrackedAlgorithm;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.MappingSimilarityCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.PositionBasedCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.mutation.OsaMutation;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.nsgaII.NSGAII;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.spea2.SPEA2;
import ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor;
import ro.ulbsibiu.acaps.noc.xml.link.LinkType;
import ro.ulbsibiu.acaps.noc.xml.node.NodeType;

/**
 * <p>
 * This class is created to optimize NoC application mapping problem in respect
 * of two objectives. One is Energy that is already implemented in
 * {@link JMetalEvolutionaryAlgorithmMapper}. Second objective is temperature.
 * To calculate the temperature we use the <a href =
 * "http://lava.cs.virginia.edu/HotSpot/">Hotspot</a> tool. Hotspot requires the
 * NoC floorplan (flp) and power trace file (ptrace). We use very simple
 * floorplan. We divide the area into equal size block. Each block is large
 * enough to contain the biggest core and a router.
 * </p>
 * 
 * <p>
 * ptrace file contains total power consumed by the core. The ptrace file is
 * dynamically written according to the mapping (that means which core is mapped
 * onto which block/tile). Each core consumes a certain power when executing a
 * task. This information is in the (Application Characterization Graph) APCG.
 * If a core runs more than one task, then we will sum the powers for each task.
 * </p>
 * 
 * <p>
 * Hotsopt writes a file with the final steady state temperatures into a .steady
 * file. The file is parsed and tile temperature is retrieved. Using the
 * temperature, the second objective is calculated. This means those tiles that
 * are having high temperature should be kept apart from each other. Note that
 * the second objective is in contradiction with the first (energy objective).
 * </p>
 * 
 * @author shaikat
 * @author cradu (code review and integration)
 */
public class EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm
		extends EnergyAwareJMetalEvolutionaryAlgorithmMapper {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm.class);

	private static final String MAPPER_ID_PREFIX = "MO-";
	
	/** path to Hotspot */
	public static final String HOTSPOT_PATH = "./lib/hotspot/";
	
	/** this unique identifier helps having unique Hotspot files when we do multiple simulations at a time */
	private static final long UID = System.currentTimeMillis();

	private CorePower[] corePower;

	/** solutions will be stored here (Pareto front) */
	private SolutionSet solutions;

	/** track which one is the current solution */
	private int trackCurrentSolution = 0;

	/** counts how many cores were parsed from the parsed APCGs */
	private int previousCorePowerCount = 0;
	
	/** the number of 2D mesh NoC rows */
	private int noOfRows;
	
	/** the number of 2D mesh NoC columns */
	private int noOfCols;
	
	public EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm(
			String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir,
			int coresNumber, double linkBandwidth, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit,
			float switchEBit, float linkEBit, Long seed,
			JMetalAlgorithm jMetalAlgorithm, Integer populationSize,
			Integer generations, Class<?> crossoverClass,
			Integer crossoverProbability, Class<?> mutationClass,
			Integer mutationProbability) throws JAXBException {

		super(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, buildRoutingTable,
				legalTurnSet, bufReadEBit, bufWriteEBit, switchEBit, linkEBit,
				seed, jMetalAlgorithm, populationSize, generations,
				crossoverClass, crossoverProbability, mutationClass,
				mutationProbability);

		this.corePower = new CorePower[coresNumber];
		for (int i = 0; i < coresNumber; i++) {
			corePower[i] = new CorePower(Integer.MIN_VALUE, null,
					Integer.MIN_VALUE);
		}
		
		String[] split = topologySize.split("x");
		logger.assertLog(split != null && split.length == 2, "Incorrect topology size " + topologySize + "!");
		try {
			noOfCols = Integer.valueOf(split[0]);
			noOfRows = Integer.valueOf(split[1]);
		} catch (NumberFormatException e) {
			logger.error(e);
		}
	}

	public EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm(
			String benchmarkName, String ctgId, String apcgId,
			String topologyName, String topologySize, File topologyDir,
			int coresNumber, double linkBandwidth, float switchEBit,
			float linkEBit, Long seed, JMetalAlgorithm jMetalAlgorithm,
			Integer populationSize, Integer generations,
			Class<?> crossoverClass, Integer crossoverProbability,
			Class<?> mutationClass, Integer mutationProbability)
			throws JAXBException {

		super(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, false,
				LegalTurnSet.WEST_FIRST, 1.056f, 2.831f, switchEBit, linkEBit,
				seed, jMetalAlgorithm, populationSize, generations,
				crossoverClass, crossoverProbability, mutationClass,
				mutationProbability);

		this.corePower = new CorePower[coresNumber];
		for (int i = 0; i < coresNumber; i++) {
			corePower[i] = new CorePower(Integer.MIN_VALUE, null,
					Integer.MIN_VALUE);
		}
		
		String[] split = topologySize.split("x");
		logger.assertLog(split != null && split.length == 2, "Incorrect topology size " + topologySize + "!");
		try {
			noOfCols = Integer.valueOf(split[0]);
			noOfRows = Integer.valueOf(split[1]);
		} catch (NumberFormatException e) {
			logger.error(e);
		}
	}

	@Override
	public String getMapperId() {
		return MAPPER_ID_PREFIX + super.getMapperId();
	}

	/**
	 * @param apcg
	 * @param ctg
	 * 
	 *            this method calculate the total power consumed by a core from
	 *            apcg
	 * 
	 */
	public void parseApcgForPower(ApcgType apcg, CtgType ctg) {

		logger.assertLog(apcg != null, "The APCG cannot be null");
		logger.assertLog(ctg != null, "The CTG cannot be null");

		// we use previousCoreCount to shift the cores from each APCG
		List<CoreType> coreList = apcg.getCore();
		for (int i = 0; i < coreList.size(); i++) {
			CoreType coreType = coreList.get(i);
			List<TaskType> taskList = coreType.getTask();
			for (int j = 0; j < taskList.size(); j++) {
				TaskType taskType = taskList.get(j);

				corePower[previousCorePowerCount
						+ Integer.valueOf(coreType.getUid())].setApcgId(apcg
						.getId());
				corePower[previousCorePowerCount
						+ Integer.valueOf(coreType.getUid())].setCoreId(Integer
						.valueOf(coreType.getUid()));
				corePower[previousCorePowerCount
						+ Integer.valueOf(coreType.getUid())]
						.setTotalComsumedPower(corePower[previousCorePowerCount
								+ Integer.valueOf(coreType.getUid())]
								.getTotalConsumedPower() + taskType.getPower());
			}
		}
		previousCorePowerCount += coreList.size();
	}

	@Override
	protected void doBeforeMapping() {
		computeCoreNeighbors();
		computeCoreToCommunicationPDF();
		computeCoresCommunicationPDF();
		if (crossover instanceof MappingSimilarityCrossover) {
			MappingSimilarityCrossover msCrossover = (MappingSimilarityCrossover) crossover;
			msCrossover.setCores(cores);
			msCrossover.setCoreNeighbors(coreNeighbors);
			msCrossover.setNodes(nodes);
			msCrossover.setNodeNeighbors(nodeNeighbors);
		}
		if (mutation instanceof OsaMutation) {
			OsaMutation osaMutation = (OsaMutation) mutation;
			osaMutation.setCores(cores);
			osaMutation.setCoreNeighbors(coreNeighbors);
			osaMutation.setNodes(nodes);
			osaMutation.setNodeNeighbors(nodeNeighbors);
			osaMutation.setInitialTemperature(1);
			osaMutation.setCoreToCommunication(coreToCommunication);
			osaMutation.setCoresCommunicationPDF(coresCommunicationPDF);
			osaMutation.setTotalToCommunication(totalToCommunication);
		}

		try {
			problem = new EnergyAndTemperatureAwareMappingProblem(this,
					nodes.length, cores.length, rand);

			switch (jMetalAlgorithm) {
			case NSGAII:
				algorithm = new NSGAII(problem);
				break;
			case SPEA2:
				algorithm = new SPEA2(problem);
				algorithm.setInputParameter("archiveSize", 100);
				break;
			default:
				logger.fatal("Unknown jMetal algorithm: " + jMetalAlgorithm
						+ "! I can work only with NSGAII or SPEA2. Exiting...");
				System.exit(-1);
				break;
			}

			algorithm.setInputParameter("populationSize", populationSize);
			algorithm.setInputParameter("maxEvaluations", generations * populationSize);
			((TrackedAlgorithm) algorithm).setAlgorithmTracker(this);

			// crossover =
			// CrossoverFactory.getCrossoverOperator("PMXCrossover");
			crossover.setParameter("probability", crossoverProbability / 100.0);
			// crossover.setParameter("distributionIndex", 20.0);

			// mutation = MutationFactory.getMutationOperator("SwapMutation");
			mutation.setParameter("probability", mutationProbability / 100.0);
			// mutation.setParameter("distributionIndex", 20.0);

			selection = (Selection) SelectionFactory
					.getSelectionOperator("BinaryTournament");
			// selection =
			// SelectionFactory.getSelectionOperator("DifferentialEvolutionSelection");

			algorithm.addOperator("crossover", crossover);
			algorithm.addOperator("mutation", mutation);
			algorithm.addOperator("selection", selection);
		} catch (ClassNotFoundException e) {
			logger.error(e);
		} catch (JMException e) {
			logger.error(e);
		}
	}

	@Override
	protected int doMapping() {
		int totalNumberOfSolutions = 0;
		try {
			solutions = algorithm.execute();
			// Solution S = new Solution(population.get(0));
			// solution = ((Permutation) S.getDecisionVariables()[0]).vector_;
			logger.info("Variables values have been writen to file VAR");
			solutions.printVariablesToFile("VAR");
			logger.info("Objectives values have been writen to file FUN");
			solutions.printObjectivesToFile("FUN");

			totalNumberOfSolutions = solutions.size();

			/* soultions of pareto front is stored into solution array */
			for (int i = 0; i < solutions.size(); i++) {
				logger.info("Best mapping " + i + " has energy " + solutions.get(i).getObjective(0) + " picoJoule");
				logger.info("Best mapping " + i + " has temperature " + 1 / solutions.get(i).getObjective(1) + " Kelvin");
			}

		} catch (ClassNotFoundException e) {
			logger.error(e);
		} catch (JMException e) {
			logger.error(e);
		}
		return totalNumberOfSolutions;
	}

	@Override
	protected void doBeforeSavingMapping() {
		// return the current mapping at this moment
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore("-1");
		}
		for (int i = 0; i < cores.length; i++) {
			cores[i].setNodeId(-1);
		}
		for (int i = 0; i < nodes.length; i++) {
			int[] solution = ((Permutation) solutions.get(trackCurrentSolution).getDecisionVariables()[0]).vector_;
			String coreAsString = Integer.toString(solution[i]);
			nodes[i].setCore(coreAsString);
			if (!"-1".equals(coreAsString)) {
				cores[Integer.valueOf(coreAsString)].setNodeId(i);
			}
		}
		trackCurrentSolution += 1;
		if (buildRoutingTable) {
			programRouters();
		}
	}

	/**
	 * Dynamically writes the ptrace Hotspot file. The file has specific format.
	 * The file is written by maintaining the format. The first line has the NoC
	 * tiles and the second puts the power consumed by the cores mapped onto
	 * these tiles (in Watts).
	 * 
	 * @param individual the mapping
	 */
	private void writePtraceFile(int[] individual) {
		double[] powerConsumedByCores = new double[individual.length];
		for (int i = 0; i < individual.length; i++) {
			if (individual[i] != -1) {
				powerConsumedByCores[i] = corePower[individual[i]].getTotalConsumedPower();
			} else {
				powerConsumedByCores[i] = 0.0;
			}
		}

		try {
			String fileName = HOTSPOT_PATH + benchmarkName + "-ctg-" + ctgId
					+ "-mapping-" + apcgId + "_" + getMapperId() + "-"
					+ buildRoutingTable + "-" + legalTurnSet + "-" + noOfCols
					+ "x" + noOfRows + UID;
			FileOutputStream stream = new FileOutputStream(fileName + ".ptrace");
			OutputStreamWriter out = new OutputStreamWriter(stream, "UTF-8");

			for (int i = 0; i < powerConsumedByCores.length; i++) {
				if (i < powerConsumedByCores.length - 1) {
					out.write("Tile" + i + "\t");
				}
				else {
					out.write("Tile" + i);
				}
			}
			out.write(System.getProperty("line.separator"));
			for (int i = 0; i < powerConsumedByCores.length; i++) {
				if (i < powerConsumedByCores.length - 1) {
					out.write(Double.toString(powerConsumedByCores[i]) + "\t");
				}
				else {
					out.write(Double.toString(powerConsumedByCores[i]));
				}
			}
			out.write(System.getProperty("line.separator"));

			out.flush();
			stream.flush();
			out.close();
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Calls Hotspot and waits until it finishes running. Hotspot writes the
	 * .steady file. As input it requires .flp and .ptrace files.
	 * 
	 * @see #HOTSPOT_PATH
	 * 
	 * @param individual the mapping
	 */
	private void runHotspot(int[] individual) {
		try {
			String flpFilename = HOTSPOT_PATH + "flp/" + noOfCols + "x" + noOfRows + ".flp";
			String ptraceFileName = HOTSPOT_PATH + benchmarkName + "-ctg-"
					+ ctgId + "-mapping-" + apcgId + "_" + getMapperId() + "-"
					+ buildRoutingTable + "-" + legalTurnSet + "-" + noOfCols
					+ "x" + noOfRows + UID + ".ptrace";
			String steadyStateFileName = HOTSPOT_PATH + benchmarkName + "-ctg-"
					+ ctgId + "-mapping-" + apcgId + "_" + getMapperId() + "-"
					+ buildRoutingTable + "-" + legalTurnSet + "-" + noOfCols
					+ "x" + noOfRows + UID + ".steady";

			String hotspotCmd = HOTSPOT_PATH + "hotspot -c " + HOTSPOT_PATH
					+ "hotspot.config -f " + flpFilename + " -p "
					+ ptraceFileName + " -steady_file " + steadyStateFileName;
			if (logger.isDebugEnabled()) {
				logger.debug("Calling HotSpot with command: " + hotspotCmd);
			}
			Process p = Runtime.getRuntime().exec(hotspotCmd);

			p.waitFor();
			p.destroy();
			
			File f = new File(ptraceFileName);
			boolean deleted = f.delete();
			if (logger.isDebugEnabled()) {
				if (deleted) {
					logger.debug("Deleted file " + f.getName());
				} else {
					logger.debug("Could not delete file " + f.getName());
				}
			}
		} catch (InterruptedException e) {
			logger.error(e);
			System.exit(0);
		} catch (IOException e) {
			logger.error(e);
			System.exit(0);
		}

	}

	/**
	 * The method parses the Hotspot steady file and retrieves the temperature
	 * of each tile. The file has specific format also.
	 * 
	 * @param individual
	 *            the mapping
	 * @return the temperatures
	 */
	private double[] parseSteadyFile(int[] individual) {
		double[] temperatures = new double[individual.length];

		try {
			String fileName = HOTSPOT_PATH + benchmarkName + "-ctg-" + ctgId
					+ "-mapping-" + apcgId + "_" + getMapperId() + "-"
					+ buildRoutingTable + "-" + legalTurnSet + "-" + noOfCols
					+ "x" + noOfRows + UID + ".steady";

			FileInputStream fstream = new FileInputStream(fileName);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;

			String tem[] = new String[2];
			int i = 0;
			while ((strLine = br.readLine()) != null) {
				if (strLine.startsWith("Tile")) {
					tem = strLine.split("\t");
					temperatures[i++] = Double.parseDouble(tem[1]);
				} else {
					break;
				}
			}

			in.close();
			
			File f = new File(fileName);
			boolean deleted = f.delete();
			if (logger.isDebugEnabled()) {
				if (deleted) {
					logger.debug("Deleted file " + f.getName());
				} else {
					logger.debug("Could not delete file " + f.getName());
				}
			}
		} catch (Exception e) {
			logger.error(e);
			System.exit(0);
		}

		return temperatures;
	}

	private double calculateSecondFitness(int[] individual) {
		logger.assertLog(individual != null,
				"Attempting to compute fitness for a NULL individual!");
		logger.assertLog(individual.length == nodes.length,
				"The individual doesn't contains " + nodes.length + " genes!");

		double temperatureOfCores[] = new double[individual.length];

		writePtraceFile(individual);
		runHotspot(individual);
		temperatureOfCores = parseSteadyFile(individual);

		double maxTemperature = Double.MIN_VALUE;

		// here we take 4 adjacent (2 in X direction and 2 in Y direction) cores
		// to measure the temperature of the block. All temperature of 4 cores
		// are summed up. Temperature of block need to be minimized
		for (int i = 0; i < individual.length; i++) {
			int currentCol = i % hSize;
			int currentRow = i / hSize;
			if (currentCol + 1 < noOfCols && currentRow + 1 < noOfRows) {
				double totalTemperature = temperatureOfCores[i]
						+ temperatureOfCores[i + 1]
						+ temperatureOfCores[(i + noOfCols)]
						+ temperatureOfCores[(i + noOfCols) + 1];
				if (totalTemperature >= maxTemperature) {
					maxTemperature = totalTemperature;
				}
			}
		}

		return 1 / maxTemperature;
	}

	public void analyzeIt (int solutionIndex, double onePerMaxTemperatureSubmatrix) {
	    logger.info("Verify the communication load of each link...");
	    String bandwidthRequirements;
	    
		generateLinkUsageList();

		long[] usedBandwidth = new long[links.length];
		
		for (int i = 0; i < links.length; i++) {
			usedBandwidth[i] = 0;
		}

		for (int src = 0; src < nodes.length; src++) {
			for (int dst = 0; dst < nodes.length; dst++) {
	            if (src == dst) {
	                continue;
	            }
	            int srcProc = Integer.valueOf(nodes[src].getCore());
	            int dstProc = Integer.valueOf(nodes[dst].getCore());
	            if (srcProc > -1 && dstProc > -1) {
	            	long commLoad = cores[srcProc].getToBandwidthRequirement()[dstProc];
		            if (commLoad == 0) {
		                continue;
		            }
		            NodeType currentNode = nodes[src];
		            while (Integer.valueOf(currentNode.getId()) != dst) {
		                int linkId = routingTables[Integer.valueOf(currentNode.getId())][src][dst];
		                LinkType link = links[linkId];
						String node = "-1";
						// we work with with bidirectional links
						if (currentNode.getId().equals(link.getFirstNode())) {
							node = link.getSecondNode();
						} else {
							if (currentNode.getId().equals(link.getSecondNode())) {
								node = link.getFirstNode();
							}
						}
						currentNode = nodes[Integer.valueOf(node)];
		                usedBandwidth[linkId] += commLoad;
		            }
	            }
	        }
	    }
	    //check for the overloaded links
	    int violations = 0;
	    long maxBandwidthRequirement = Long.MIN_VALUE;
		for (int i = 0; i < links.length; i++) {
			logger.info("Link " + i + " has " + links[i].getBandwidth() + " b/s bandwidth");
			logger.info("The application requires link " + i + " " + usedBandwidth[i] + " b/s bandwidth");
	        if (usedBandwidth[i] > links[i].getBandwidth()) {
	        	logger.info("Link " + i + " is overloaded: " + usedBandwidth[i] + " b/s > "
	                 + links[i].getBandwidth() + " b/s");
	            violations ++;
	        }
	        if (usedBandwidth[i] > maxBandwidthRequirement) {
	        	maxBandwidthRequirement = usedBandwidth[i];
	        }
	    }
	    
	    if (violations == 0) {
	    	logger.info("Succes");
	    	bandwidthRequirements = "Succes";
	    }
	    else {
	    	logger.info("Fail");
	    	bandwidthRequirements = "Fail";
	    }
	    
	    logger.info("Maximum bandwidth requirement is " + maxBandwidthRequirement + " b/s");
	    
	    if (logger.isDebugEnabled()) {
		    logger.debug("Energy consumption estimation ");
		    logger.debug("(note this are not exact numbers, but serve as a relative energy indication) ");
		    logger.debug("Energy consumed in link is " + calculateLinkEnergy());
		    logger.debug("Energy consumed in switch is " + calculateSwitchEnergy());
		    logger.debug("Energy consumed in buffer is " + calculateBufferEnergy());
	    }
	    double energy = calculateCommunicationEnergy();
	    logger.info("Total communication energy consumption is " + energy);
	    
		MapperDatabase.getInstance().setOutputs(
				new String[] { "bandwidthRequirements" + "_" + Integer.toString(solutionIndex), "maxBandwidthRequirement" + "_" + Integer.toString(solutionIndex), "energy" + "_" + Integer.toString(solutionIndex), "onePerMaxTemperatureSubmatrix" + "_" + Integer.toString(solutionIndex) },
				new String[] { bandwidthRequirements, Long.toString(maxBandwidthRequirement), Double.toString(energy), Double.toString(1 / onePerMaxTemperatureSubmatrix) });
	}

	@Override
	public void processIntermediateSolution(String parameterName, String parameterValue, Solution solution) {
		int[] permutation = ((Permutation) solution.getDecisionVariables()[0]).vector_;
		logger.info(parameterName + " " + parameterValue);
		if (logger.isDebugEnabled()) {
			logger.debug("Processing an intermediate solution");
		}
		
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore("-1");
		}
		for (int i = 0; i < cores.length; i++) {
			cores[i].setNodeId(-1);
		}
		for (int i = 0; i < nodes.length; i++) {
			String coreAsString = Integer.toString(permutation[i]);
			nodes[i].setCore(coreAsString);
			if (!"-1".equals(coreAsString)) {
				cores[Integer.valueOf(coreAsString)].setNodeId(i);
			}
		}
		
		if (logger.isDebugEnabled()) {
			logger.debug("Verify the communication load of each link...");
		}

	    double energy = calculateCommunicationEnergy();
	    if (logger.isDebugEnabled()) {
	    	logger.debug("Total communication energy consumption is " + energy);
	    }
	    
		MapperDatabase.getInstance().setOutputs(
				new String[] { parameterName + "_" + "energy" + "_" + "onePerMaxTemperatureSubmatrix" },
				new String[] { parameterValue.toString() + "_" + Double.toString(energy) + "_" + Double.toString(solution.getObjective(1)) });
	}
	
	public static void main(String args[]) throws TooFewNocNodesException,
			IOException, JAXBException, ParseException {
		final float switchEBit = 0.284f;
		final float linkEBit = 0.449f;
		final float bufReadEBit = 1.056f;
		final float bufWriteEBit = 2.831f;
		
		final String cliArgs[] = args;
		
		MapperInputProcessor mapperInputProcessor = new MapperInputProcessor() {
		
			/* (non-Javadoc)
			 * @see ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor#useMapper(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.util.List, java.util.List, boolean, ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.LegalTurnSet, double, java.lang.Long)
			 */
			@Override
			public void useMapper(String benchmarkFilePath,
					String benchmarkName, String ctgId, String apcgId,
					List<CtgType> ctgTypes, List<ApcgType> apcgTypes,
					boolean doRouting, LegalTurnSet lts, double linkBandwidth,
					Long seed) throws JAXBException, TooFewNocNodesException,
					FileNotFoundException {
				try {
					CommandLineParser parser = new PosixParser();
					CommandLine cmd = parser.parse(getCliOptions(), cliArgs);
					JMetalAlgorithm jMetalAlgorithm = null;
					jMetalAlgorithm = JMetalAlgorithm.valueOf(cmd
							.getOptionValue("j",
									JMetalAlgorithm.EGA.toString()));
		
					logger.info("Using "
							+ jMetalAlgorithm
							+ " (an energy- and temepature- aware, jMetal, evolutionary algorithm) mapper for "
							+ benchmarkFilePath + "ctg-" + ctgId + " (APCG "
							+ apcgId + ")");
		
					EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm eaJMetalMapper;
					int cores = 0;
					for (int k = 0; k < apcgTypes.size(); k++) {
						cores += apcgTypes.get(k).getCore().size();
					}
					int hSize = (int) Math.ceil(Math.sqrt(cores));
					hSize = Math.max(2, hSize); // using at least a 2x2 2D mesh
					String meshSize;
					int nodes;
					// we allow rectangular 2D meshes as well
					if (hSize * (hSize - 1) >= cores) {
						meshSize = hSize + "x" + (hSize - 1);
						nodes = hSize * (hSize - 1);
					} else {
						meshSize = hSize + "x" + hSize;
						nodes = hSize * hSize;
					}
					logger.info("The algorithm has "
							+ cores
							+ " cores to map => working with a 2D mesh of size "
							+ meshSize);
					// working with a 2D mesh topology
					String topologyName = "mesh2D";
					String topologyDir = ".." + File.separator + "NoC-XML"
							+ File.separator + "src" + File.separator + "ro"
							+ File.separator + "ulbsibiu" + File.separator
							+ "acaps" + File.separator + "noc" + File.separator
							+ "topology" + File.separator + topologyName
							+ File.separator + meshSize;
		
					Integer populationSize = null;
					Integer generations = null;
					Class<?> crossoverClass = null;
					Integer crossoverProbability = null;
					Class<?> mutationClass = null;
					Integer mutationProbability = null;
					if (cmd.hasOption("p")) {
						populationSize = Integer.valueOf(cmd
								.getOptionValue("p"));
					}
					int defaultGenerationsNumber;
					// Bellow is the number of evaluations made by OSA, by
					// default (i.e., an initial temperature of 1)
					double osaEvaluations = 33 * cores
							* (2 * nodes - cores - 1) + 1;
					if (populationSize == null) {
						defaultGenerationsNumber = (int) Math
								.ceil(osaEvaluations / POPULATION_SIZE);
					} else {
						defaultGenerationsNumber = (int) Math
								.ceil(osaEvaluations / populationSize);
					}
					generations = Integer.valueOf(cmd.getOptionValue("g",
							Integer.toString(defaultGenerationsNumber)));
					try {
						String crossoverClassString = cmd.getOptionValue("xc",
								PositionBasedCrossover.class.getName());
						crossoverClass = Class.forName(crossoverClassString);
					} catch (ClassNotFoundException e) {
						logger.error(e);
					}
					logger.info("Using a " + crossoverClass.getName()
							+ " crossover");
					if (cmd.hasOption("x")) {
						crossoverProbability = Integer.valueOf(cmd
								.getOptionValue("x"));
					}
					try {
						String mutationClassString = cmd.getOptionValue("mc",
								SwapMutation.class.getName());
						mutationClass = Class.forName(mutationClassString);
					} catch (ClassNotFoundException e) {
						logger.error(e);
					}
					logger.info("Using a " + mutationClass.getName()
							+ " mutation");
					if (cmd.hasOption("m")) {
						mutationProbability = Integer.valueOf(cmd
								.getOptionValue("m"));
					}
					int defaultMutationProbability = (int) Math
							.floor(100.0 / nodes);
					if (mutationProbability == null) {
						mutationProbability = defaultMutationProbability;
					}
		
					String[] parameters = new String[] {
							"linkBandwidth",
							"switchEBit", 
							"linkEBit", 
							"bufReadEBit",
							"bufWriteEBit", 
							"routing", 
							"seed",
							"populationSize", 
							"generations",
							"crossoverProbability", 
							"mutationClass",
							"mutationProbability", };
					String values[] = new String[] {
							Double.toString(linkBandwidth),
							Float.toString(switchEBit),
							Float.toString(linkEBit),
							Float.toString(bufReadEBit),
							Float.toString(bufWriteEBit),
							null,
							seed == null ? null : Long.toString(seed),
							populationSize == null ? null : Integer
									.toString(populationSize),
							generations == null ? null : Integer
									.toString(generations),
							crossoverProbability == null ? null : Integer
									.toString(crossoverProbability),
							mutationClass == null ? null : mutationClass
									.getName(),
							mutationProbability == null ? null : Integer
									.toString(mutationProbability), };
					if (doRouting) {
						values[values.length - 7] = "true" + "-" + lts.toString();;
						MapperDatabase.getInstance().setParameters(parameters,
								values);
		
						// with routing
						eaJMetalMapper = new EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm(
								benchmarkName, ctgId, apcgId, topologyName,
								meshSize, new File(topologyDir), cores,
								linkBandwidth, true, lts,
								bufReadEBit, bufWriteEBit, switchEBit,
								linkEBit, seed, jMetalAlgorithm,
								populationSize, generations, crossoverClass,
								crossoverProbability, mutationClass,
								mutationProbability);
					} else {
						values[values.length - 7] = "false" + "-" + lts.toString();;
						MapperDatabase.getInstance().setParameters(parameters,
								values);
		
						// without routing
						eaJMetalMapper = new EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm(
								benchmarkName, ctgId, apcgId, topologyName,
								meshSize, new File(topologyDir), cores,
								linkBandwidth, switchEBit, linkEBit, seed,
								jMetalAlgorithm, populationSize, generations,
								crossoverClass, crossoverProbability,
								mutationClass, mutationProbability);
					}
		
					// // read the input data from a traffic.config file (NoCmap
					// style)
					// eagaMapper(
					// "telecom-mocsyn-16tile-selectedpe.traffic.config",
					// linkBandwidth);
		
					for (int k = 0; k < apcgTypes.size(); k++) {
						// read the input data using the Unified Framework's XML
						// interface
						eaJMetalMapper.parseApcg(apcgTypes.get(k), ctgTypes.get(k));
						eaJMetalMapper.parseApcgForPower(apcgTypes.get(k),
								ctgTypes.get(k));
		
					}
		
					// // This is just for checking that
					// bbMapper.parseTrafficConfig(...)
					// // and parseApcg(...) have the same effect
					// eagaMapper.printCores();
		
					String[] mappingXml = eaJMetalMapper.map();
					String routing = "";
					if (doRouting) {
						routing = "_routing";
					}
					String mappingXmlFilePath = benchmarkFilePath + "ctg-"
							+ ctgId + File.separator + "mapping-" + apcgId
							+ "_" + eaJMetalMapper.getMapperId() + routing;
					File f = new File(mappingXmlFilePath);
					f.mkdirs();
					for (int i = 0; i < mappingXml.length; i++) {
						PrintWriter pw = new PrintWriter(mappingXmlFilePath + File.separator + i + ".xml");
						logger.info("Saving the mapping XML file" + mappingXmlFilePath + File.separator + i + ".xml");
						pw.write(mappingXml[i]);
						pw.close();
						
						if (i == 0) {
							eaJMetalMapper.trackCurrentSolution = 0;
						}
						eaJMetalMapper.doBeforeSavingMapping();
						
						logger.info("The generated mapping number " + i + " is:");
						eaJMetalMapper.printCurrentMapping();
			
						eaJMetalMapper.analyzeIt(i, eaJMetalMapper.solutions.get(i).getObjective(1));
					}
				} catch (NumberFormatException e) {
					logger.fatal(e);
					System.exit(0);
				} catch (ParseException e) {
					logger.fatal(e);
					System.exit(0);
				} catch (IllegalArgumentException e) {
					logger.fatal(e);
					System.exit(0);
				}
			}
		};
		
		mapperInputProcessor.getCliOptions().addOption(
				"j",
				"jmetal-algorithm",
				true,
				"the jMetal algorithm ("
						+ Arrays.toString(JMetalAlgorithm.values()) + ")");
		mapperInputProcessor.getCliOptions().addOption("p", "population-size",
				true, "the population size");
		mapperInputProcessor.getCliOptions().addOption("g", "generations",
				true, "the number of generations");
		mapperInputProcessor.getCliOptions().addOption("xc", "crossover-class",
				true, "crossover Java class");
		mapperInputProcessor.getCliOptions().addOption("x",
				"crossover-probability", true, "crossover probability (%)");
		mapperInputProcessor.getCliOptions().addOption("mc", "mutation-class",
				true, "mutation Java class");
		mapperInputProcessor.getCliOptions().addOption("m",
				"mutation-probability", true, "mutation probability (%)");
		
		mapperInputProcessor.processInput(args);
}
	
	private static class EnergyAndTemperatureAwareMappingProblem extends Problem {

		/** auto generated serial version UID */
		private static final long serialVersionUID = -1128225954989831104L;

		private EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm mapper;

		private int nodes;

		private int cores;

		private Random rand;

		public EnergyAndTemperatureAwareMappingProblem(
				EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm mapper,
				int nodes, int cores, Random rand)
				throws ClassNotFoundException {
			this.mapper = mapper;
			this.nodes = nodes;
			this.cores = cores;
			this.rand = rand;
			numberOfVariables_ = 1;
			numberOfObjectives_ = 2;
			numberOfConstraints_ = 0;
			problemName_ = "EnergyAndTemperatureAwareJMetelMultiobjectiveMappingProblem";

			upperLimit_ = new double[numberOfVariables_];
			lowerLimit_ = new double[numberOfVariables_];
			length_ = new int[numberOfVariables_];

			for (int var = 0; var < numberOfVariables_; var++) {
				length_[var] = this.nodes;
				lowerLimit_[var] = 0;
				upperLimit_[var] = this.nodes - 1;
			}

			solutionType_ = new PermutationSolutionType(this) {

				@Override
				public Variable[] createVariables() {
					Variable[] variables = new Variable[problem_
							.getNumberOfVariables()];

					for (int var = 0; var < problem_.getNumberOfVariables(); var++) {
						variables[var] = new Permutation(
								problem_.getLength(var),
								EnergyAndTemperatureAwareMappingProblem.this.rand);
					}

					for (int i = 0; i < variables.length; i++) {
						Permutation permutation = (Permutation) variables[i];
						postProcessPermutation(permutation.vector_);
					}

					return variables;
				}

				private void postProcessPermutation(int permutation[]) {
					for (int i = 0; i < permutation.length; i++) {
						if (permutation[i] >= EnergyAndTemperatureAwareMappingProblem.this.cores) {
							permutation[i] = -1;
						}
					}
				}

			};
		}

		public void evaluate(Solution solution) throws JMException {
			int permutation[] = ((Permutation) solution.getDecisionVariables()[0]).vector_;
			double Firstfitness = mapper.fitnessCalculation(permutation);
			double secondfitness = mapper.calculateSecondFitness(permutation);

			solution.setObjective(0, Firstfitness);
			solution.setObjective(1, secondfitness);
		}

	}

}
