package ro.ulbsibiu.acaps.mapper.util;

import java.io.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.mapper.ga.ea.multiObjective.EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm;

import static ro.ulbsibiu.acaps.mapper.ga.ea.multiObjective.EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm.HOTSPOT_PATH;

/**
 * NoC floorplan generator, for Hotspot.
 * 
 * @see EnergyAndTemperatureAwareJMetalMultiObjectiveEvolutionAlgorithm
 * 
 * @author shaikat
 * @author cradu
 *
 */
public class NocFloorPlanGenerator {
	
	public static final double maxwidthOfCore = 0.0098;
	public static final double maxheightOfCore = 0.0196;

	public static final double routerWidth = 0.000460114;
	public static final double routerheight = 0.000460114;
	
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(NocFloorPlanGenerator.class);

	public static void main(String args[]) {
		try {
			
			CommandLineParser parser = new PosixParser();
			Options cliOptions = new Options();
			cliOptions.addOption("x", "x-tiles", true, "number of NoC tiles placed on a row");
			cliOptions.addOption("m", "max", false, "maximum number of NoC tiles placed on a row");
			cliOptions.addOption("h", "help", false, "generates floorplans: starting from 2 x 1, up to x x (x - 1), floorplans n x n and n x (n - 1) are generated");
			CommandLine cmd = parser.parse(cliOptions, args);
			
			int numberOfRow = Integer.parseInt(cmd.getOptionValue("x", "15"));

			double blockWidth = maxwidthOfCore + routerWidth;
			double blockHeight = maxheightOfCore + routerheight;

			for (int row = 2; row <= numberOfRow; row++) {
				for (int col = row - 1; col <= row; col++) {
					String filename = HOTSPOT_PATH + "flp" + File.separator
							+ row + "x" + col + ".flp";
					FileWriter fstream = new FileWriter(filename);
					logger.info("Generating file " + filename);
					BufferedWriter out = new BufferedWriter(fstream);
		
					int tilenumber = 0;
					for (int i = 0; i < col; i++) {
						for (int j = 0; j < row; j++) {
							out.write("Tile" + tilenumber + "\t" + blockWidth + "\t"
									+ blockHeight + "\t" + j * blockWidth + "\t" + i
									* blockHeight + "\n");
							tilenumber++;
						}
					}
					out.close();
				}
			}

			logger.info("Done.");
		} catch (Exception e) {
			logger.error(e);
		}

	}
}
