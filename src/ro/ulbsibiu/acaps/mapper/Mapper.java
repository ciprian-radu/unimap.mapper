package ro.ulbsibiu.acaps.mapper;

import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;

/**
 * <p>
 * A mapper has the responsibility to map the cores from an APplication
 * Characterization Graph to the nodes from a network-on-Chip.
 * </p>
 * 
 * @author cipi
 * 
 */
public interface Mapper {

	/**
	 * A {@link Mapper} is uniquely identified with a {@link String}. This
	 * helps differentiating mapping XMLs produced with different mappers.
	 * 
	 * @return the unique identifier of this mapper
	 */
	public abstract String getMapperId();
	
	/**
	 * Maps the cores from the APCG to the nodes from the NoC.
	 * 
	 * @see MappingType
	 * 
	 * @return a array of Strings containing the mappings (specially for multi-objective case) XML
	 * 
	 * @throws TooFewNocNodesException
	 */
	public abstract String[] map() throws TooFewNocNodesException;

}