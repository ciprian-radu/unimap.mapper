package ro.ulbsibiu.acaps.mapper.ga.jmetal;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import jmetal.base.Algorithm;
import jmetal.base.Problem;
import jmetal.base.Solution;
import jmetal.base.SolutionSet;
import jmetal.base.operator.crossover.Crossover;
import jmetal.base.operator.mutation.Mutation;
import jmetal.base.operator.mutation.MutationFactory;
import jmetal.base.operator.selection.Selection;
import jmetal.base.operator.selection.SelectionFactory;
import jmetal.base.variable.Permutation;
import jmetal.metaheuristics.singleObjective.evolutionStrategy.NonElitistES;
import jmetal.metaheuristics.singleObjective.geneticAlgorithm.acGA;
import jmetal.metaheuristics.singleObjective.geneticAlgorithm.gGA;
import jmetal.metaheuristics.singleObjective.geneticAlgorithm.scGA;
import jmetal.metaheuristics.singleObjective.geneticAlgorithm.ssGA;
import jmetal.util.JMException;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.TaskType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CommunicationType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MapType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.ga.Communication;
import ro.ulbsibiu.acaps.mapper.ga.Core;
import ro.ulbsibiu.acaps.mapper.ga.GeneticAlgorithmInputException;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.PositionBasedCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.problem.MappingProblem;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.evolutionStrategy.ElitistES;
import ro.ulbsibiu.acaps.mapper.util.ApcgFilenameFilter;

/**
 * Helper class that integrates the single objective Genetic Algorithms with the Network-on-Chip application mapping problem
 * 
 * @see Mapper
 * 
 * @author cradu
 * @author shaikat
 *
 */
public class JMetalEvolutionaryAlgorithmMapper implements Mapper {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(JMetalEvolutionaryAlgorithmMapper.class);
	
	
	/**
	 * The jMetal single objective algorithms
	 * 
	 * @author cradu
	 *
	 */
	public enum JMetalAlgorithm {
		/**
		 * Elitist version of Genetic Algorithm (its developed by us)
		 */
		EGA,
		/**
		 * Steady State Genetic Algorithm
		 */
		SSGA,
		
		/**
		 * Generational Genetic Algorithm
		 */
		GGA,
		
		/**
		 * Asynchronous Cellular Genetic Algorithm
		 */
		ACGA,
		
		/**
		 * Synchronous Cellular Genetic Algorithm
		 */
		SCGA,
		
		/**
		 * Ellitist Evolutionary Strategy 
		 */
		EES,
		
		/**
		 * Non-ellitist Evolutionary Strategy 
		 */
		NEES,
		
		/**
		 * NSGA-II
		 */
		NSGAII,
		
		/**
		 * SPEA2 
		 */
		SPEA2
	}
	
	/**
	 * the selected jMetal single objective algorithm
	 */
	private JMetalAlgorithm jMetalAlgorithm;
	
	/**
	 * the size of the population 
	 */
	private int populationSize;

	/**
	 * crossover probability (a number in [0, 1] interval) 
	 */
	private double crossoverProbability;

	/**
	 * mutation probability (a number in [0, 1] interval) 
	 */
	private double mutationProbability;

	/**
	 * the maximum number of evaluations allowed for the algorithm
	 */
	private int maxEvaluations;
	
	/**
	 * jMetal {@link Problem} 
	 */
	private Problem problem;
	
	/**
	 * jMetal {@link Algorithm}
	 */
	private Algorithm algorithm;
	
	/**
	 * jMetal {@link Crossover} operator
	 */
	private Crossover crossover;
	
	/**
	 * jMetal {@link Mutation} operator
	 */
	private Mutation mutation;
	
	/**
	 * jMetal {@link Selection} operator
	 */
	private Selection selection;

	/**
	 * the number of Network-on-Chip nodes
	 */
	private int nodesNumber;

	/**
	 * the number of IP cores to be mapped
	 */
	private int coresNumber;

	/**
	 * the current Communication Task Graph
	 */
	private List<CtgType> currentCtg;

	/**
	 * the current Application Characterization Graph 
	 */
	private List<ApcgType> currentApcg;

	/**
	 * the {@link Core}s to be mapped
	 */
	private Core[] cores;

