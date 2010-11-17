package ro.ulbsibiu.acaps.mapper.random;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
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
	 * @param id (cannot be empty)
	 *            the ID of the generated mapping XML
	 * @param apcgFilePaths
	 *            the APCG XML file paths (cannot be empty)
	 * @param nodeIds
	 *            a list with the IDs of all of the NoC nodes (cannot be empty)
	 */
	public RandomMapper(String id, List<String> apcgFilePaths, List<String> nodeIds) {
		logger.assertLog(id != null && id.length() > 0,
				"No mapping XML ID specified");
		logger.assertLog(apcgFilePaths != null && apcgFilePaths.size() > 0,
				"No APCG specified");
		logger.assertLog(nodeIds != null && nodeIds.size() > 0,
				"The list of NoC nodes is not specified");

		this.id = id;
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

	/**
	 * Maps the cores to nodes in a random fashion
	 * 
	 * @see MappingType
	 * 
	 * @return a String containing the mapping XML
	 */
	@Override
	public String map() throws TooFewNocNodesException {
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
				logger.info("Core " + apcgCores[i].getCoreId() + "(APCG "
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

		return mappingXml;
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
						.get(j).getId()));
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
		StringWriter stringWriter = new StringWriter();
		JAXBElement<MappingType> mapping = mappingFactory
				.createMapping(mappingType);
		marshaller.marshal(mapping, stringWriter);

		return stringWriter.toString();
	}

	public static void main(String[] args) throws FileNotFoundException,
			TooFewNocNodesException {
		String e3sBenchmark = "auto-indust-mocsyn.tgff";

		// apply mapping for folders ctg-0, ctg-1, ctg-2, ctg-3 (we use an 4x4 2D mesh NoC)
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 4; j++) {
				String ctgId = Integer.toString(j);
				String apcgId = ctgId + "_"  + Integer.toString(i);
				String mappingId = apcgId + "_0";
				String path = "xml" + File.separator + "e3s" + File.separator
						+ e3sBenchmark + File.separator;
				List<String> nodeIds = new ArrayList<String>();
				for (int k = 0; k < 16; k++) {
					nodeIds.add(Integer.toString(k));
				}
				List<String> apcgFilePaths = new ArrayList<String>();
				apcgFilePaths.add(path + "ctg-" + ctgId + File.separator
						+ "apcg-" + apcgId + ".xml");
				Mapper mapper = new RandomMapper(mappingId, apcgFilePaths,
						nodeIds);
				String mappingXml = mapper.map();
				PrintWriter pw = new PrintWriter(path + "ctg-" + ctgId
						+ File.separator + "mapping-" + mappingId + ".xml");
				logger.info("Saving the mapping XML file");
				pw.write(mappingXml);
				pw.close();
			}
		}
		
		// apply mapping for CTGs 0 and 1 - folder ctg-0+1 (we use a 4x4 2D mesh NoC)
		String ctgId = "0+1";
		for (int i = 0; i < 2; i++) {
			String apcgId = ctgId + "_" + Integer.toString(i);
			String mappingId = apcgId + "_0";
			String path = "xml" + File.separator + "e3s" + File.separator
					+ e3sBenchmark + File.separator;
			List<String> nodeIds = new ArrayList<String>();
			for (int k = 0; k < 16; k++) {
				nodeIds.add(Integer.toString(k));
			}
			List<String> apcgFilePaths = new ArrayList<String>();
			String[] ctgIds = ctgId.split("\\+");
			for (int j = 0; j < ctgIds.length; j++) {
				apcgFilePaths.add(path + "ctg-" + ctgId + File.separator
						+ "apcg-" + ctgIds[j] + "_" + Integer.toString(i)
						+ ".xml");
			}
			Mapper mapper = new RandomMapper(mappingId, apcgFilePaths, nodeIds);
			String mappingXml = mapper.map();
			PrintWriter pw = new PrintWriter(path + "ctg-" + ctgId
					+ File.separator + "mapping-" + mappingId + ".xml");
			logger.info("Saving the mapping XML file");
			pw.write(mappingXml);
			pw.close();
		}
	}

}
