package ro.ulbsibiu.acaps.mapper;

/**
 * This exception must be thrown when the number of core to be mapped exceeds
 * the number of nodes available in the given NoC.
 * 
 * @author cipi
 * 
 */
public class TooFewNocNodesException extends Exception {

	/** automatically generated serial version UID */
	private static final long serialVersionUID = 7661421153357403295L;

	/** the number of cores that need to be mapped */
	private int cores;

	/** the number of available nodes */
	private int nodes;

	/**
	 * Constructor
	 * 
	 * @param cores
	 *            the number of cores that need to be mapped
	 * @param nodes
	 *            the number of available nodes
	 */
	public TooFewNocNodesException(int cores, int nodes) {
		super();

		assert cores > 0;
		assert nodes > 0;
		assert cores > nodes;

		this.cores = cores;
		this.nodes = nodes;
	}

	@Override
	public String getLocalizedMessage() {
		return "The number of NoC nodes ("
				+ nodes
				+ ") is too small. At least "
				+ cores
				+ " are needed (this is the amount of cores which must be mapped).";
	}

}
