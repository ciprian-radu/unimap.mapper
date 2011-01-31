package ro.ulbsibiu.acaps.mapper.ga;

import java.util.ArrayList;

import jmetal.base.Problem;
import jmetal.base.Solution;
import jmetal.base.SolutionType;

import jmetal.base.Variable;
import jmetal.base.variable.Binary;

import jmetal.base.solutionType.BinaryRealSolutionType;
import jmetal.base.solutionType.BinarySolutionType;
import jmetal.base.solutionType.IntSolutionType;
import jmetal.base.solutionType.PermutationSolutionType;
import jmetal.base.solutionType.RealSolutionType;
import jmetal.base.variable.Permutation;
import jmetal.util.JMException;

public class MappingProblem extends Problem {
	
	private ArrayList<Communication> communications;
	private Core[] cores;
	private int noOfNodes;
	public MappingProblem(Integer numberOfVariables, ArrayList<Communication> communications,
						    Core[] cores, int noOfNodes	)  throws ClassNotFoundException {
		this.communications = communications;
		this.cores = cores;
		this.noOfNodes = noOfNodes;
		
		numberOfVariables_   = numberOfVariables ;
	    numberOfObjectives_  = 1;
		numberOfConstraints_ = 0;
		
		problemName_         = "MappingProblem";
		
		upperLimit_ = new double[numberOfVariables_];
	    lowerLimit_ = new double[numberOfVariables_];
	    length_     = new int[numberOfVariables_];
	    
	    for (int var = 0; var < numberOfVariables_; var++){
	    	
	    	length_[var] = noOfNodes;   
	    	lowerLimit_[var] = 0;
	    	upperLimit_[var] = noOfNodes - 1;
	    }
	    
		solutionType_ = new PermutationSolutionType(this);
		
		
	}
	
	public void evaluate(Solution solution) throws JMException {
		//raw fitness of the individual
		double fitOfIndv = 0.0;
		
		int permutation[] ;
	    int permutationLength ;
	     
	    permutationLength = ((Permutation)solution.getDecisionVariables()[0]).getLength() ;
	    permutation = ((Permutation)solution.getDecisionVariables()[0]).vector_;
		
		//individual whose fitness need to be measured
		/*int indv[] = new int[noOfNodes];
		
		if(isInPopulation)
			indv = population[posOfIndv];
		else
			indv = newPopulation[posOfIndv];
*/
		/*now I want to find where source and destination IP core
		 * is placed in NOC node
		 */
		
		/*
		 *  posOfSourceIpcoreInNocNode -> position of the source Ip core in the Noc node
		 *  posOfDestIpcoreInNocNode -> position of the destination Ip core in the Noc node
		 *  
		 *  posOfSourceIPCoreinCores -> position of the source IP core in cores array
		 *  posOfDestIPCoreinCores -> position of the destination IP core in cores array	
		 */
	
		for(int i = 0; i < communications.size(); i++){
			
			int posOfSourceIpCoreInNocNode = -1, posOfDestIpCoreInNocNode = -1, posOfSourceIPCoreInCores = -1, posOfDestIPCoreInCores = -1;
				
			/*first i want to find which core (in integer number) source and destination IPcore is*/
			
			for(int j = 0; j < cores.length; j++) {
				if(this.communications.get(i).getApcgId().equals(cores[j].getApcgId()) && this.communications.get(i).getSourceUid().equals(cores[j].getCoreUid())) {
					posOfSourceIPCoreInCores = j;
					break;
				}
			}
		
			for(int j = 0; j < cores.length; j++) {
				if(this.communications.get(i).getApcgId().equals(cores[j].getApcgId()) && this.communications.get(i).getdestUid().equals(cores[j].getCoreUid())) {
					posOfDestIPCoreInCores = j;
					break;
				}
			}
		
		
			for(int j = 0; j < noOfNodes; j++) {
				if(posOfSourceIPCoreInCores == permutation[j]) {
					posOfSourceIpCoreInNocNode = j;
					break;
				}
			}
			
			for(int j = 0; j < noOfNodes; j++) {
				if(posOfDestIPCoreInCores == permutation[j]) {
					posOfDestIpCoreInNocNode = j;
					break;
				}
			}
		
			/*get the position (x, y) of the source and destination IP core 
			 * in the Noc Matrix (4x4)
			 */
			/* (xSource, ySource)-> position of source IP core in MxM matrix
			 * (xDest, yDest)-> position of destination IP core in MxM matrix
			 */
			
			int xSource, ySource, xDest, yDest, M;
			M = (int) Math.sqrt(noOfNodes);
			
			xSource = posOfSourceIpCoreInNocNode / M;
			ySource = posOfSourceIpCoreInNocNode % M;
			
			xDest = posOfDestIpCoreInNocNode / M;
			yDest = posOfDestIpCoreInNocNode % M;
			
			/* Now find out the manhattan distance between source and destination node
			 * Formula: |(X1-x2)| + |(y1-y2)|
			*/ 			
			
			int distance = Math.abs(xSource - xDest) + Math.abs(ySource - yDest);
			
			fitOfIndv += this.communications.get(i).getVolume() * distance;  
			
			}
		
		solution.setObjective(0, fitOfIndv);

	}
}