	/** all the communication is stored in the communication array */
	private ArrayList<Communication> communications;

	/** counts how many cores were parsed from the parsed APCGs */
	private int previousCoreCount = 0;
	
	/**
	 * Constructor
	 * 
	 * @param ctg
	 *            Communication Task Graph
	 * @param apcg
	 *            Application Characterization Graph
	 * @param coresNumber
	 *            the number of IP cores to be mapped
	 * @param nodesNumber
	 *            the number of Network-on-Chip nodes
	 * @param jMetalAlgorithm
	 *            the selected jMetal single objective algorithm
	 * @param populationSize
	 *            the size of the population
	 * @param maxEvaluations
	 *            the maximum number of evaluations allowed for the algorithm
	 * @param crossoverProbability
	 *            crossover probability (a number in [0, 1] interval)
	 * @param mutationProbability
	 *            mutation probability (a number in [0, 1] interval)
	 */
	public JMetalEvolutionaryAlgorithmMapper(List<CtgType> ctg, List<ApcgType> apcg,
			int coresNumber, int nodesNumber, JMetalAlgorithm jMetalAlgorithm,
			int populationSize, int maxEvaluations, double crossoverProbability,
			double mutationProbability) {
		this.currentCtg = ctg;
		this.currentApcg = apcg;
		this.coresNumber = coresNumber;
		this.nodesNumber = nodesNumber;
		this.jMetalAlgorithm = jMetalAlgorithm;
		this.maxEvaluations = maxEvaluations;
		this.populationSize = populationSize;
		this.crossoverProbability = crossoverProbability;
		this.mutationProbability = mutationProbability;

		initializeCores();

		for (int k = 0; k < currentApcg.size(); k++) {
			parseApcg(currentApcg.get(k));
		}

		getCommunicatios();
	}

	@Override
	public String getMapperId() {
		return jMetalAlgorithm.toString();
	}	
	
