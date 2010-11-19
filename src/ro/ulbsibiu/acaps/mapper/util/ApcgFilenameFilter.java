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

	public ApcgFilenameFilter(String ctgId) {
		this.ctgId = ctgId;
	}

	@Override
	public boolean accept(File dir, String name) {
		return name.endsWith(".xml") && name.contains("apcg-" + ctgId);
	}

}