package ro.ulbsibiu.acaps.mapper.ga.ea.multiObjective;

import ro.ulbsibiu.acaps.mapper.sa.Core;

/**
 * @author shaikat
 *
 */
public class CorePower extends Core {

	private double totalConsumedPower = 0.0;
	
	private double idlepower = 0.0;
	
	
	/**
	 * Constructor
	 * 
	 * @param coreId
	 *            the ID of the core
	 * @param apcgId
	 *            the ID of the APCG to which this cores belongs to
	 * @param nodeId
	 *            the ID of the node to which this core is assigned to
	 */
	public CorePower(int coreId, String apcgId, int nodeId) {
		super (coreId, apcgId,nodeId);
	}

	
	public void setTotalComsumedPower(double totalConsumedPower){
		this.totalConsumedPower = totalConsumedPower; 
	}
	
	public double getTotalConsumedPower(){
		return this.totalConsumedPower;
	}
	
	public void setIdlePower(double idlePower){
		this.idlepower = idlePower;
		
	}
	public double getIdlePower(){
		return this.idlepower;
	}
}

