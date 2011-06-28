package ro.ulbsibiu.acaps.mapper.random;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MapType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.util.ApcgFilenameFilter;

/**
 * This @link{Mapper} maps the cores to nodes in a random fashion. The cores are
 * described in APCG XML files. Each APCG has a corresponding CTG. The CTG
 * describes the application, as a set of communicating tasks. Having more APCGs
 * / CTGs allows building a mapping for all of them.
 * 
 * @author cipi
 * 
 */
public class RandomMapper implements Mapper {

	/**
	 * Helper class that associates a core with its APCG
	 * 
	 * @author cradu
	 *
	 */
	private static class ApcgCore {
		
		private String apcgId;
		
		private String coreId;

		public ApcgCore(String apcgId, String coreId) {
			this.apcgId = apcgId;
			this.coreId = coreId;
		}

		public String getApcgId() {
			return apcgId;
		}

		public String getCoreId() {
			return coreId;
		}
		
	}
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(RandomMapper.class);
	
	private static final String MAPPER_ID = "0";

	/** the ID of the generated mapping XML */
	private String id;
	
	/** the APCG XML files */
	private List<File> apcgFiles;

	/** a list with the IDs of all of the NoC nodes */
	private List<String> nodeIds;

	/** holds the mapping (cores are identified by IDs) */
	private Map<ApcgCore, String> coresToNodes;

	/**
	 * Constructor
	 * 
	 * @param apcgId
	 *            the ID of the APCG (cannot be empty)
	 * @param apcgFilePaths
	 *            the APCG XML file paths (cannot be empty)
	 * @param nodeIds
	 *            a list with the IDs of all of the NoC nodes (cannot be empty)
	 */
	public RandomMapper(String apcgId, List<String> apcgFilePaths, List<String> nodeIds) {
		logger.assertLog(apcgId != null && apcgId.length() > 0,
				"No APCG XML ID specified");
		logger.assertLog(apcgFilePaths != null && apcgFilePaths.size() > 0,
				"No APCG specified");
		logger.assertLog(nodeIds != null && nodeIds.size() > 0,
				"The list of NoC nodes is not specified");

		this.id = apcgId + "_" + getMapperId();
		apcgFiles = new ArrayList<File>();
		
		for (int i = 0; i < apcgFilePaths.size(); i++) {
			logger.assertLog(apcgFilePaths.get(i).endsWith(".xml"),
					"The APCG must be an XML file");
			File apcgFile = new File(apcgFilePaths.get(i));
			logger.assertLog(apcgFile.isFile(), "The APCG is not a file");
			
			logger.info("Working with the APCG from the XML " + apcgFilePaths.get(i));
			apcgFiles.add(apcgFile);
		}

		this.nodeIds = nodeIds;
	}
	
	@Override
	public String getMapperId() {
		return MAPPER_ID;
	}

	/**
	 * Maps the cores to nodes in a random fashion
	 * 
	 * @see MappingType
	 * 
	 * @return a String containing the mapping XML
	 */
	@Override
	public String[] map() throws TooFewNocNodesException {
		if (logger.isDebugEnabled()) {
			logger.debug("Random mapping started");
		}

		String mappingXml = null;

		try {
			ApcgCore[] apcgCores = getApcgCores();
			if (apcgCores.length > nodeIds.size()) {
				throw new TooFewNocNodesException(apcgCores.length,
						nodeIds.size());
			}
			coresToNodes = new HashMap<ApcgCore, String>(apcgCores.length);
			Random random = new Random();
			// using the following temporary list we ensure that each core gets
			// mapped to a different node
			List<String> tempNodeIds = new ArrayList<String>(nodeIds);
			for (int i = 0; i < apcgCores.length; i++) {
				int k = random.nextInt(tempNodeIds.size());
				coresToNodes.put(apcgCores[i], tempNodeIds.get(k));
				logger.info("Core " + apcgCores[i].getCoreId() + " (APCG "
						+ apcgCores[i].getApcgId() + ") is mapped to node "
						+ tempNodeIds.get(k));
				tempNodeIds.remove(k);
			}
			mappingXml = generateMap();

		} catch (JAXBException e) {
			logger.error("JAXB encountered an error", e);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Random mapping finished");
		}

		return new String[] { mappingXml };
	}

