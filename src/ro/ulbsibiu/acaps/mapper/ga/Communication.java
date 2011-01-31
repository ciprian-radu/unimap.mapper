package ro.ulbsibiu.acaps.mapper.ga;

/*
 * The class store all the communications needed for each ctg and apcg
 * because its costly(time consuming) to parse XML for each time we need
 */
public class Communication {

	/* the source and destination Uid of the cores that communicate */
	private String sourceUid, destUid, apcgId;

	/* how much data will be transfered within source and destination */
	private double volume;

	public Communication(String apcgId, String sourceUid, String destUid,
			double volume) {
		this.apcgId = apcgId;
		this.sourceUid = sourceUid;
		this.destUid = destUid;
		this.volume = volume;
	}

	public String getSourceUid() {
		return sourceUid;

	}

	public String getdestUid() {
		return destUid;
	}

	public double getVolume() {
		return volume;
	}

	public String getApcgId() {
		return apcgId;

	}

	public void setSourceUid(String sourceUid) {
		this.sourceUid = sourceUid;

	}

	public void setdestUid(String destUid) {
		this.destUid = destUid;
	}

	public void setApcgId(String apcgId) {
		this.apcgId = apcgId;

	}

	public void setVolume(double volume) {
		this.volume = volume;
	}

}
