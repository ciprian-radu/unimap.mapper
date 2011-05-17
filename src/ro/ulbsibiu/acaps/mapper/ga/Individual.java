package ro.ulbsibiu.acaps.mapper.ga;

/**
 * Helper class for representing a Genetic Algorithm individual (chromosome). A
 * chromosome is made of more genes. Each gene is an <IP core, NoC node> pair.
 * The individual is basically kept as an array of genes (e.g.: genes[1] = 2 =>
 * NoC node 1 has IP core 2 placed onto it). The individual fitness is also
 * stored.
 * 
 * @author shaikat
 * @author cradu
 */

public class Individual {

	/** the genes that form this individual (chromosome) */
	private int[] genes;

	/** the fitness of this individual */
	private double fitness;

	/**
	 * Default constructor
	 * 
	 * @param genes
	 *            the genes
	 * @param fitness
	 *            the fitness
	 */
	public Individual(int[] genes, double fitness) {
		this.genes = genes;
		this.fitness = fitness;
	}

	/**
	 * @return the genes
	 */
	public int[] getGenes() {
		return genes;
	}

	/**
	 * @return the fitness
	 */
	public double getFitness() {
		return this.fitness;
	}
}
