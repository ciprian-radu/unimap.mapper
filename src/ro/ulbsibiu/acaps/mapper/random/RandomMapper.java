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
 * This @link{Mapper} maps the cores to nodes in a random fashion.
 * 
 * @author cipi
 * 
 */
public class RandomMapper implements Mapper {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(RandomMapper.class);

	/** the APCG XML file */
	private File apcgFile;

	/** a list with the IDs of all of the NoC nodes */
	private List<String> nodeIds;

	/** holds the mapping (cores are identified by IDs) */
	private Map<String, String> coresToNodes;

	/**
	 * Constructor
	 * 
	 * @param apcgFilePath
	 *            the APCG XML file path (cannot be empty)
	 * @param nodeIds
	 *            a list with the IDs of all of the NoC nodes (cannot be empty)
	 */
	public RandomMapper(String apcgFilePath, List<String> nodeIds) {
		logger.assertLog(apcgFilePath != null && apcgFilePath.length() > 0,
				"Unspecified APCG");
		logger.assertLog(apcgFilePath.endsWith(".xml"),
				"The APCG must be an XML file");
		logger.assertLog(nodeIds != null && nodeIds.size() > 0,
				"The list of NoC nodes is not specified");

		logger.info("Working with the APCG from the XML " + apcgFilePath);

		apcgFile = new File(apcgFilePath);
		logger.assertLog(apcgFile.isFile(), "The APCG is not a file");

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
			String[] coreIds = getCoreIds();
			if (coreIds.length > nodeIds.size()) {
				throw new TooFewNocNodesException(coreIds.length,
						nodeIds.size());
			}
			coresToNodes = new HashMap<String, String>(coreIds.length);
			Random random = new Random();
			// using the following temporary list we ensure that each core gets
			// mapped to a different node
			List<String> tempNodeIds = new ArrayList<String>(nodeIds);
			for (int i = 0; i < coreIds.length; i++) {
				int k = random.nextInt(tempNodeIds.size());
				coresToNodes.put(coreIds[i], tempNodeIds.get(k));
				logger.info("Core " + coreIds[i] + " is mapped to node "
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

	private String[] getCoreIds() throws JAXBException {
		JAXBContext jaxbContext = JAXBContext
				.newInstance("ro.ulbsibiu.acaps.ctg.xml.apcg");
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		JAXBElement<ApcgType> apcgXml = (JAXBElement<ApcgType>) unmarshaller
				.unmarshal(apcgFile);
		List<ro.ulbsibiu.acaps.ctg.xml.apcg.CoreType> cores = apcgXml
				.getValue().getCore();

		String[] coreIds = new String[cores.size()];
		for (int i = 0; i < cores.size(); i++) {
			coreIds[i] = cores.get(i).getId();
		}

		return coreIds;
	}

	private String getApcgId() throws JAXBException {
		JAXBContext jaxbContext = JAXBContext
				.newInstance("ro.ulbsibiu.acaps.ctg.xml.apcg");
		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
		JAXBElement<ApcgType> apcgXml = (JAXBElement<ApcgType>) unmarshaller
				.unmarshal(apcgFile);

		return apcgXml.getValue().getId();
	}

	private String generateMap() throws JAXBException {
		logger.assertLog(coresToNodes != null, "No core was mapped!");

		if (logger.isDebugEnabled()) {
			logger.debug("Generating an XML String with the mapping");
		}

		ObjectFactory mappingFactory = new ObjectFactory();
		MappingType mappingType = new MappingType();
		String id = getApcgId();
		mappingType.setId(id);
		mappingType.setApcg(id);

		Set<String> cores = coresToNodes.keySet();
		for (String core : cores) {
			MapType mapType = new MapType();
			mapType.setCore(core);
			mapType.setNode(coresToNodes.get(core));
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

		for (int i = 0; i < 2; i++) {
			String mappingId = Integer.toString(i);
			String apcgId = Integer.toString(i);
			for (int j = 0; j < 4; j++) {
				String ctgId = Integer.toString(j);
				String path = "xml" + File.separator + "e3s" + File.separator
						+ e3sBenchmark + File.separator;
				List<String> nodeIds = new ArrayList<String>();
				for (int k = 0; k < 16; k++) {
					nodeIds.add(Integer.toString(k));
				}
				Mapper mapper = new RandomMapper(path + "ctg-" + ctgId
						+ File.separator + "apcg-" + apcgId + ".xml", nodeIds);
				String mappingXml = mapper.map();
				PrintWriter pw = new PrintWriter(path + "ctg-" + ctgId
						+ File.separator + "mapping-" + mappingId + ".xml");
				logger.info("Saving the mapping XML file");
				pw.write(mappingXml);
				pw.close();
			}
		}
	}

}
