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
import jmetal.base.Operator;
import jmetal.base.Problem;
import jmetal.base.Solution;
import jmetal.base.SolutionSet;
import jmetal.base.operator.mutation.MutationFactory;
import jmetal.base.operator.selection.SelectionFactory;
import jmetal.base.variable.Permutation;
import jmetal.metaheuristics.singleObjective.evolutionStrategy.ElitistES;
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
import ro.ulbsibiu.acaps.mapper.ga.GeneticAlgorithmMapper;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.PositionBasedCrossover;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.problem.MappingProblem;
import ro.ulbsibiu.acaps.mapper.util.ApcgFilenameFilter;

/**
 * Helper class that integrates the single objective Genetic Algorithms with the Network-on-Chip application mapping problem
 * 
 * @see Mapper
 * 
 * @author shaikat
 * @author cradu
 *
 */
public class JMetalGeneticAlgorithmMapper implements Mapper {

	private static int populationSize;

	private static double crossoverPr;

	private static double mutationPr;

	//private static int noOfGenerationToRun;

	private static int maxEvolutions;
	
	private static String algorithmName;
	
	private String mapperId = "";

	Problem problem; // The problem to solve
	Algorithm algorithm; // The algorithm to use
	Operator crossover; // Crossover operator
	Operator mutation; // Mutation operator
	Operator selection; // Selection operator

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(GeneticAlgorithmMapper.class);

	/**
	 * the number of processes (tasks). Note that each core has only one task
	 * associated to it.
	 */
	private int noOfNodes;

	private int noOfIpCores;

	private List<CtgType> currentCtg;

	private List<ApcgType> currentApcg;

	/**
	 * the processes of mapping of IPcores and cores in integer number ( the
	 * cores in Noc are represented in integer number)
	 */
	private Core[] cores;

	/** all the communication is stored in the communication array */
	private ArrayList<Communication> communications;

	/** counts how many cores were parsed from the parsed APCGs */
	int previousCoreCount = 0;
	
	public JMetalGeneticAlgorithmMapper(List<CtgType> ctg,
			List<ApcgType> apcg, int noOfIpCores, int noOfNodes) {

		this.noOfIpCores = noOfIpCores;
		this.noOfNodes = noOfNodes;

		this.currentCtg = ctg;
		this.currentApcg = apcg;

		initializeCores();

		for (int k = 0; k < currentApcg.size(); k++) {
			parseApcg(currentApcg.get(k));
		}

		getCommunicatios();
	}

	@Override
	public String getMapperId() {
		return mapperId;
	}	
	
