package ro.ulbsibiu.acaps.mapper.bb;

/**
 * Helper class for keeping information regarding the communication between two
 * processes.
 * 
 * @author cipi
 * 
 */
class ProcComm {

	/** the source process (i.e. the one who generates the communication) */
	public int srcProc;

	/**
	 * the destination process (i.e. the one who receives the data from the
	 * source process)
	 */
	public int dstProc;

	/** the bandwidth requirement for this communication */
	public long bandwidth;

	/** (only useful in routing synthesis) */
	public int adaptivity;

}
