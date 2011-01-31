package ro.ulbsibiu.acaps.mapper.ga;

import java.util.Arrays;



/**
 * @author shaikat
 * This class is for each individual 
 * The important thing is that its need to calculate the fitness only for once for each individual
 */

public class Individual {
	
	int []gene;
	double fitness;
	int sizeOfGene;
	
	public Individual(int sizeOfGene, int []gene, double fitness) {
		this.sizeOfGene = sizeOfGene;
		this.gene = new int[sizeOfGene];
		this.gene = Arrays.copyOf(gene, gene.length);
		this.fitness = fitness;
	}
	
	void setGene(int [] gene){
		this.gene = Arrays.copyOf(gene, gene.length);
	}
	
	void setSizeOfGene(int sizeOfGene){
		this.sizeOfGene = sizeOfGene;
	}
	
	void setFitness(double fitness){
		this.fitness = fitness;
	}
	
	int [] getGene(){
		return this.gene;
	}
	
	int getSizeOfGene(){
		return this.sizeOfGene;
	}
	
	double getFitness(){
		return this.fitness;
	}
}
