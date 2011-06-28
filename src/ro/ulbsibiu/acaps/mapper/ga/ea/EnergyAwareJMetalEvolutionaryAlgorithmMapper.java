package ro.ulbsibiu.acaps.mapper.ga.ea;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.xml.bind.JAXBException;

import jmetal.base.Algorithm;
import jmetal.base.Problem;
import jmetal.base.Solution;
import jmetal.base.SolutionSet;
import jmetal.base.Variable;
import jmetal.base.operator.crossover.Crossover;
import jmetal.base.operator.mutation.Mutation;
import jmetal.base.operator.mutation.SwapMutation;
import jmetal.base.operator.selection.Selection;
import jmetal.base.operator.selection.SelectionFactory;
import jmetal.base.solutionType.PermutationSolutionType;
import jmetal.base.variable.Permutation;
import jmetal.util.JMException;
import jmetal.util.PseudoRandom;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.LegalTurnSet;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.AlgorithmTracker;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.JMetalEvolutionaryAlgorithmMapper;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.JMetalEvolutionaryAlgorithmMapper.JMetalAlgorithm;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.TrackedAlgorithm;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.MappingSimilarityCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.NocPositionBasedCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.PMXCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.PositionBasedCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.mutation.OsaMutation;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.evolutionStrategy.ElitistES;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.evolutionStrategy.NonElitistES;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.geneticAlgorithm.ElitistGA;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.geneticAlgorithm.acGA;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.geneticAlgorithm.gGA;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.geneticAlgorithm.scGA;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.geneticAlgorithm.ssGA;
import ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor;

/**
 * Helper class that integrates the single objective Genetic Algorithms with the
 * Network-on-Chip application mapping problem. It combines
 * {@link EnergyAwareGeneticAlgorithmMapper} with
 * {@link JMetalEvolutionaryAlgorithmMapper}. Thus, it generates mappings using
 * a genetic algorithm, it evaluates them in terms of energy consumption and it
 * can also consider bandwidth constraints. Additionally,a routing function can
 * be computed.
 * 
 * @see EnergyAwareGeneticAlgorithmMapper
 * @see JMetalEvolutionaryAlgorithmMapper
 * 
 * @author cradu
 * 
 */
public class EnergyAwareJMetalEvolutionaryAlgorithmMapper extends EnergyAwareGeneticAlgorithmMapper implements AlgorithmTracker {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(EnergyAwareJMetalEvolutionaryAlgorithmMapper.class);
	
	private static final String MAPPER_ID_PREFIX = "EA-";
	
	/**
	 * the selected jMetal single objective algorithm
	 */
	protected JMetalAlgorithm jMetalAlgorithm;
	
	/**
	 * jMetal {@link Problem} 
	 */
	protected Problem problem;
	
	/**
	 * jMetal {@link Algorithm}
	 */
	protected Algorithm algorithm;
	
	/**
	 * jMetal {@link Crossover} operator
	 */
	protected Crossover crossover;
	
	/**
	 * jMetal {@link Mutation} operator
	 */
	protected Mutation mutation;
	
	/**
	 * jMetal {@link Selection} operator
	 */
	protected Selection selection;
	
	private int[] solution;
	
	/** the distinct nodes with which each node communicates directly (through a single link) */
	protected Set<Integer>[] nodeNeighbors;
	
	/** the maximum neighbors a node can have */
	private int maxNodeNeighbors = 0;
	
	/** the distinct cores with which each core communicates directly */
	protected Set<Integer>[] coreNeighbors;
	
	/**
	 * the total data communicated by the cores
	 */
	protected double[] coreToCommunication;
	
	/** the total amount of data communicated by all cores*/
	protected long totalToCommunication;
	
