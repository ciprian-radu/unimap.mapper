package ro.ulbsibiu.acaps.mapper.util;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Internal {@link FilenameFilter} for APCG XML files
 * 
 * @author cradu
 * 
 */
public class ApcgFilenameFilter implements FilenameFilter {

	private String ctgId;
	
	private String apcgId;

	public ApcgFilenameFilter(String ctgId) {
		this(ctgId, null);
	}
	
	public ApcgFilenameFilter(String ctgId, String apcgId) {
		this.ctgId = ctgId;
		this.apcgId = apcgId;
	}

	@Override
	public boolean accept(File dir, String name) {
		String apcgName = "apcg-" + ctgId;
		if (apcgId != null) {
			apcgName += "_" + apcgId;
		}
		return name.endsWith(".xml") && name.contains(apcgName);
	}

}