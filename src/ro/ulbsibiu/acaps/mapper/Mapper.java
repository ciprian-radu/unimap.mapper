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
	 * Maps the cores from the APCG to the nodes from the NoC.
	 * 
	 * @see MappingType
	 * 
	 * @return a String containing the mapping XML
	 * 
	 * @throws TooFewNocNodesException
	 */
	public abstract String map() throws TooFewNocNodesException;

}