	/** for every core, the (from and to) communication probability density function */
	protected double[][] coresCommunicationPDF;

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
	 * @param jMetalAlgorithm
	 *            the jMetal evolutionary algorithm
	 * @param populationSize
	 *            the population size (if <tt>null</tt>, a default value of 100
	 *            will be used)
	 * @param generations
	 *            the number of generations (if <tt>null</tt>, a default value
	 *            of 100 will be used)
	 * @param crossoverProbability
	 *            the crossover probability (%) (if <tt>null</tt>, a default
	 *            value of 90 will be used)
	 * @param mutationClass
	 *            the Java class for the mutation operator
	 * @param mutationProbability
	 *            the mutation probability (%) (if <tt>null</tt>, a default
	 *            value of 5 will be used)
	 */
	public EnergyAwareJMetalEvolutionaryAlgorithmMapper(String benchmarkName,
			String ctgId, String apcgId, String topologyName,
			String topologySize, File topologyDir, int coresNumber,
			double linkBandwidth, float switchEBit, float linkEBit, Long seed,
			JMetalAlgorithm jMetalAlgorithm, Integer populationSize,
			Integer generations, Class<?> crossoverClass,
			Integer crossoverProbability, Class<?> mutationClass,
			Integer mutationProbability) throws JAXBException {
		this(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, false,
				LegalTurnSet.WEST_FIRST, 1.056f, 2.831f, switchEBit, linkEBit,
				seed, jMetalAlgorithm, populationSize, generations,
				crossoverClass, crossoverProbability, mutationClass,
				mutationProbability);
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
	 * @param jMetalAlgorithm
	 *            the jMetal evolutionary algorithm
	 * @param populationSize
	 *            the population size (if <tt>null</tt>, a default value of 100
	 *            will be used)
	 * @param generations
	 *            the number of generations (if <tt>null</tt>, a default value
	 *            of 100 will be used)
	 * @param crossoverClass
	 *            the Java class for the crossover operator
	 * @param crossoverProbability
	 *            the crossover probability (%) (if <tt>null</tt>, a default
	 *            value of 90 will be used)
	 * @param mutationClass
	 *            the Java class for the mutation operator
	 * @param mutationProbability
	 *            the mutation probability (%) (if <tt>null</tt>, a default
	 *            value of 5 will be used)
	 * @throws JAXBException
	 */
	public EnergyAwareJMetalEvolutionaryAlgorithmMapper(String benchmarkName,
			String ctgId, String apcgId, String topologyName,
			String topologySize, File topologyDir, int coresNumber,
			double linkBandwidth, boolean buildRoutingTable,
			LegalTurnSet legalTurnSet, float bufReadEBit, float bufWriteEBit,
			float switchEBit, float linkEBit, Long seed,
			JMetalAlgorithm jMetalAlgorithm, Integer populationSize,
			Integer generations, Class<?> crossoverClass,
			Integer crossoverProbability, Class<?> mutationClass,
			Integer mutationProbability) throws JAXBException {
		super(benchmarkName, ctgId, apcgId, topologyName, topologySize,
				topologyDir, coresNumber, linkBandwidth, buildRoutingTable,
				legalTurnSet, bufReadEBit, bufWriteEBit, switchEBit, linkEBit,
				seed, populationSize, generations, crossoverProbability,
				mutationProbability);
		this.jMetalAlgorithm = jMetalAlgorithm;
		
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
		
		if (crossoverClass.equals(PositionBasedCrossover.class)
				|| crossoverClass.equals(PMXCrossover.class)) {
			try {
				this.crossover = (Crossover) crossoverClass.newInstance();
			} catch (InstantiationException e) {
				logger.error(e);
			} catch (IllegalAccessException e) {
				logger.error(e);
			}
		} else {
			// TODO The mapping similarity crossover operator is topology dependent
			if (crossoverClass.equals(MappingSimilarityCrossover.class)) {
				this.crossover = new MappingSimilarityCrossover() {
					
					@Override
					public int computeDistance(int node1, int node2) {
						int node1Column = node1 % hSize;
						int node1Row = node1 / hSize;
						
						int node2Column = node2 % hSize;
						int node2Row = node2 / hSize;
						
						// Manhattan distance
						int distance = Math.abs(node1Column - node2Column) + Math.abs(node1Row - node2Row);
						if (logger.isTraceEnabled()) {
							logger.trace("Manhattan distance between NoC nodes " + node1 + " and " + node2 + " is " + distance);
						}
						
						return distance;
					}
				};
			} else {
				if (crossoverClass.equals(NocPositionBasedCrossover.class)) {
					this.crossover = new NocPositionBasedCrossover(cores) {

						@Override
						public int computeDistance(int node1, int node2) {
							int node1Column = node1 % hSize;
							int node1Row = node1 / hSize;
							
							int node2Column = node2 % hSize;
							int node2Row = node2 / hSize;
							
							// Manhattan distance
							int distance = Math.abs(node1Column - node2Column) + Math.abs(node1Row - node2Row);
							if (logger.isTraceEnabled()) {
								logger.trace("Manhattan distance between NoC nodes " + node1 + " and " + node2 + " is " + distance);
							}
							
							return distance;
						}
						
					};
				} else {
					logger.fatal("Unknown crossover operator: "
							+ crossoverClass + "! Exiting...");
					System.exit(0);
				}
			}
		}
		
		if (mutationClass.equals(SwapMutation.class)
				|| mutationClass.equals(OsaMutation.class)) {
			try {
				this.mutation = (Mutation) mutationClass.newInstance();
			} catch (InstantiationException e) {
				logger.error(e);
			} catch (IllegalAccessException e) {
				logger.error(e);
			}
		} else {
			logger.fatal("Unknown mutation operator: " + mutationClass
					+ "! Exiting...");
			System.exit(0);
		}
		
		PseudoRandom.setSeed(seed);
	}

	@Override
	public String getMapperId() {
		String sufix = "";
		if (crossover instanceof PositionBasedCrossover) {
			sufix += "-PB";
		}
		if (crossover instanceof PMXCrossover) {
			sufix += "-PMX";
		}
		if (crossover instanceof MappingSimilarityCrossover) {
			sufix += "-MS";
		}
		if (crossover instanceof NocPositionBasedCrossover) {
			sufix += "-NPB";
		}
		if (mutation instanceof OsaMutation) {
			sufix += "-OSA";
		}
		return MAPPER_ID_PREFIX + jMetalAlgorithm.toString() + sufix;
	}
	
	protected void computeCoreToCommunicationPDF() {
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
	
	protected void computeCoresCommunicationPDF() {
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
	
	protected void computeCoreNeighbors() {
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
			problem = new EnergyAwareMappingProblem(this, nodes.length, cores.length, rand);

			switch (jMetalAlgorithm) {
			case EGA:
				algorithm = new ElitistGA(problem);
				break;
			case SSGA:
				algorithm = new ssGA(problem);
				break;
			case GGA:
				algorithm = new gGA(problem);
				break;
			case ACGA:
				algorithm = new acGA(problem);
				break;
			case SCGA:
				algorithm = new scGA(problem);
				break;
			case EES:
				algorithm = new ElitistES(problem, populationSize, populationSize * 2);
				break;
			case NEES:
				algorithm = new NonElitistES(problem, populationSize, populationSize * 2);
				break;
			default:
				logger.fatal("Unknown jMetal algorithm: " + jMetalAlgorithm + "! Exiting...");
				System.exit(-1);
				break;
			}

			algorithm.setInputParameter("populationSize", populationSize);
			algorithm.setInputParameter("maxEvaluations", generations * populationSize);
			((TrackedAlgorithm) algorithm).setAlgorithmTracker(this);

			// crossover = CrossoverFactory.getCrossoverOperator("PMXCrossover");
			crossover.setParameter("probability", crossoverProbability / 100.0);
			// crossover.setParameter("distributionIndex", 20.0);

			// mutation = MutationFactory.getMutationOperator("SwapMutation");
			mutation.setParameter("probability", mutationProbability / 100.0);
			// mutation.setParameter("distributionIndex", 20.0);

			selection = (Selection) SelectionFactory.getSelectionOperator("BinaryTournament");
			// selection = SelectionFactory.getSelectionOperator("DifferentialEvolutionSelection");

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
		try {
			SolutionSet population = algorithm.execute();
			Solution S = new Solution(population.get(0));
			solution = ((Permutation) S.getDecisionVariables()[0]).vector_;
			logger.info("Best mapping has cost " + population.get(0).getObjective(0));
		} catch (ClassNotFoundException e) {
			logger.error(e);
		} catch (JMException e) {
			logger.error(e);
		}
		return 1;
	}

	@Override
	protected void doBeforeSavingMapping() {
		// return the best mapping found
		for (int i = 0; i < nodes.length; i++) {
			nodes[i].setCore("-1");
		}
		for (int i = 0; i < cores.length; i++) {
			cores[i].setNodeId(-1);
		}
		for (int i = 0; i < nodes.length; i++) {
			String coreAsString = Integer.toString(solution[i]);
			nodes[i].setCore(coreAsString);
			if (!"-1".equals(coreAsString)) {
				cores[Integer.valueOf(coreAsString)].setNodeId(i);
			}
		}
		if (buildRoutingTable) {
			programRouters();
		}
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
				new String[] { parameterName + "_" + "energy" },
				new String[] { parameterValue.toString() + "_" + Double.toString(energy) });
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
			 * @see ro.ulbsibiu.acaps.mapper.util.MapperInputProcessor#useMapper(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.util.List, java.util.List, boolean, java.lang.Long)
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
					jMetalAlgorithm = JMetalAlgorithm.valueOf(cmd.getOptionValue("j", JMetalAlgorithm.EGA.toString()));
					
					logger.info("Using "
							+ jMetalAlgorithm
							+ " (an energy aware, jMetal, evolutionary algorithm) mapper for "
							+ benchmarkFilePath + "ctg-" + ctgId + " (APCG "
							+ apcgId + ")");
					
					EnergyAwareJMetalEvolutionaryAlgorithmMapper eaJMetalMapper;
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
					
					Integer populationSize = null;
					Integer generations = null;
					Class<?> crossoverClass = null;
					Integer crossoverProbability = null;
					Class<?> mutationClass = null;
					Integer mutationProbability = null;
					if (cmd.hasOption("p")) {
						populationSize = Integer.valueOf(cmd.getOptionValue("p"));
					}
					int defaultGenerationsNumber;
					// Bellow is the number of evaluations made by OSA, by default (i.e., an initial temperature of 1)
					double osaEvaluations = 33*cores*(2*nodes - cores - 1) + 1;
					if (populationSize == null) {
						defaultGenerationsNumber = (int) Math.ceil(osaEvaluations / POPULATION_SIZE);
					} else {
						defaultGenerationsNumber = (int) Math.ceil(osaEvaluations / populationSize);
					}
					if (cmd.hasOption("g")) {
						if (JMetalAlgorithm.EES.equals(jMetalAlgorithm)
								|| JMetalAlgorithm.NEES.equals(jMetalAlgorithm)) {
							generations = 2 * Integer.valueOf(cmd.getOptionValue("g")) - 1;
						} else {
							generations = Integer.valueOf(cmd.getOptionValue("g"));
						}
					} else {
						generations = defaultGenerationsNumber;
					}
					try {
						String crossoverClassString = cmd.getOptionValue("xc", PositionBasedCrossover.class.getName());
						crossoverClass = Class.forName(crossoverClassString);
					} catch (ClassNotFoundException e) {
						logger.error(e);
					}
					logger.info("Using a " + crossoverClass.getName() + " crossover");
					if (cmd.hasOption("x")) {
						crossoverProbability = Integer.valueOf(cmd.getOptionValue("x"));
					}
					try {
						String mutationClassString = cmd.getOptionValue("mc", SwapMutation.class.getName());
						mutationClass = Class.forName(mutationClassString);
					} catch (ClassNotFoundException e) {
						logger.error(e);
					}
					logger.info("Using a " + mutationClass.getName() + " mutation");
					if (cmd.hasOption("m")) {
						mutationProbability = Integer.valueOf(cmd.getOptionValue("m"));
					}
					int defaultMutationProbability = (int) Math.floor(100.0 / nodes);
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
							"mutationProbability",
							};
					String values[] = new String[] {
							Double.toString(linkBandwidth),
							Float.toString(switchEBit), Float.toString(linkEBit),
							Float.toString(bufReadEBit),
							Float.toString(bufWriteEBit),
							null,
							seed == null ? null : Long.toString(seed),
							populationSize == null ? null : Integer.toString(populationSize),
							generations == null ? null : Integer.toString(generations),
							crossoverProbability == null ? null : Integer.toString(crossoverProbability),
							mutationClass == null ? null : mutationClass.getName(),
							mutationProbability == null ? null : Integer.toString(mutationProbability),
							};
					if (doRouting) {
						values[values.length - 7] = "true" + "-" + lts.toString();
						MapperDatabase.getInstance().setParameters(parameters, values);
						
						// with routing
						eaJMetalMapper = new EnergyAwareJMetalEvolutionaryAlgorithmMapper(
								benchmarkName, ctgId, apcgId, topologyName,
								meshSize, new File(topologyDir), cores,
								linkBandwidth, true, lts,
								bufReadEBit, bufWriteEBit, switchEBit,
								linkEBit, seed, jMetalAlgorithm,
								populationSize, generations, crossoverClass,
								crossoverProbability, mutationClass,
								mutationProbability);
					} else {
						values[values.length - 7] = "false";
						MapperDatabase.getInstance().setParameters(parameters, values);
						
						// without routing
						eaJMetalMapper = new EnergyAwareJMetalEvolutionaryAlgorithmMapper(
								benchmarkName, ctgId, apcgId, topologyName,
								meshSize, new File(topologyDir), cores,
								linkBandwidth, switchEBit, linkEBit, seed,
								jMetalAlgorithm, populationSize, generations,
								crossoverClass, crossoverProbability,
								mutationClass, mutationProbability);
					}

//			// read the input data from a traffic.config file (NoCmap style)
//			eagaMapper(
//					"telecom-mocsyn-16tile-selectedpe.traffic.config",
//					linkBandwidth);
					
					for (int k = 0; k < apcgTypes.size(); k++) {
						// read the input data using the Unified Framework's XML interface
						eaJMetalMapper.parseApcg(apcgTypes.get(k), ctgTypes.get(k));
					}
					
//			// This is just for checking that bbMapper.parseTrafficConfig(...)
//			// and parseApcg(...) have the same effect
//			eagaMapper.printCores();

					String[] mappingXml = eaJMetalMapper.map();
					File dir = new File(benchmarkFilePath + "ctg-" + ctgId);
					dir.mkdirs();
					String routing = "";
					if (doRouting) {
						routing = "_routing";
					}
					String mappingXmlFilePath = benchmarkFilePath + "ctg-" + ctgId
							+ File.separator + "mapping-" + apcgId + "_"
							+ eaJMetalMapper.getMapperId() + routing + ".xml";
					PrintWriter pw = new PrintWriter(mappingXmlFilePath);
					logger.info("Saving the mapping XML file" + mappingXmlFilePath);
					pw.write(mappingXml[0]);
					pw.close();

					logger.info("The generated mapping is:");
					eaJMetalMapper.printCurrentMapping();
					
					eaJMetalMapper.analyzeIt();
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
		
		mapperInputProcessor.getCliOptions().addOption("j", "jmetal-algorithm", true, "the jMetal algorithm (" 
				+ Arrays.toString(JMetalAlgorithm.values()) + ")");
		mapperInputProcessor.getCliOptions().addOption("p", "population-size", true, "the population size");
		mapperInputProcessor.getCliOptions().addOption("g", "generations", true, 
				"the number of generations (the number of evaluations equals g * p for all algorithms except EES ans NEES, where it is (2*g - 1) * p)");
		mapperInputProcessor.getCliOptions().addOption("xc", "crossover-class", true, "crossover Java class");
		mapperInputProcessor.getCliOptions().addOption("x", "crossover-probability", true, "crossover probability (%)");
		mapperInputProcessor.getCliOptions().addOption("mc", "mutation-class", true, "mutation Java class");
		mapperInputProcessor.getCliOptions().addOption("m", "mutation-probability", true, "mutation probability (%)");
		
		mapperInputProcessor.processInput(args);
	}
	
	private static class EnergyAwareMappingProblem extends Problem {
		
		/** auto generated serial version UID */
		private static final long serialVersionUID = -1128225954989831104L;
		
		private EnergyAwareJMetalEvolutionaryAlgorithmMapper mapper;
		
		private int nodes;
		
		private int cores;
		
		private Random rand;
		
		public EnergyAwareMappingProblem(
				EnergyAwareJMetalEvolutionaryAlgorithmMapper mapper, int nodes, int cores, Random rand)
				throws ClassNotFoundException {
			this.mapper = mapper;
			this.nodes = nodes;
			this.cores = cores;
			this.rand = rand;
			numberOfVariables_ = 1;
			numberOfObjectives_ = 1;
			numberOfConstraints_ = 0;
			problemName_ = "EnergyAwareMappingProblem";

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
								EnergyAwareMappingProblem.this.rand);
					}

					for (int i = 0; i < variables.length; i++) {
						Permutation permutation = (Permutation) variables[i];
						postProcessPermutation(permutation.vector_);
					}

					return variables;
				}
				
				private void postProcessPermutation(int permutation[]) {
					for (int i = 0; i < permutation.length; i++) {
						if (permutation[i] >= EnergyAwareMappingProblem.this.cores) {
							permutation[i] = -1;
						}
					}
				}
				
			};
		}
		
		public void evaluate(Solution solution) throws JMException {
		    int permutation[] = ((Permutation)solution.getDecisionVariables()[0]).vector_;
		    double fitness = mapper.fitnessCalculation(permutation);
			solution.setObjective(0, fitness);
		}
		
	}
}
