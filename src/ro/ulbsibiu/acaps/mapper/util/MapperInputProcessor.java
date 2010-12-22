package ro.ulbsibiu.acaps.mapper.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.MapperDatabase;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;

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
	
	/**
	 * Default constructor
	 * 
	 * @param args
	 *            command line arguments
	 */
	public MapperInputProcessor() {
		;
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
	 * @throws JAXBException
	 * @throws TooFewNocNodesException
	 * @throws FileNotFoundException
	 */
	public abstract void useMapper(String benchmarkFilePath,
			String benchmarkName, String ctgId, String apcgId,
			List<CtgType> ctgTypes, List<ApcgType> apcgTypes, boolean doRouting)
			throws JAXBException, TooFewNocNodesException,
			FileNotFoundException;
	
	/**
	 * Process the command line arguments
	 * 
	 * @param args
	 *            the command line arguments
	 * @throws JAXBException
	 * @throws TooFewNocNodesException
	 * @throws FileNotFoundException
	 */
	public void processInput(String[] args) throws JAXBException, TooFewNocNodesException, FileNotFoundException {
		if (args == null || args.length < 1) {
			System.err.println("usage:   java {TheMapper}.class [E3S benchmarks] [--ctg {ID}] [--apcg {ID}] {false|true}");
			System.err.println("	     Note that false or true specifies if the algorithm should generate routes (routing may be true or false; any other value means false)");
			System.err.println("example 1 (specify the tgff file): java {TheMapper}.class ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff false");
			System.err.println("example 2 (map the entire E3S benchmark suite): java {TheMapper}.class false");
		} else {
			File[] tgffFiles = null;
			String specifiedCtgId = null;
			String specifiedApcgId = null;
			if (args.length == 1) {
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
				List<File> tgffFileList = new ArrayList<File>(args.length - 1);
				int i;
				for (i = 0; i < args.length - 1; i++) {
					if (args[i].startsWith("--ctg") || args[i].startsWith("--apcg")) {
						break;
					}
					tgffFileList.add(new File(args[i]));
				}
				tgffFiles = tgffFileList.toArray(new File[tgffFileList.size()]);
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
				i += 2;
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

			for (int i = 0; i < tgffFiles.length; i++) {
				String path = ".." + File.separator + "CTG-XML" + File.separator
						+ "xml" + File.separator + "e3s" + File.separator
						+ tgffFiles[i].getName() + File.separator;
				
				File e3sBenchmark = new File(path);
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
						
						useMapper(path, tgffFiles[i].getName(), ctgId, apcgId, ctgTypes, apcgTypes, "true".equals(args[args.length - 1]));
						
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