	public String map() {
		StringWriter stringWriter = new StringWriter();
		try {

			problem = new MappingProblem(1, communications, cores, noOfNodes);

			if (algorithmName.equals("ssGA")){
				algorithm = new ssGA(problem);
				mapperId = "ssGA";
			}
			else if (algorithmName.equals("gGA")){
				algorithm = new gGA(problem);
				mapperId = "gGA";
			}
			else if (algorithmName.equals("acGA")){
				algorithm = new acGA(problem);
				mapperId = "acGA";
			}
			else if (algorithmName.equals("scGA")){
				algorithm = new scGA(problem);
				mapperId = "scGA";
			}
			else if (algorithmName.equals("ElitistES")) {
				algorithm = new ElitistES(problem, populationSize,
						populationSize * 2);
				mapperId = "ElitisES";
			}
			else if (algorithmName.equals("NonElitistES")){
				algorithm = new NonElitistES(problem, populationSize,
						populationSize * 2);
				mapperId = "NonElitisES";
			}
			else {
				System.out.println("please check the algorithm name");
				System.exit(-1);
			}

			algorithm.setInputParameter("populationSize", populationSize);
			algorithm.setInputParameter("maxEvaluations", maxEvolutions);

			 //crossover = CrossoverFactory
			 //.getCrossoverOperator("PMXCrossover");

			crossover = new PositionBasedCrossover();
			crossover.setParameter("probability", crossoverPr);
			// /crossover.setParameter("distributionIndex", 20.0);

			mutation = MutationFactory.getMutationOperator("SwapMutation");

			mutation.setParameter("probability", mutationPr);
			// mutation.setParameter("distributionIndex", 20.0);

			selection = SelectionFactory
					.getSelectionOperator("BinaryTournament");

			// selection =
			// SelectionFactory.getSelectionOperator("DifferentialEvolutionSelection")
			// ;
			/* Add the operators to the algorithm */
			algorithm.addOperator("crossover", crossover);
			algorithm.addOperator("mutation", mutation);
			algorithm.addOperator("selection", selection);

			/* Execute the Algorithm */
			long initTime = System.currentTimeMillis();
			SolutionSet population = algorithm.execute();
			long estimatedTime = System.currentTimeMillis() - initTime;
			System.out.println("Total execution time: " + estimatedTime);

			/* Log messages */
			System.out
					.println("Objectives values have been writen to file FUN");
			population.printObjectivesToFile("FUN");
			System.out.println("Variables values have been writen to file VAR");
			population.printVariablesToFile("VAR");

			/*
			 * int numberOfVariables =
			 * population.get(0).getDecisionVariables().length ; for (int i = 0;
			 * i < population.size(); i++) { for (int j = 0; j <
			 * numberOfVariables; j++)
			 * bw.write(solutionsList_.get(i).getDecisionVariables
			 * ()[j].toString() + " ");
			 */
			int sol[];
			Solution S = new Solution(population.get(0));
			sol = ((Permutation) S.getDecisionVariables()[0]).vector_;
			for (int i = 0; i < sol.length; i++)
				System.out.print(sol[i] + " ");
			System.out.println();
			System.out.println();
			printCurrentMapping(sol);
			// System.out.println("Communication cost = "
			// +mp.calculateCommunicationCost(S));
			System.out.println("Communication cost = " + S.getObjective(0));

			MappingType mapping = new MappingType();
			mapping.setId(mapperId);
			mapping.setRuntime((double) estimatedTime);

			for (int i = 0; i < cores.length; i++) {
				int j;
				int currentIpCore = cores[i].getCoreNo();
				for (j = 0; j < this.noOfNodes; j++) {
					if (currentIpCore == sol[j]) {
						break;
					}
				}

				MapType map = new MapType();
				map.setNode("" + j);
				map.setCore(cores[i].getCoreUid());
				map.setApcg(cores[i].getApcgId());
				mapping.getMap().add(map);
			}

			ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory mappingFactory = new ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory();
			JAXBElement<MappingType> jaxbElement = mappingFactory
					.createMapping(mapping);

			try {
				JAXBContext jaxbContext = JAXBContext
						.newInstance(MappingType.class);
				Marshaller marshaller = jaxbContext.createMarshaller();
				marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
				marshaller.marshal(jaxbElement, stringWriter);
			} catch (JAXBException e) {
				logger.error("JAXB encountered an error", e);
			}

			// return stringWriter.toString();

		} catch (ClassNotFoundException e) {
			System.out.print("Class not found");
		} catch (JMException e) {
			System.out.println("JMP Exception occured");
		}
		return stringWriter.toString();

	}

	private void printCurrentMapping(int sol[]) {

		for (int i = 0; i < cores.length; i++) {
			int j;
			int currentIpCore = cores[i].getCoreNo();
			for (j = 0; j < this.noOfNodes; j++) {
				if (currentIpCore == sol[j]) {
					break;
				}
			}

			System.out.println("core " + cores[i].getCoreUid() + " (APCG "
					+ cores[i].getApcgId() + ") is mapped to Noc Node " + j);
		}

		System.out.println();
	}

	private void initializeCores() {
		cores = new Core[noOfIpCores];
		for (int i = 0; i < cores.length; i++) {
			cores[i] = new Core(i, null, -1);
		}
	}

	private void getCommunicatios() {

		this.communications = new ArrayList<Communication>();

		int communicationIndex = 0;
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
		System.err.println("usage:   java GeneticAlgorithmMapperV1.class [{populationSize} {maxEvolution} {crossoverPr} {mutationPr}] [--algo {algo name}] [E3S benchmarks]");
		System.err.println("1st parameter is number of population (population size)");
		System.err.println("2nd parameter is maximum number of evolution to run");
		System.err.println("3rd parameter is crossover probability (in real number) crossover probability < 1.00");
		System.err.println("4th parameter is mutation probability (in real number) mutation probability < 1.00");
		System.err.println("algorithm names are {ssGA, gGA scGA acGA ElitistES NonElitistES}");
		System.err.println("example 1 (specify the tgff file): java GeneticAlgorithmMapperV1.class 100 500000 0.8 0.005 --algo gGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 2 (specify the tgff file with default parameter): java GeneticAlgorithmMapperV1.class ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 3 (specify the tgff file with algo name): java GeneticAlgorithmMapperV1.class --algo gGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 4 (specify the tgff file with GA parameters): java GeneticAlgorithmMapperV1.class 100 50000 0.08 0.05 ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 5 (specify the ctg with all other parameters): java GeneticAlgorithmMapperV1.class 100 100000 0.8 0.05 --algo acGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3");
		System.err.println("example 6 (specify the ctg and algo name): java GeneticAlgorithmMapperV1.class --algo scGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3");
		System.err.println("example 7 (specify the ctg and apcg with all parameters): java GeneticAlgorithmMapperV1.class 100 100000 0.8 0.05 --algo scGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3 --apcg 2");
		System.err.println("example 8 (specify the ctg, apcg and algo name): java GeneticAlgorithmMapperV1.class --algo scGA ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3 --apcg 2");
		System.err.println("example 9 (map the entire E3S benchmark suite with all paremeters): java GeneticAlgorithmMapper.class 100 1000000 0.8 0.01 --algo gGA");
		System.err.println("example 10 (map the entire E3S benchmark suite with specific algo): java GeneticAlgorithmMapper.class --algo gGA");
		System.exit(-1);
	}