	private ApcgCore[] getApcgCores() throws JAXBException {
		List<ApcgCore> apcgCores = new ArrayList<ApcgCore>();

		JAXBContext jaxbContext = JAXBContext
				.newInstance("ro.ulbsibiu.acaps.ctg.xml.apcg");
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		for (int i = 0; i < apcgFiles.size(); i++) {
			@SuppressWarnings("unchecked")
			JAXBElement<ApcgType> apcgXml = (JAXBElement<ApcgType>) unmarshaller
					.unmarshal(apcgFiles.get(i));
			List<ro.ulbsibiu.acaps.ctg.xml.apcg.CoreType> cores = apcgXml
					.getValue().getCore();

			for (int j = 0; j < cores.size(); j++) {
				apcgCores.add(new ApcgCore(apcgXml.getValue().getId(), cores
						.get(j).getUid()));
			}
		}

		return apcgCores.toArray(new ApcgCore[apcgCores.size()]);
	}

	private String generateMap() throws JAXBException {
		logger.assertLog(coresToNodes != null, "No core was mapped!");

		if (logger.isDebugEnabled()) {
			logger.debug("Generating an XML String with the mapping");
		}

		ObjectFactory mappingFactory = new ObjectFactory();
		MappingType mappingType = new MappingType();
		mappingType.setId(id);

		Set<ApcgCore> cores = coresToNodes.keySet();
		for (ApcgCore apcgCore : cores) {
			MapType mapType = new MapType();
			mapType.setApcg(apcgCore.getApcgId());
			mapType.setCore(apcgCore.getCoreId());
			mapType.setNode(coresToNodes.get(apcgCore));
			mappingType.getMap().add(mapType);
		}

		JAXBContext jaxbContext = JAXBContext.newInstance(MappingType.class);
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
		StringWriter stringWriter = new StringWriter();
		JAXBElement<MappingType> mapping = mappingFactory
				.createMapping(mappingType);
		marshaller.marshal(mapping, stringWriter);

		return stringWriter.toString();
	}

	public static void main(String[] args) throws FileNotFoundException,
			TooFewNocNodesException {
		System.err.println("usage:   java RandomMapper.class [E3S benchmarks]");
		System.err.println("example 1 (specify the tgff file): java RandomMapper.class ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff");
		System.err.println("example 2 (map the entire E3S benchmark suite): java RandomMapper.class");
		File[] tgffFiles = null;
		if (args == null || args.length == 0) {
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
			tgffFiles = new File[args.length];
			for (int i = 0; i < args.length; i++) {
				tgffFiles[i] = new File(args[i]);
			}
		}
		for (int i = 0; i < tgffFiles.length; i++) {
			String path = ".." + File.separator + "CTG-XML" + File.separator
					+ "xml" + File.separator + "e3s" + File.separator
					+ tgffFiles[i].getName() + File.separator;
			File e3sBenchmark = new File(path);
			String[] ctgs = e3sBenchmark.list(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return dir.isDirectory() && name.startsWith("ctg-");
				}
			});
			for (int j = 0; j < ctgs.length; j++) {
				String ctgId = ctgs[j].substring("ctg-".length());
				// if the ctg ID contains + => we need to map multiple CTGs in one mapping XML
				String[] ctgIds = ctgId.split("\\+");
				List<File> apcgsList = new ArrayList<File>();
				for (int k = 0; k < ctgIds.length; k++) {
					File ctg = new File(path + "ctg-" + ctgIds[k]);
					apcgsList.addAll(Arrays.asList(ctg.listFiles(new ApcgFilenameFilter(ctgIds[k]))));
				}
				File[] apcgFiles = apcgsList.toArray(new File[apcgsList.size()]);
				for (int l = 0; l < apcgFiles.length / ctgIds.length; l++) {
					String apcgId = ctgId + "_" + l;
					List<String> apcgFilePaths = new ArrayList<String>();
					for (int k = 0; k < apcgFiles.length; k++) {
						if (apcgFiles[k].getName().endsWith(l + ".xml")) {
							apcgFilePaths.add(apcgFiles[k].getAbsolutePath());
						}
					}

					List<String> nodeIds = new ArrayList<String>();
					for (int n = 0; n < 64; n++) {
						nodeIds.add(Integer.toString(n));
					}

					logger.info("Using a random mapper for "
							+ path + ctgId + " (APCG " + apcgId + ")");
					
					Mapper mapper = new RandomMapper(apcgId, apcgFilePaths,
							nodeIds);
					String[] mappingXml = mapper.map();
					String mappingId = apcgId + "_" + mapper.getMapperId();
					String xmlFileName = path + "ctg-" + ctgId + File.separator
							+ "mapping-" + mappingId + ".xml";
					PrintWriter pw = new PrintWriter(xmlFileName);
					logger.info("Saving the mapping XML file " + xmlFileName);
					pw.write(mappingXml[0]);
					pw.close();
				}
			}
			logger.info("Finished with e3s" + File.separator
					+ tgffFiles[i].getName());
		}
		logger.info("Done.");
	}

}
