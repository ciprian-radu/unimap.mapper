package ro.ulbsibiu.acaps.mapper.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.BandwidthConstrainedEnergyAndPerformanceAwareMapper.LegalTurnSet;

/**
 * Helper class capable of processing the {@link Mapper} user input.
 * 
 * @author cipi
 * 
 */
public abstract class MapperInputProcessor {
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(MapperInputProcessor.class);
	
	/** command line interface options */
	private Options cliOptions;
	
	/**
	 * Default constructor
	 * 
	 * @param args
	 *            command line arguments
	 */
	public MapperInputProcessor() {
		initCliOptions();
	}
	
	private void initCliOptions() {
		cliOptions = new Options();
		cliOptions.addOption("b", "benchmarks", true, "the benchmarks file paths");
		cliOptions.addOption("c", "ctg", true, "the file path to the Communication Task Graph");
		cliOptions.addOption("a", "apcg", true, "the file path to the Application Characterization Graph");
		cliOptions.addOption("r", "with-routing", true, "the algorithm generates routes using West First (WEST_FIRST) or Odd Even (ODD_EVEN) legat turn set");
		cliOptions.addOption("l", "link-bandwidth", true, "the NoC links' bandwidth, in bits per second");
		cliOptions.addOption("s", "seed", true, "random generator seed");
		cliOptions.addOption("h", "help", false, "print this message");
	}

	/**
	 * @return the command line interface options
	 */
	public Options getCliOptions() {
		return cliOptions;
	}

	/**
	 * This abstract method must contain the code that uses the {@link Mapper}.
	 * It is called each time a new application must be mapped. Note that the
	 * {@link Mapper} might be required to map multiple CTGs at the same time.
	 * Each application (benchmark) is identified by a set of CTGs and a set of
	 * APCGs. Each CTG has exactly one corresponding APCG.
	 * 
	 * @param benchmarkFilePath
	 *            the benchmark file path
	 * @param benchmarkName
	 *            the benchmark's name
	 * @param ctgId
	 *            the CTG identifier
	 * @param apcgId
	 *            the APCG identifier
	 * @param ctgTypes
	 *            a list with the CTGs that represent the benchmark
	 * @param apcgTypes
	 *            a list with the APCGs that represent the benchmark
	 * @param doRouting
	 *            whether or not the user requested the {@link Mapper} to do
	 *            routing
	 * @param lts
	 *            the {@link LegalTurnSet} (useful only when doRouting is true)
	 * @param linkBandwidth
	 *            the NoC links' bandwidth, in bits per second
	 * @param seed
	 *            the seed for the random number generator of the initial
	 *            population
	 * @throws JAXBException
	 * @throws TooFewNocNodesException
	 * @throws FileNotFoundException
	 */
	public abstract void useMapper(String benchmarkFilePath,
			String benchmarkName, String ctgId, String apcgId,
			List<CtgType> ctgTypes, List<ApcgType> apcgTypes,
			boolean doRouting, LegalTurnSet lts, double linkBandwidth, Long seed)
			throws JAXBException,
			TooFewNocNodesException, FileNotFoundException;
	
