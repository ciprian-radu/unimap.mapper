package ro.ulbsibiu.acaps.mapper.bb;

/**
 * @author cipi
 * 
 */
class ProcComm {
	int src_proc;

	int dst_proc;

	int BW;

	int adaptivity; // only useful in routing synthesis

	// only useful energy aware routing
	int volume;

	float rate;
}