	public String[] map() {
		StringWriter []stringWriter = new StringWriter[1];
		stringWriter[0]= new StringWriter();
		try {
			problem = new MappingProblem(1, communications, cores, nodesNumber);

			switch (jMetalAlgorithm) {
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
			algorithm.setInputParameter("maxEvaluations", maxEvaluations);

			// crossover = CrossoverFactory.getCrossoverOperator("PMXCrossover");
			crossover = new PositionBasedCrossover();
			crossover.setParameter("probability", crossoverProbability);
			// crossover.setParameter("distributionIndex", 20.0);

			mutation = MutationFactory.getMutationOperator("SwapMutation");
			mutation.setParameter("probability", mutationProbability);
			// mutation.setParameter("distributionIndex", 20.0);

			selection = (Selection) SelectionFactory.getSelectionOperator("BinaryTournament");
			// selection = SelectionFactory.getSelectionOperator("DifferentialEvolutionSelection");

			algorithm.addOperator("crossover", crossover);
			algorithm.addOperator("mutation", mutation);
			algorithm.addOperator("selection", selection);

			// Execute the Algorithm
			long initTime = System.currentTimeMillis();
			SolutionSet population = algorithm.execute();
			long estimatedTime = System.currentTimeMillis() - initTime;
			logger.info("Total execution time: " + estimatedTime / 1000.0 + " s");

//			population.printObjectivesToFile("FUN");
//			logger.info("Objectives values have been writen to file FUN");
//			population.printVariablesToFile("VAR");
//			logger.info("Variables values have been writen to file VAR");

			Solution S = new Solution(population.get(0));
			int sol[] = ((Permutation) S.getDecisionVariables()[0]).vector_;
//			for (int i = 0; i < sol.length; i++) {
//				System.out.print(sol[i] + " ");
//			}
			printCurrentMapping(sol);
			logger.info("Communication cost = " + S.getObjective(0));

			MappingType mapping = new MappingType();
			mapping.setId(jMetalAlgorithm.toString());
			mapping.setRuntime((double) estimatedTime);

			for (int i = 0; i < cores.length; i++) {
				int currentIpCore = cores[i].getCoreNo();
				for (int j = 0; j < this.nodesNumber; j++) {
					if (currentIpCore == sol[j]) {
						MapType map = new MapType();
						map.setNode(Integer.toString(j));
						map.setCore(cores[i].getCoreUid());
						map.setApcg(cores[i].getApcgId());
						mapping.getMap().add(map);
						break;
					}
				}
			}
			
			
			ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory mappingFactory = new ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory();
			JAXBElement<MappingType> jaxbElement = mappingFactory.createMapping(mapping);
			JAXBContext jaxbContext = JAXBContext.newInstance(MappingType.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
			marshaller.marshal(jaxbElement, stringWriter[0]);
		} catch (ClassNotFoundException e) {
			logger.error(e);
		} catch (JMException e) {
			logger.error(e);
		} catch (JAXBException e) {
			logger.error("JAXB encountered an error", e);
		}
		
		String[] returnString = new String [1];
		returnString[0]= stringWriter[0].toString();
		
		return returnString;
	}

	private void printCurrentMapping(int sol[]) {
		for (int i = 0; i < cores.length; i++) {
			int currentIpCore = cores[i].getCoreNo();
			for (int j = 0; j < this.nodesNumber; j++) {
				if (currentIpCore == sol[j]) {
					logger.info("Core " + cores[i].getCoreUid() + " (APCG "
							+ cores[i].getApcgId() + ") is mapped to NoC node " + j);
					break;
				}
			}
		}
	}

	private void initializeCores() {
		cores = new Core[coresNumber];
		for (int i = 0; i < cores.length; i++) {
			cores[i] = new Core(i, null, -1);
		}
	}

	private void getCommunicatios() {
		this.communications = new ArrayList<Communication>();
		for (int k = 0; k < currentCtg.size(); k++) {

			// communication is the list of all communication of the current ctg
			List<CommunicationType> communication = this.currentCtg.get(k)
					.getCommunication();

			/*
			 * taskAssingToIpcore is a list of task assigned to a IP core here
			 * it is considered a core is assign a task
			 */
			List<TaskType> taskAssignToIpcore;

			// no of communication in the current Ctg
			int noOfComm = this.currentCtg.get(k).getCommunication().size();

			// no of IP cores in current apcg
			int noOfIpCore = this.currentApcg.get(k).getCore().size();

			// source and destination of application task
			String sourceTask, destTask;

			// volume of the data
			double volume;

			// source and destination IP core, initialized by -1
			String sourceIpCore = "-1", destIpCore = "-1";

			for (int i = 0; i < noOfComm; i++) {

				sourceTask = communication.get(i).getSource().getId();
				destTask = communication.get(i).getDestination().getId();
				volume = communication.get(i).getVolume();

				// source task
				for (int j = 0; j < noOfIpCore; j++) {
					taskAssignToIpcore = this.currentApcg.get(k).getCore()
							.get(j).getTask();
					if (sourceTask.equals(taskAssignToIpcore.get(0).getId())) {
						sourceIpCore = this.currentApcg.get(k).getCore().get(j)
								.getUid();
						break;
					}
				}

				// destination task
				for (int j = 0; j < noOfIpCore; j++) {
					taskAssignToIpcore = this.currentApcg.get(k).getCore()
							.get(j).getTask();
					if (destTask.equals(taskAssignToIpcore.get(0).getId())) {
						destIpCore = this.currentApcg.get(k).getCore().get(j)
								.getUid();
						break;
					}
				}
				communications.add(new Communication(this.currentApcg.get(k)
						.getId(), sourceIpCore, destIpCore, volume));

			}
		}

	}

	private void parseApcg(ApcgType apcg) {
		int i;
		for (i = 0; i < apcg.getCore().size(); i++) {
			cores[previousCoreCount + i].setApcgId(apcg.getId());
			cores[previousCoreCount + i].setCoreUid(apcg.getCore().get(i)
					.getUid());
		}
		previousCoreCount += i;
	}

	public static void printCommandLineArguments() {
		System.err.println("usage:   java JMetalEvolutionaryAlgorithmMapper.class [{populationSize} {maxEvolution} {crossoverPr} {mutationPr}] [--algo {algo name}] [E3S benchmarks]");
		System.err.println("1st parameter is number of population (population size)");
		System.err.println("2nd parameter is maximum number of evolution to run");
		System.err.println("3rd parameter is crossover probability (in real number) crossover probability < 1.00");
		System.err.println("4th parameter is mutation probability (in real number) mutation probability < 1.00");
		System.err.println("algorithm names are: SSGA, GGA, SCGA, ACGA, EES, NEES");
		System.err.println("example 1 (specify the tgff file): java JMetalEvolutionaryAlgorithmMapper.class 100 500000 0.8 0.005 --algo gGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 2 (specify the tgff file with default parameter): java JMetalEvolutionaryAlgorithmMapper.class ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 3 (specify the tgff file with algo name): java JMetalEvolutionaryAlgorithmMapper.class --algo gGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 4 (specify the tgff file with GA parameters): java JMetalEvolutionaryAlgorithmMapper.class 100 50000 0.08 0.05 ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 5 (specify the ctg with all other parameters): java JMetalEvolutionaryAlgorithmMapper.class 100 100000 0.8 0.05 --algo ACGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3");
		System.err.println("example 6 (specify the ctg and algo name): java JMetalEvolutionaryAlgorithmMapper.class --algo scGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3");
		System.err.println("example 7 (specify the ctg and apcg with all parameters): java JMetalEvolutionaryAlgorithmMapper.class 100 100000 0.8 0.05 --algo SCGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3 --apcg 2");
		System.err.println("example 8 (specify the ctg, apcg and algo name): java JMetalEvolutionaryAlgorithmMapper.class --algo scGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3 --apcg 2");
		System.err.println("example 9 (map the entire E3S benchmark suite with all paremeters): java JMetalEvolutionaryAlgorithmMapper.class 100 1000000 0.8 0.01 --algo GGA");
		System.err.println("example 10 (map the entire E3S benchmark suite with specific algo): java JMetalEvolutionaryAlgorithmMapper.class --algo GGA");
		System.exit(0);
	}

	public static void main(String args[]) throws TooFewNocNodesException,
			IOException, JAXBException, GeneticAlgorithmInputException {

		File[] tgffFiles = null;
		String specifiedCtgId = null;
		String specifiedApcgId = null;
		boolean is1stParameterString = false;

		final int defaultPopulationSize = 400;
		final int defaultMaxEvaluations = 5000000;
		final double defaultCrossoverPr = 0.85;
		final double defaultMutationPr = 0.05;
		final JMetalAlgorithm defaultAlgorithmName = JMetalAlgorithm.SSGA;
		
		int populationSize = 0;
		int maxEvaluations = 0;
		double crossoverProbability = 0;
		double mutationProbability = 0;
		JMetalAlgorithm jMetalAlgorithm = null;

		if (args.length == 0) {
			populationSize = defaultPopulationSize;
			maxEvaluations = defaultMaxEvaluations;
			crossoverProbability = defaultCrossoverPr;
			mutationProbability = defaultMutationPr;
			jMetalAlgorithm = defaultAlgorithmName;
		} else {
			try {
				populationSize = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				is1stParameterString = true;
			}
			if (is1stParameterString == true) {
				populationSize = defaultPopulationSize;
				maxEvaluations = defaultMaxEvaluations;
				crossoverProbability = defaultCrossoverPr;
				mutationProbability = defaultMutationPr;
				if (args[0].startsWith("--algo")) {
					jMetalAlgorithm = JMetalAlgorithm.valueOf(args[1]);
				} else {
					jMetalAlgorithm = defaultAlgorithmName;
				}
			} else {
				try {
					maxEvaluations = Integer.parseInt(args[1]);
					if (Double.parseDouble(args[2]) > 1.0)
						throw new GeneticAlgorithmInputException(
								"Crossover Probability must be less than 1.0");
					crossoverProbability = Double.parseDouble(args[2]);
					if (Double.parseDouble(args[3]) > 1.0)
						throw new GeneticAlgorithmInputException(
								"Mutation Probability must be less than 1.0");
					mutationProbability = Double.parseDouble(args[3]);
					if (args.length <= 4)
						jMetalAlgorithm = defaultAlgorithmName;
					else if (args[4].startsWith("--algo")) {
						jMetalAlgorithm = JMetalAlgorithm.valueOf(args[5]);
					} else {
						jMetalAlgorithm = defaultAlgorithmName;
					}
				} catch (NumberFormatException e) {
					logger.error(e);
					printCommandLineArguments();
				} catch (IllegalArgumentException e) {
					logger.error(e);
					printCommandLineArguments();
				}
			}
		}

		if (args.length == 0
				|| (args.length == 2 && args[0].startsWith("--algo"))
				|| (args.length == 4 && is1stParameterString == false)
				|| (args.length == 6 && is1stParameterString == false)) {
			File e3sDir = new File(".." + File.separator + "CTG-XML"
					+ File.separator + "xml" + File.separator + "e3s");
			logger.assertLog(e3sDir.isDirectory(),
					"Could not find the E3S benchmarks directory!");
			tgffFiles = e3sDir.listFiles(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".tgff");
				}
			});
		} else {
			List<File> tgffFileList = new ArrayList<File>(args.length);
			int i;

			if (is1stParameterString == true) {
				if (args[0].startsWith("--algo") == false)
					i = 0;
				else
					i = 2;
			} else {
				if (args[4].startsWith("--algo") == false)
					i = 4;
				else
					i = 6;
			}

			for (; i < args.length; i++) {
				if (args[i].startsWith("--ctg") || args[i].startsWith("--apcg")) {
					break;
				}
				tgffFileList.add(new File(args[i]));
			}
			tgffFiles = tgffFileList.toArray(new File[tgffFileList.size()]);
			if (i < args.length) {
				if (args[i].startsWith("--ctg")) {

					logger.assertLog(args.length > i + 1,
							"Expecting CTG ID after --ctg option");
					specifiedCtgId = args[i + 1];
				} else {
					if (args[i].startsWith("--apcg")) {
						logger.assertLog(args.length > i + 1,
								"Expecting APCG ID after --ctg option");
						specifiedApcgId = args[i + 1];
					}
				}

				i = i + 2;

				if (args.length > i + 1) {
					if (args[i].startsWith("--ctg")) {
						logger.assertLog(args.length > i + 1,
								"Expecting CTG ID after --ctg option");
						specifiedCtgId = args[i + 1];
					} else {
						if (args[i].startsWith("--apcg")) {
							logger.assertLog(args.length > i + 1,
									"Expecting APCG ID after --ctg option");
							specifiedApcgId = args[i + 1];
						}
					}
				}

				if (specifiedCtgId != null) {
					logger.info("Mapping only CTGs with ID " + specifiedCtgId);
				}
				if (specifiedApcgId != null) {
					logger.info("Mapping only APCGs with ID " + specifiedApcgId);
				}
			}
		}