	/**
	 * Process the command line arguments
	 * 
	 * @param args
	 *            the command line arguments
	 * @throws JAXBException
	 * @throws TooFewNocNodesException
	 * @throws FileNotFoundException
	 * @throws ParseException
	 */
	public void processInput(String[] args) throws JAXBException, TooFewNocNodesException, FileNotFoundException, ParseException {
		HelpFormatter formatter = new HelpFormatter();
		String helpMessage = "java <TheMapper>.class [-b E3S benchmarks] [-c ID] [-a ID] [-r] [-s seed]\n\n"
				+ "If -b is not used, all E3S benchmarks are considered."
				+ "Note that the algorithm can do or not routing."
				+ "The optional seed parameter can be used to control the initial mapping, which is randomly generated."
				+ "Example 1 (specify the benchmarks & require routing & impose seed): java {TheMapper}.class -b ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff -r -s 123456"
				+ "Example 2 (map the entire E3S benchmark suite): java {TheMapper}.class";
		
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = parser.parse(cliOptions, args);
		
		if (args == null || args.length < 1) {
			formatter.printHelp(helpMessage, cliOptions);
		} else {
			File[] tgffFiles = null;
			String specifiedCtgId = null;
			String specifiedApcgId = null;
			if (!cmd.hasOption("b")) {
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
				List<File> tgffFileList = new ArrayList<File>();
				String[] optionValues = cmd.getOptionValues("b");
				for (int i = 0; i < optionValues.length; i++) {
					tgffFileList.add(new File(optionValues[i]));
				}
				tgffFiles = tgffFileList.toArray(new File[tgffFileList.size()]);
				if (cmd.hasOption("c")) {
					specifiedCtgId = cmd.getOptionValue("c");
				}
				if (cmd.hasOption("a")) {
					specifiedApcgId = cmd.getOptionValue("a");
				}
				if (specifiedCtgId != null) {
					logger.info("Mapping only CTGs with ID " + specifiedCtgId);
				}
				if (specifiedApcgId != null) {
					logger.info("Mapping only APCGs with ID " + specifiedApcgId);
				}
			}

			for (int i = 0; i < tgffFiles.length; i++) {
				String path = tgffFiles[i].getPath() + File.separator;
				File e3sBenchmark = tgffFiles[i];
				String[] ctgs = null;
				if (specifiedCtgId != null) {
					ctgs = new String[] {"ctg-" + specifiedCtgId};
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
					// if the ctg ID contains + => we need to map multiple CTGs in one mapping XML
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
						
						File ctg = new File(path + "ctg-" + ctgIds[k]);
						apcgsList.addAll(Arrays.asList(ctg.listFiles(new ApcgFilenameFilter(ctgIds[k], specifiedApcgId))));
					}
					File[] apcgFiles = apcgsList.toArray(new File[apcgsList.size()]);
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
												.getAbsolutePath())))
										.getValue();
								apcgTypes.add(apcg);
							}
						}
						logger.assertLog(apcgTypes.size() == ctgTypes.size(), 
								"An equal number of CTGs and APCGs is expected!");
						
						Long seed = null;
						if (cmd.hasOption("s")) {
							try {
								seed = new Long(cmd.getOptionValue("s"));
							} catch (NumberFormatException e) {
								logger.fatal("Seed is not a number! Stoping...", e);
								System.exit(0);
							}
						}
						boolean routing = cmd.hasOption("r");
						if (routing) {
							String lts = cmd.getOptionValue("r");
							if (LegalTurnSet.ODD_EVEN.toString().equals(lts)) {
								useMapper(path, tgffFiles[i].getName(), ctgId,
										apcgId, ctgTypes, apcgTypes, routing,
										LegalTurnSet.ODD_EVEN,
										Double.valueOf(cmd.getOptionValue("l",
												Double.toString(256E9))), seed);
							} else {
								if (LegalTurnSet.WEST_FIRST.toString().equals(lts)) {
									useMapper(path, tgffFiles[i].getName(), ctgId,
											apcgId, ctgTypes, apcgTypes, routing,
											LegalTurnSet.WEST_FIRST,
											Double.valueOf(cmd.getOptionValue("l",
													Double.toString(256E9))), seed);
								} else {
									logger.warn("Unknown legal turn set. Using ODD_EVEN!");
									useMapper(path, tgffFiles[i].getName(), ctgId,
											apcgId, ctgTypes, apcgTypes, routing,
											LegalTurnSet.ODD_EVEN,
											Double.valueOf(cmd.getOptionValue("l",
													Double.toString(256E9))), seed);
								}
							}
						} else {
							useMapper(path, tgffFiles[i].getName(), ctgId,
									apcgId, ctgTypes, apcgTypes, routing,
									LegalTurnSet.ODD_EVEN,
									Double.valueOf(cmd.getOptionValue("l",
											Double.toString(256E9))), seed);
						}
						
						// increment the mapper database run ID after each
						// mapped CTG, except the last one (no need to do this
						// for the last one)
						if (i < tgffFiles.length - 1 || j < ctgs.length - 1) {
							MapperDatabase.getInstance().incrementRun();
						}
					}
				}
			}
			MapperDatabase.getInstance().close();
			logger.info("Done.");
		}
	}
}
