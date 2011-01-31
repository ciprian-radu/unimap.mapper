package ro.ulbsibiu.acaps.mapper.ga;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.TaskType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CommunicationType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MapType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;

import jmetal.base.*;
import jmetal.base.operator.crossover.*;
import jmetal.base.operator.mutation.*;
import jmetal.base.operator.selection.*;
import jmetal.base.variable.Permutation;
import jmetal.metaheuristics.singleObjective.geneticAlgorithm.ssGA;
import jmetal.metaheuristics.singleObjective.geneticAlgorithm.gGA;
import jmetal.metaheuristics.singleObjective.geneticAlgorithm.scGA;
import jmetal.problems.singleObjective.*;
import jmetal.util.JMException;

public class GeneticAlgorithmMapperWithJmetal {

	private static int populationSize;

	private static int crossoverPr;

	private static int mutationPr;

	private static int noOfGenerationToRun;

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

	public GeneticAlgorithmMapperWithJmetal(List<CtgType> ctg,
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

	public String map() {
		StringWriter stringWriter = new StringWriter();
		try {
			problem = new MappingProblem(1, communications, cores, noOfNodes);

			 algorithm = new ssGA(problem);
			//algorithm = new gGA(problem);
			// algorithm = new scGA(problem) ;
			algorithm.setInputParameter("populationSize", populationSize);
			algorithm.setInputParameter("maxEvaluations", noOfGenerationToRun);

			crossover = CrossoverFactory
					.getCrossoverOperator("PMXCrossover");
			crossover.setParameter("probability", (double) crossoverPr / 100.0);
			crossover.setParameter("distributionIndex", 20.0);

			mutation = MutationFactory.getMutationOperator("SwapMutation");

			mutation.setParameter("probability", (double) mutationPr / 100.0);
			mutation.setParameter("distributionIndex", 20.0);

			selection = SelectionFactory
					.getSelectionOperator("BinaryTournament");

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
			Solution S = population.get(0);
			sol = ((Permutation) S.getDecisionVariables()[0]).vector_;
			for (int i = 0; i < sol.length; i++)
				System.out.print(sol[i] + " ");
			System.out.println();
			System.out.println();
			printCurrentMapping(sol);

			MappingType mapping = new MappingType();
			mapping.setId("ga");
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

			//return stringWriter.toString();

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

	void parseApcg(ApcgType apcg) {
		int i;
		for (i = 0; i < apcg.getCore().size(); i++) {
			cores[previousCoreCount + i].setApcgId(apcg.getId());
			cores[previousCoreCount + i].setCoreUid(apcg.getCore().get(i)
					.getUid());
		}
		previousCoreCount += i;
	}

	public static void main(String args[]) throws TooFewNocNodesException,
			IOException, JAXBException {

		/*
		 * Run the program For mapping the entire E3S benchmark suite: java
		 * SimulatedAnnealingMapper populationSize NoOfGenerationToRun
		 * CrossoverPr MutationPr For mapping a specific E3S benchmark : java
		 * GeneticAlgorithmMapper ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff
		 * populationSize NoOfGenerationToRun CrossoverPr MutationPr For mapping
		 * a specific Ctg of a benchmark: (example ctg-0) java
		 * GeneticAlgorithmMapper ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff
		 * --ctg 0 populationSize NoOfGenerationToRun CrossoverPr MutationPr
		 * java GeneticAlgorithmMapper
		 * ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3
		 * populationSize NoOfGenerationToRun CrossoverPr MutationPr
		 */

		if (args.length < 4) {
			logger.info("Please provide appropriate arguments");
		} else {

			File[] tgffFiles = null;
			String specifiedCtgId = null;

			populationSize = Integer.parseInt(args[0]);
			noOfGenerationToRun = Integer.parseInt(args[1]);
			crossoverPr = Integer.parseInt(args[2]);
			mutationPr = Integer.parseInt(args[3]);
			// here 20 percent of the total population size is used
			// tournamentSize = (populationSize * 20) / 100;

			if (args.length == 4) {
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
				for (i = 4; i < args.length; i++) {
					if (args[i].startsWith("--ctg")
							|| args[i].startsWith("--apcg")) {
						break;
					}
					tgffFileList.add(new File(args[i]));
				}
				tgffFiles = tgffFileList.toArray(new File[tgffFileList.size()]);
				if (args[i].startsWith("--ctg")) {
					logger.assertLog(args.length > i + 1,
							"Expecting CTG ID after --ctg option");
					specifiedCtgId = args[i + 1];
				}
				if (specifiedCtgId != null) {
					logger.info("Mapping only CTGs with ID " + specifiedCtgId);
				}
			}

			for (int i = 0; i < tgffFiles.length; i++) {
				String path = ".." + File.separator + "CTG-XML"
						+ File.separator + "xml" + File.separator + "e3s"
						+ File.separator + tgffFiles[i].getName()
						+ File.separator;

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
					List<ApcgType> apcgTypes = new ArrayList<ApcgType>();

					String apcgId = "";

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

						apcgs = apcgPath.list(new FilenameFilter() {

							@Override
							public boolean accept(File dir, String name) {
								return dir.isDirectory()
										&& name.startsWith("apcg-")
										&& name.endsWith("2.xml");
							}
						});

						for (int l = 0; l < apcgs.length; l++) {

							apcgId = ctgId + "_" + 2;

							jaxbContext = JAXBContext
									.newInstance("ro.ulbsibiu.acaps.ctg.xml.apcg");
							unmarshaller = jaxbContext.createUnmarshaller();
							@SuppressWarnings("unchecked")
							ApcgType apcg = ((JAXBElement<ApcgType>) unmarshaller
									.unmarshal(new File(apcgPath
											+ File.separator + apcgs[l])))
									.getValue();

							apcgTypes.add(apcg);

						}
					}

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

					/*
					 * for (int k = 0; k < apcgTypes.size(); k++) {
					 * gaMapper.parseApcg(apcgTypes.get(k)); }
					 */

					GeneticAlgorithmMapperWithJmetal gaMapper = new GeneticAlgorithmMapperWithJmetal(
							ctgTypes, apcgTypes, noOfIpCores, nodeXmls.length);

					String mappingXml = gaMapper.map();

					File dir = new File(path + "ctg-" + ctgId);
					dir.mkdirs();

					String mappingXmlFilePath = path + "ctg-" + ctgId
							+ File.separator + "mapping-" + apcgId + "_" + "ga"
							+ ".xml";
					PrintWriter pw = new PrintWriter(mappingXmlFilePath);
					logger.info("Saving the mapping XML file"
							+ mappingXmlFilePath);
					logger.info("Saving the mapping XML file");
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