		// input processing finished

		for (int i = 0; i < tgffFiles.length; i++) {
			String path = ".." + File.separator + "CTG-XML" + File.separator
					+ "xml" + File.separator + "e3s" + File.separator
					+ tgffFiles[i].getName() + File.separator;

			File e3sBenchmark = new File(path);
			String[] ctgs = null;
			if (specifiedCtgId != null) {
				ctgs = new String[] { "ctg-" + specifiedCtgId };
			} else {
				ctgs = e3sBenchmark.list(new FilenameFilter() {

					@Override
					public boolean accept(File dir, String name) {
						return dir.isDirectory() && name.startsWith("ctg-");
					}

				});
			}
			logger.assertLog(ctgs.length > 0, "No CTGs to work with!");

			for (int j = 0; j < ctgs.length; j++) {
				String ctgId = ctgs[j].substring("ctg-".length());
				List<CtgType> ctgTypes = new ArrayList<CtgType>();
				// if the ctg ID contains + => we need to map multiple CTGs
				// in one mapping XML
				String[] ctgIds = ctgId.split("\\+");

				List<File> apcgsList = new ArrayList<File>();

				for (int k = 0; k < ctgIds.length; k++) {
					JAXBContext jaxbContext = JAXBContext
							.newInstance("ro.ulbsibiu.acaps.ctg.xml.ctg");
					Unmarshaller unmarshaller = jaxbContext
							.createUnmarshaller();
					@SuppressWarnings("unchecked")
					CtgType ctgType = ((JAXBElement<CtgType>) unmarshaller
							.unmarshal(new File(path + "ctg-" + ctgIds[k]
									+ File.separator + "ctg-" + ctgIds[k]
									+ ".xml"))).getValue();
					ctgTypes.add(ctgType);

					String[] apcgs = null;
					String pathOfApcg = path + "ctg-" + ctgIds[k]
							+ File.separator;

					File apcgPath = new File(pathOfApcg);

					apcgsList.addAll(Arrays.asList(apcgPath
							.listFiles(new ApcgFilenameFilter(ctgIds[k],
									specifiedApcgId))));
				}
				File[] apcgFiles = apcgsList
						.toArray(new File[apcgsList.size()]);
				for (int l = 0; l < apcgFiles.length / ctgIds.length; l++) {
					String apcgId = ctgId + "_";
					if (specifiedApcgId == null) {
						apcgId += l;
					} else {
						apcgId += specifiedApcgId;
					}
					List<ApcgType> apcgTypes = new ArrayList<ApcgType>();
					for (int k = 0; k < apcgFiles.length; k++) {
						String id;
						if (specifiedApcgId == null) {
							id = Integer.toString(l);
						} else {
							id = specifiedApcgId;
						}
						if (apcgFiles[k].getName().endsWith(id + ".xml")) {
							JAXBContext jaxbContext = JAXBContext
									.newInstance("ro.ulbsibiu.acaps.ctg.xml.apcg");
							Unmarshaller unmarshaller = jaxbContext
									.createUnmarshaller();
							@SuppressWarnings("unchecked")
							ApcgType apcg = ((JAXBElement<ApcgType>) unmarshaller
									.unmarshal(new File(apcgFiles[k]
											.getAbsolutePath()))).getValue();
							apcgTypes.add(apcg);
						}
					}
					logger.assertLog(apcgTypes.size() == ctgTypes.size(),
							"An equal number of CTGs and APCGs is expected!");

					logger.info("Using " + jMetalAlgorithm + " for " + path + "ctg-"
							+ ctgId + " (APCG " + apcgId + ")");

					// number of IP cores in the apcg
					int noOfIpCores = 0;
					for (int k = 0; k < apcgTypes.size(); k++) {
						noOfIpCores += apcgTypes.get(k).getCore().size();
					}

					int hSize = (int) Math.ceil(Math.sqrt(noOfIpCores));
					hSize = Math.max(4, hSize); // using at least a 4x4 2D mesh
					String meshSize = hSize + "x" + hSize;
					logger.info("The algorithm has "
							+ noOfIpCores
							+ " cores to map => working with a 2D mesh of size "
							+ meshSize);

					// working with a 2D mesh topology
					String topologyDir = ".." + File.separator + "NoC-XML"
							+ File.separator + "src" + File.separator + "ro"
							+ File.separator + "ulbsibiu" + File.separator
							+ "acaps" + File.separator + "noc" + File.separator
							+ "topology" + File.separator + "mesh2D"
							+ File.separator + meshSize;

					File nodesDir = new File(topologyDir, "nodes");
					logger.assertLog(nodesDir.isDirectory(), nodesDir.getName()
							+ " is not a directory!");
					File[] nodeXmls = nodesDir.listFiles(new FileFilter() {

						@Override
						public boolean accept(File pathname) {
							return pathname.getName().endsWith(".xml");
						}
					});

					logger.debug("Found " + nodeXmls.length + " nodes");

					JMetalEvolutionaryAlgorithmMapper gaMapper = new JMetalEvolutionaryAlgorithmMapper(
							ctgTypes, apcgTypes, noOfIpCores, nodeXmls.length,
							jMetalAlgorithm, populationSize, maxEvaluations,
							crossoverProbability, mutationProbability);

					String[] mappingXml = gaMapper.map();

					File dir = new File(path + "ctg-" + ctgId);
					dir.mkdirs();

					String mappingXmlFilePath = path + "ctg-" + ctgId
							+ File.separator + "mapping-" + apcgId + "_" + gaMapper.getMapperId()
							+ ".xml";
					PrintWriter pw = new PrintWriter(mappingXmlFilePath);
					logger.info("Saving the mapping XML file " + mappingXmlFilePath);
					pw.write(mappingXml[0]);
					pw.close();
				}

			}
			logger.info("Done");
		}
	}
}