	public static void main(String args[]) throws TooFewNocNodesException,
			IOException, JAXBException, GeneticAlgorithmInputException {

		File[] tgffFiles = null;
		String specifiedCtgId = null;
		String specifiedApcgId = null;
		boolean is1stParameterString = false;

		final int defaultPopulationSize = 400;
		final int defaultMaxEvolutions = 5000000;
		final double defaultCrossoverPr = 0.85;
		final double defaultMutationPr = 0.05;
		final String defaultAlgorithmName = "ssGA";

		if (args.length == 0) {
			populationSize = defaultPopulationSize;
			maxEvolutions = defaultMaxEvolutions;
			crossoverPr = defaultCrossoverPr;
			mutationPr = defaultMutationPr;
			algorithmName = defaultAlgorithmName;
		} else {
			try {
				populationSize = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				is1stParameterString = true;

			}
			if (is1stParameterString == true) {
				populationSize = defaultPopulationSize;
				maxEvolutions = defaultMaxEvolutions;
				crossoverPr = defaultCrossoverPr;
				mutationPr = defaultMutationPr;
				if (args[0].startsWith("--algo")) {
					algorithmName = args[1];
				} else
					algorithmName = defaultAlgorithmName;

			} else {
				try {
					maxEvolutions = Integer.parseInt(args[1]);
					if (Double.parseDouble(args[2]) > 1.0)
						throw new GeneticAlgorithmInputException(
								"Crossover Probability must be less than 1.0");
					crossoverPr = Double.parseDouble(args[2]);
					if (Double.parseDouble(args[3]) > 1.0)
						throw new GeneticAlgorithmInputException(
								"Mutation Probability must be less than 1.0");
					mutationPr = Double.parseDouble(args[3]);
					if (args.length <= 4)
						algorithmName = defaultAlgorithmName;
					else if (args[4].startsWith("--algo")) {
						algorithmName = args[5];
					} else
						algorithmName = defaultAlgorithmName;

				} catch (NumberFormatException e) {
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

					logger.info("Using " + algorithmName
							+ " Genetic Algorithm mapper for " + path + "ctg-"
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

					JMetalGeneticAlgorithmMapper gaMapper = new JMetalGeneticAlgorithmMapper(
							ctgTypes, apcgTypes, noOfIpCores, nodeXmls.length);

					String mappingXml = gaMapper.map();

					File dir = new File(path + "ctg-" + ctgId);
					dir.mkdirs();

					String mappingXmlFilePath = path + "ctg-" + ctgId
							+ File.separator + "mapping-" + apcgId + "_" + gaMapper.getMapperId()
							+ ".xml";
					PrintWriter pw = new PrintWriter(mappingXmlFilePath);
					logger.info("Saving the mapping XML file"
							+ mappingXmlFilePath);
					logger.info("Saving the mapping XML file\n\n");
					pw.write(mappingXml);
					pw.close();

					// gaMapper.printCurrentMapping();
					// String mappingXml = gaMapper.map();

					/*
					 * File dir = new File(path + "ctg-" + ctgId); dir.mkdirs();
					 * 
					 * String mappingXmlFilePath = path + "ctg-" + ctgId +
					 * File.separator + "mapping-" + apcgId + "_" +
					 * gaMapper.getMapperId() + ".xml"; PrintWriter pw = new
					 * PrintWriter(mappingXmlFilePath);
					 * logger.info("Saving the mapping XML file" +
					 * mappingXmlFilePath);
					 * logger.info("Saving the mapping XML file");
					 * pw.write(mappingXml); pw.close();
					 * 
					 * gaMapper.printCurrentMapping();
					 */
				}

			}

			logger.info("Program End");
		}
	}
}
