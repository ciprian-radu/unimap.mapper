package ro.ulbsibiu.acaps.mapper.ga;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import jmetal.util.PseudoRandom;

import org.apache.log4j.Logger;

import ro.ulbsibiu.acaps.ctg.xml.apcg.ApcgType;
import ro.ulbsibiu.acaps.ctg.xml.apcg.TaskType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CommunicationType;
import ro.ulbsibiu.acaps.ctg.xml.ctg.CtgType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MapType;
import ro.ulbsibiu.acaps.ctg.xml.mapping.MappingType;
import ro.ulbsibiu.acaps.mapper.Mapper;
import ro.ulbsibiu.acaps.mapper.TooFewNocNodesException;
import ro.ulbsibiu.acaps.mapper.util.ApcgFilenameFilter;

/**
 * @author shaikat 
 * V2: speed up process
 * I do not want to run fitnesscalculation method so many time.
 * Its run only one time after creation of individual
 */
public class GeneticAlgorithmMapper implements Mapper {

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(GeneticAlgorithmMapper.class);

	private static final String MAPPER_ID = "ga";

	/** the number of nodes (nodes) from the NoC */
	private int noOfNodes;

	/**
	 * the number of processes (tasks). Note that each core has only one task
	 * associated to it.
	 */
	private static Long seed = new Long(1425367891);
	
	private int noOfIpCores;

	private int populationSize; 

	private int crossoverPr;

	private int mutationPr;

	private int noOfGenerationToRun;
	
	private int maxNoOfEvolutions;

	private static int percentageOfOldPopuToNewPopu = 20;

	private static int tournamentSize;

	private List<Individual> population;

	private List<Individual> newPopulation;

	private int[] currentChild1;
	private int[] currentChild2;

	private int currentNoOfGeneration;
	
	private int currentNoOfEvoltions;

	private List<CtgType> currentCtg;

	private List<ApcgType> currentApcg;

	/**
	 * the processes of mapping of IPcores and cores in integer number ( the
	 * cores in Noc are represented in integer number)
	 */
	private Core[] cores;

	/** all the communication is stored in the communication array */
	private ArrayList<Communication> communications;

	/** counts how many cores were parsed from the parsed APCGs */
	private int previousCoreCount = 0;

	public GeneticAlgorithmMapper(List<CtgType> ctg, List<ApcgType> apcg,
			int noOfIpCores, int noOfNodes, int populationSize, int maxNoOfEolutions, int crossoverPr, int mutationPr) {

		this.noOfIpCores = noOfIpCores;
		this.noOfNodes = noOfNodes;
		this.currentNoOfGeneration = 1;
		this.currentNoOfEvoltions = 0;
		this.currentCtg = ctg;
		this.currentApcg = apcg;
		this.populationSize = populationSize;
		this.maxNoOfEvolutions = maxNoOfEolutions;
		this.crossoverPr = crossoverPr;
		this.mutationPr = mutationPr;
		this.population = new ArrayList<Individual>(populationSize);
		this.newPopulation = new ArrayList<Individual>(populationSize);
		this.currentChild1 = new int[noOfNodes];
		this.currentChild2 = new int[noOfNodes];

		// tournament size
		tournamentSize = (int) Math.ceil( (populationSize * 10) / 100 );
		// initialize the cores in integer number
		initializeCores();
		getCommunicatios();

	}

	/**
	 * get all the communication by parsing ctgs and apcgs
	 */
	private void getCommunicatios() {

		this.communications = new ArrayList<Communication>();

		int communicationIndex = 0;
		for (int k = 0; k < currentCtg.size(); k++) {

			// communication is the list of all communication of the current ctg
			List<CommunicationType> communication = this.currentCtg.get(k)
					.getCommunication();

			/*
			 * taskAssingToIpcore is a list of task assigned to a IP core here
			 * it is considered a core is assign a task
			 */
			List<TaskType> taskAssignToIpcore;

			// no of communication in the current Ctg
			int noOfComm = this.currentCtg.get(k).getCommunication().size();

			// no of IP cores in current apcg
			int noOfIpCore = this.currentApcg.get(k).getCore().size();

			// source and destination of application task
			String sourceTask, destTask;

			// volume of the data
			double volume;

			// source and destination IP core, initialized by -1
			String sourceIpCore = "-1", destIpCore = "-1";

			for (int i = 0; i < noOfComm; i++) {

				sourceTask = communication.get(i).getSource().getId();
				destTask = communication.get(i).getDestination().getId();
				volume = communication.get(i).getVolume();

				// source task
				for (int j = 0; j < noOfIpCore; j++) {
					taskAssignToIpcore = this.currentApcg.get(k).getCore()
							.get(j).getTask();
					if (sourceTask.equals(taskAssignToIpcore.get(0).getId())) {
						sourceIpCore = this.currentApcg.get(k).getCore().get(j)
								.getUid();
						break;
					}
				}

				// destination task
				for (int j = 0; j < noOfIpCore; j++) {
					taskAssignToIpcore = this.currentApcg.get(k).getCore()
							.get(j).getTask();
					if (destTask.equals(taskAssignToIpcore.get(0).getId())) {
						destIpCore = this.currentApcg.get(k).getCore().get(j)
								.getUid();
						break;
					}
				}
				communications.add(new Communication(this.currentApcg.get(k)
						.getId(), sourceIpCore, destIpCore, volume));

			}
		}

	}

	/**
	 * initialize the cores in integer number
	 */
	private void initializeCores() {
		cores = new Core[noOfIpCores];
		for (int i = 0; i < cores.length; i++) {
			cores[i] = new Core(i, null, -1);
		}
	}

	/**
	 * this function is used for initialization of the population
	 */

	private void doInitPopulation() {

		Random rm;
		//if(seed == null)
			rm = new Random();
		//else 
			//rm = new Random(seed);

		/* initialized in this way so that no number will be repeated */
		for (int i = 0; i < populationSize; i++) {

			int[] tempIndividual = new int[noOfNodes];
			for (int k = 0; k < noOfNodes; k++)
				tempIndividual[k] = -1;

			int noOfNode = 0;
			while (noOfNode != noOfNodes) {
				//int aNum = rm.nextInt(noOfNodes);
				int aNum = PseudoRandom.randInt(0, noOfNodes - 1);
				int z = 0;
				boolean track = true;
				while (tempIndividual[z] != -1 && track) {

					if (aNum == tempIndividual[z])
						track = false;
					else
						z++;

				}
				if (track == true) {
					tempIndividual[z] = aNum;
					noOfNode++;
				}

			}
			// calculate the fitness here
			double fitnessOfIndividual = fitnessCalculation(tempIndividual);
			population.add(new Individual(tempIndividual, fitnessOfIndividual));
			currentNoOfEvoltions++;

		}
	}

	/**
	 * do Position based crossover between two parents generate two new children
	 * 
	 * @param pr1
	 *            position of the parent # 1 in the population
	 * @param pr2
	 *            position of the parent # 2 in the population
	 */
	private void doPositionBasedCrossOver(int pr1, int pr2) {

		// two parents that is used for crossover
		int parent1[] = new int[noOfNodes];
		int parent2[] = new int[noOfNodes];

		// two children that is created after crossover
		int child1[] = new int[noOfNodes];
		int child2[] = new int[noOfNodes];

		// copy parents from population
		parent1 = Arrays.copyOf(population.get(pr1).getGenes(),
				population.get(pr1).getGenes().length);
		parent2 = Arrays.copyOf(population.get(pr2).getGenes(),
				population.get(pr2).getGenes().length);

		Random rm;
		//if(seed == null)
			rm = new Random();
		//else 
			//rm = new Random(seed);

		//if (rm.nextInt(100) <= crossoverPr) {
		if (PseudoRandom.randInt(0, 100) <= crossoverPr) {
			for (int i = 0; i < child1.length; i++) {
				child1[i] = child2[i] = -1;
			}

			// 25 percent of the gene is used as positions
			int numberOfPositions = (int) ((this.noOfNodes / 100.0) * 25);

			int[] setOfPositions = new int[numberOfPositions];
			for (int i = 0; i < setOfPositions.length; i++)
				setOfPositions[i] = -1;

			for (int i = 0; i < setOfPositions.length; i++) {
				int number;
				// be sure that position is not repeated
				boolean track;
				do {
					track = false;
					//number = rm.nextInt(this.noOfNodes);
					number =  PseudoRandom.randInt(0, noOfNodes-1);
					for (int k = 0; k < setOfPositions.length; k++)
						if (number == setOfPositions[k]) {
							track = true;
							break;
						}

				} while (track);

				setOfPositions[i] = number;

			}

			int[] tempParent = new int[this.noOfNodes];

			tempParent = Arrays.copyOf(parent2, parent2.length);

			/*
			 * copy the content of the specific position(setOfPosition) from
			 * parent1 to child1. Also set -1 in position of parent2
			 * (tempParent) where the content of the parent1 of the specific
			 * position (setOfPosition) is matched
			 */
			for (int i = 0; i < setOfPositions.length; i++) {
				child1[setOfPositions[i]] = parent1[setOfPositions[i]];
				int j;
				for (j = 0; j < tempParent.length; j++) {
					if (tempParent[j] == parent1[setOfPositions[i]]) {
						tempParent[j] = -1;
						break;
					}
				}

			}
			/*
			 * starting from left to right of parent2(tempParent), copy content
			 * from parent2 to the rest of the position of child1 by skipping -1
			 */
			for (int i = 0; i < tempParent.length; i++) {

				if (tempParent[i] == -1)
					continue;
				else {
					for (int j = 0; j < child1.length; j++) {
						if (child1[j] != -1)
							continue;
						else {
							child1[j] = tempParent[i];
							break;
						}
					}
				}

			}
			// child1 finished

			// child2 start

			for (int i = 0; i < setOfPositions.length; i++)
				setOfPositions[i] = -1;

			for (int i = 0; i < setOfPositions.length; i++) {
				int number;
				// be sure that position is not repeated
				boolean track;
				do {
					track = false;
					//number = rm.nextInt(this.noOfNodes);
					number =  PseudoRandom.randInt(0, noOfNodes-1);
					for (int k = 0; k < setOfPositions.length; k++)
						if (number == setOfPositions[k]) {
							track = true;
							break;
						}

				} while (track);

				setOfPositions[i] = number;

			}

			tempParent = Arrays.copyOf(parent1, parent1.length);

			for (int i = 0; i < setOfPositions.length; i++) {
				child2[setOfPositions[i]] = parent2[setOfPositions[i]];
				int j;
				for (j = 0; j < tempParent.length; j++) {
					if (tempParent[j] == parent2[setOfPositions[i]]) {
						tempParent[j] = -1;
						break;
					}
				}

			}

			for (int i = 0; i < tempParent.length; i++) {

				if (tempParent[i] == -1)
					continue;
				else {
					for (int j = 0; j < child2.length; j++) {
						if (child2[j] != -1)
							continue;
						else {
							child2[j] = tempParent[i];
							break;
						}
					}
				}
			}

			currentChild1 = Arrays.copyOf(child1, child1.length);
			currentChild2 = Arrays.copyOf(child2, child2.length);

		}

		// the random number is greater that crossoverPr
		else {
			currentChild1 = Arrays.copyOf(parent1, parent1.length);
			currentChild2 = Arrays.copyOf(parent2, parent2.length);

		}

	}

	/**
	 * do Cut and crossfill crossover between two parent
	 * 
	 * @param pr1
	 *            position of the parent # 1 in the population
	 * @param pr2
	 *            position of the parent # 2 in the population
	 */

	private void doCutandCrossfillCrossoverV1(int pr1, int pr2) {

		Random rm;
		if(seed == null)
			rm = new Random();
		else 
			rm = new Random(seed);

		// two parents that is used for crossover
		int parent1[] = new int[noOfNodes];
		int parent2[] = new int[noOfNodes];

		// two children that is created after crossover
		int child1[] = new int[noOfNodes];
		int child2[] = new int[noOfNodes];

		// copy parents from population
		parent1 = Arrays.copyOf(population.get(pr1).getGenes(),
				population.get(pr1).getGenes().length);
		parent2 = Arrays.copyOf(population.get(pr2).getGenes(),
				population.get(pr2).getGenes().length);

		if (rm.nextInt(100) <= crossoverPr) {

			int cr_point;
			// find a crossover point within 1 to noOfNodes-1
			do {
				cr_point = rm.nextInt(noOfNodes - 1);
			} while (cr_point == 0);

			// copy first part from parent
			for (int i = 0; i < cr_point; i++) {
				child1[i] = parent1[i];
				child2[i] = parent2[i];
			}

			// copy next part in the same order skipping values already in the
			// child
			int in_ch1, in_ch2;
			in_ch1 = in_ch2 = cr_point;
			boolean track_ch1, track_ch2;
			for (int i = cr_point; i < noOfNodes; i++) {
				// for parent1
				track_ch2 = true;
				int temp = parent1[i];
				for (int j = 0; j < cr_point; j++) {
					if (temp == child2[j]) {
						track_ch2 = false;
						break;

					}
				}
				if (track_ch2) {
					child2[in_ch2++] = temp;
				}

				// for parent2
				track_ch1 = true;
				int temp2 = parent2[i];
				for (int j = 0; j < cr_point; j++) {
					if (temp2 == child1[j]) {
						track_ch1 = false;
						break;
					}
				}
				if (track_ch1) {
					child1[in_ch1++] = temp2;

				}
			}

			// copy last part from parent to child
			// for child 1 -> copy from parent2
			boolean track = true;
			for (int i = in_ch1; i < noOfNodes; i++) {
				for (int j = 0; j < cr_point; j++) {
					track = true;
					int temp = parent2[j];
					for (int z = 0; z < i; z++) {
						if (temp == child1[z]) {
							track = false;
							break;
						}
					}
					if (track) {
						child1[i] = temp;
						break;
					}
				}
			}

			// for child2-> copy from parent 1
			track = true;
			for (int i = in_ch2; i < noOfNodes; i++) {
				for (int j = 0; j < cr_point; j++) {
					track = true;
					int temp = parent1[j];
					for (int z = 0; z < i; z++) {
						if (temp == child2[z]) {
							track = false;
							break;
						}
					}
					if (track) {
						child2[i] = temp;
						break;
					}
				}
			}
			currentChild1 = child1;
			currentChild2 = child2;
		}
		// the random number is greater that crossoverPr
		else {
			currentChild1 = parent1;
			currentChild2 = parent2;

		}

	}

	private void doCutAndCrossfillCrossoverV2(int pr1, int pr2) {

		Random rm;
		if(seed == null)
			rm = new Random();
		else 
			rm = new Random(seed);

		// two parents that is used for crossover
		int parent1[] = new int[noOfNodes];
		int parent2[] = new int[noOfNodes];

		// two children that is created after crossover
		int child1[] = new int[noOfNodes];
		int child2[] = new int[noOfNodes];

		// copy parents from population
		parent1 = Arrays.copyOf(population.get(pr1).getGenes(),
				population.get(pr1).getGenes().length);
		parent2 = Arrays.copyOf(population.get(pr2).getGenes(),
				population.get(pr2).getGenes().length);

		if (rm.nextInt(100) <= crossoverPr) {

			int crPoint;
			// find a crossover point within 1 to noOfNodes-1
			do {
				crPoint = rm.nextInt(noOfNodes - 1);
			} while (crPoint == 0);

			// copy first part from parent
			for (int i = 0; i < crPoint; i++) {
				child1[i] = parent1[i];
				child2[i] = parent2[i];
			}

			// copy next part in the same order skipping values already in the
			// child
			int indexChild1 = crPoint;
			boolean trackChild1;
			for (int i = 0; i < noOfNodes; i++) {
				// for parent1
				trackChild1 = true;
				int temp = parent2[i];
				for (int j = 0; j < crPoint; j++) {
					if (temp == child1[j]) {
						trackChild1 = false;
						break;

					}
				}
				if (trackChild1) {
					child1[indexChild1++] = temp;
				}
			}

			// for child2
			int indexChild2 = crPoint;
			boolean trackChild2;
			for (int i = 0; i < noOfNodes; i++) {
				// for parent1
				trackChild2 = true;
				int temp = parent1[i];
				for (int j = 0; j < crPoint; j++) {
					if (temp == child2[j]) {
						trackChild2 = false;
						break;

					}
				}
				if (trackChild2) {
					child2[indexChild2++] = temp;
				}
			}
			currentChild1 = child1;
			currentChild2 = child2;

		} else {
			currentChild1 = parent1;
			currentChild2 = parent2;
		}
	}

	/**
	 * this function do mutation of the two current children -> currentChild1
	 * and currentChild2. Here we use only swapping two randomly selected number
	 * in the array
	 **/
	private void doSwapMutation() {

		Random rm;
		//if(seed == null)
			rm = new Random();
		//else 
			//rm = new Random(seed);

		int pos1Forchild1, pos2Forchild1, pos1Forchild2, pos2Forchild2, temp;

		pos1Forchild1 = rm.nextInt(noOfNodes);
		pos2Forchild1 = rm.nextInt(noOfNodes);

		pos1Forchild2 = rm.nextInt(noOfNodes);
		pos2Forchild2 = rm.nextInt(noOfNodes);

		if (rm.nextInt(100) <= mutationPr) {

			temp = currentChild1[pos1Forchild1];
			currentChild1[pos1Forchild1] = currentChild1[pos2Forchild1];
			currentChild1[pos2Forchild1] = temp;
		}
		if (rm.nextInt(100) <= mutationPr) {

			temp = currentChild2[pos1Forchild2];
			currentChild2[pos1Forchild2] = currentChild2[pos2Forchild2];
			currentChild2[pos2Forchild2] = temp;
		}
	}

	/*
	 * insert mutation
	 */
	private void doInsertMutation(){
		
		Random rm;
		if(seed == null)
			rm = new Random();
		else 
			rm = new Random(seed);

		int pos1Forchild1, pos2Forchild1, pos1Forchild2, pos2Forchild2, temp1, temp2;

		pos1Forchild1 = rm.nextInt(noOfNodes);
		pos2Forchild1 = rm.nextInt(noOfNodes);

		pos1Forchild2 = rm.nextInt(noOfNodes);
		pos2Forchild2 = rm.nextInt(noOfNodes);
		
		if (rm.nextInt(100) <= mutationPr) {
			boolean isEqualTrack = false;
			if( pos1Forchild1 > pos2Forchild1) {
				int tempPosition = pos1Forchild1;
				pos1Forchild1 = pos2Forchild1;
				pos2Forchild1 = tempPosition;
			
			}
			else if (pos1Forchild1 == pos2Forchild1)
				isEqualTrack = true;
			
			if(isEqualTrack == false) {	
				temp1 = currentChild1[pos1Forchild1 + 1];
				currentChild1[pos1Forchild1 + 1] = currentChild1 [pos2Forchild1];
				for(int i = pos1Forchild1 + 2; i<= pos2Forchild1; i++) {
					temp2 = currentChild1[i];
					currentChild1 [i] = temp1;
					temp1 = temp2;
				}
				
			}
		
		}
		

		
		//for child 2 
		temp1 = temp2 = -1;
		if (rm.nextInt(100) <= mutationPr) {
			boolean isEqualTrack = false;
			if( pos1Forchild2 > pos2Forchild2) {
				int tempPosition = pos1Forchild2;
				pos1Forchild2 = pos2Forchild2;
				pos2Forchild2 = tempPosition;
			
			}
			else if (pos1Forchild2 == pos2Forchild2)
				isEqualTrack = true;
				
			if(isEqualTrack == false) {	
				temp1 = currentChild2[pos1Forchild2 + 1];
				currentChild2[pos1Forchild2 + 1] = currentChild2 [pos2Forchild2];
				for(int i = pos1Forchild2 + 2; i<= pos2Forchild2; i++) {
					temp2 = currentChild2[i];
					currentChild2 [i] = temp1;
					temp1 = temp2;
				}
				
			}
		
		}
	}
	
	
	private void doMutation(int[] individual) {
		if (logger.isDebugEnabled()) {
			logger.debug("Applying swapping based mutation for individual " + Arrays.toString(individual));
		}
		
		Random rand;
		//if (seed == null) {
			rand = new Random();
		//} else {
		//	rand = new Random(seed);
		//}

		//rand = new Random();
		boolean mutationOccured = false;
		for (int i = 0; i < individual.length; i++) {
			//int position = rand.nextInt(individual.length);
			int position = PseudoRandom.randInt(0, individual.length - 1);
			//if (rand.nextInt(100) <= mutationPr) {
			if (PseudoRandom.randInt(0, 100) <= mutationPr) {
				int temp = individual[i];
				individual[i] = individual[position];
				individual[position] = temp;
				if (i != position) {
					mutationOccured = true;
					if (logger.isTraceEnabled()) {
						logger.trace("Individual mutated gene number " + i + ", by swapping it with gene number " + position);
					}
				}
			}
		}
		
		if (mutationOccured) {
			if (logger.isDebugEnabled()) {
				logger.debug("Individual mutated to " + Arrays.toString(individual));
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("Individual did not suffer any mutation");
			}
		}

	}
	
	/**
	 * this method is used to swap two cores but within two one must be used in
	 * current apcg
	 **/
	/*
	 * private void doMutationv2() {
	 * 
	 * Random rm = new Random(); int pos1, pos2, temp;
	 * 
	 * if(Math.abs(rm.nextInt()) % 100 <= mutationPr){
	 * 
	 * boolean track = true; do { pos1 = Math.abs(rm.nextInt()) % noOfNodes;
	 * //we try to find a position where we have a core that is in apcg for(int
	 * i = 0; i < this.currentApcg.getCore().size(); i++)
	 * if(this.currentApcg.getCore
	 * ().get(i).getUid().equals(""+currentChild1[pos1])) { track = false;
	 * break; } }while(track); pos2 = Math.abs(rm.nextInt()) % noOfNodes; temp =
	 * currentChild1[pos1]; currentChild1[pos1] = currentChild1[pos2];
	 * currentChild1[pos2] = temp;
	 * 
	 * }
	 * 
	 * if(Math.abs(rm.nextInt()) % 100 <= mutationPr) { boolean track = true; do
	 * { pos1 = Math.abs(rm.nextInt()) % noOfNodes; //we try to find a position
	 * where we have a core that is in apcg for(int i = 0; i <
	 * this.currentApcg.getCore().size(); i++)
	 * if(this.currentApcg.getCore().get(
	 * i).getUid().equals(""+currentChild2[pos1])) { track = false; break; }
	 * 
	 * }while(track); pos2 = Math.abs(rm.nextInt()) % noOfNodes; temp =
	 * currentChild2[pos1]; currentChild2[pos1] = currentChild2[pos2];
	 * currentChild2[pos2]=temp;
	 * 
	 * }
	 * 
	 * 
	 * 
	 * }
	 */

	/**
	 * @param indv
	 *            the individual that need to calculate fitness
	 * 
	 * @return the fitness of the individual
	 */

	private double fitnessCalculation(int[] indv) {

		// raw fitness of the individual
		double fitOfIndv = 0.0;

		/*
		 * now I want to find where source and destination IP core is placed in
		 * NOC node
		 */

		/*
		 * posOfSourceIpcoreInNocNode -> position of the source Ip core in the
		 * Noc node posOfDestIpcoreInNocNode -> position of the destination Ip
		 * core in the Noc node
		 * 
		 * posOfSourceIPCoreinCores -> position of the source IP core in cores
		 * array posOfDestIPCoreinCores -> position of the destination IP core
		 * in cores array
		 */

		for (int i = 0; i < communications.size(); i++) {

			int posOfSourceIpCoreInNocNode = -1, posOfDestIpCoreInNocNode = -1, posOfSourceIPCoreInCores = -1, posOfDestIPCoreInCores = -1;

			/*
			 * first i want to find which core (in integer number) source and
			 * destination IPcore is
			 */

			for (int j = 0; j < cores.length; j++) {
				if (this.communications.get(i).getApcgId()
						.equals(cores[j].getApcgId())
						&& this.communications.get(i).getSourceUid()
								.equals(cores[j].getCoreUid())) {
					posOfSourceIPCoreInCores = j;
					break;
				}
			}

			for (int j = 0; j < cores.length; j++) {
				if (this.communications.get(i).getApcgId()
						.equals(cores[j].getApcgId())
						&& this.communications.get(i).getdestUid()
								.equals(cores[j].getCoreUid())) {
					posOfDestIPCoreInCores = j;
					break;
				}
			}

			for (int j = 0; j < noOfNodes; j++) {
				if (posOfSourceIPCoreInCores == indv[j]) {
					posOfSourceIpCoreInNocNode = j;
					break;
				}
			}

			for (int j = 0; j < noOfNodes; j++) {
				if (posOfDestIPCoreInCores == indv[j]) {
					posOfDestIpCoreInNocNode = j;
					break;
				}
			}

			/*
			 * get the position (x, y) of the source and destination IP core in
			 * the Noc Matrix (4x4)
			 */
			/*
			 * (xSource, ySource)-> position of source IP core in MxM matrix
			 * (xDest, yDest)-> position of destination IP core in MxM matrix
			 */

			int xSource, ySource, xDest, yDest, M;
			M = (int) Math.sqrt(noOfNodes);

			xSource = posOfSourceIpCoreInNocNode / M;
			ySource = posOfSourceIpCoreInNocNode % M;

			xDest = posOfDestIpCoreInNocNode / M;
			yDest = posOfDestIpCoreInNocNode % M;

			/*
			 * Now find out the manhattan distance between source and
			 * destination node Formula: |(X1-x2)| + |(y1-y2)|
			 */

			int distance = Math.abs(xSource - xDest)
					+ Math.abs(ySource - yDest);

			fitOfIndv += this.communications.get(i).getVolume() * distance;

		}
		return (1 / fitOfIndv);
	}

	/**
	 * used for inserting two current children (currentChild1, currentChild2)
	 * into mating pool (newPopulation array).
	 * 
	 * This function is called each time after crossover and mutation have done.
	 **/

	private void insertCurrentChildrenInTonewPopulation() {

		newPopulation.add(new Individual(currentChild1,
				fitnessCalculation(currentChild1)));
		newPopulation.add(new Individual(currentChild2,
				fitnessCalculation(currentChild2)));

	}

	/**
	 * This function find a parent by using Tournament selection algorithm
	 * 
	 * @param tournamentSize
	 *            Specify the size of the tournament
	 * 
	 * @return position of the parent in the array
	 */

	private int tournamentSelection(int tournamentSize) {

		Random rm;
		//if(seed == null)
			//rm = new Random();
		//else 
			rm = new Random(seed);
		// save the position of randomly selected potential parents
		int posParent[] = new int[tournamentSize];

		for (int i = 0; i < posParent.length; i++)
			posParent[i] = -1;

		for (int i = 0; i < tournamentSize; i++) {
			boolean track;
			int position;
			// be sure that position is not repeated
			do {
				track = false;
				//position = rm.nextInt(populationSize);
				position = PseudoRandom.randInt(0, populationSize - 1);
				for (int k = 0; k < posParent.length; k++)
					if (position == posParent[k]) {
						track = true;
						break;
					}

			} while (track);
			posParent[i] = position;
		}

		double f = Double.MIN_VALUE, fitness;
		int posOfParent = -1;

		for (int i = 0; i < tournamentSize; i++) {
			fitness = population.get(i).getFitness();
			if (fitness > f) {
				f = fitness;
				posOfParent = posParent[i];
			}
		}
		return posOfParent;

	}

	private void createPopulationElitismV2() {

		/*
		 * inner class to make easier for sorting
		 */
		class FitnessofIndv {
			double fitness;
			int index;
			boolean isParent;

			public FitnessofIndv(double fitness, int index, boolean isParent) {
				this.fitness = fitness;
				this.index = index;
				this.isParent = isParent;
			}
		}

		/*
		 * fitnessArray: save all the fitness of parent (population) and child
		 * (new population)
		 */
		ArrayList<FitnessofIndv> fitnessArray = new ArrayList<FitnessofIndv>();
		for (int i = 0; i < populationSize; i++) {
			fitnessArray.add(new FitnessofIndv(population.get(i).getFitness(),
					i, true));
			fitnessArray.add(new FitnessofIndv(newPopulation.get(i)
					.getFitness(), i, false));

		}

		/*
		 * sort the whole population according to the fitness value (descending
		 * order)
		 */
		Collections.sort(fitnessArray, new Comparator<FitnessofIndv>() {
			public int compare(FitnessofIndv o1, FitnessofIndv o2) {
				if (o1.fitness < o2.fitness)
					return 1;
				else if (o1.fitness == o2.fitness)
					return 0;
				else
					return -1;
			}
		});

		// copy population to old population
		List<Individual> oldPopulation = new ArrayList<Individual>(noOfNodes);

		for (int i = 0; i < populationSize; i++) {
			oldPopulation.add(population.get(i));

		}

		population.clear();

		/*
		 * copy back to population: best populationSize individuals
		 */
		for (int i = 0; i < populationSize; i++) {
			if (fitnessArray.get(i).isParent == true) {
				population.add(oldPopulation.get(fitnessArray.get(i).index));
			} else {
				population.add(newPopulation.get(fitnessArray.get(i).index));
			}
		}

	}

	public String getMapperId() {
		return MAPPER_ID;
	}

	private void runGaSteps() {

		// position of parent1 and parent2 in the population
		int posOfParent1, posOfParent2;
		int i;
		
		doInitPopulation();
/*
		while (currentNoOfGeneration < noOfGenerationToRun) {

			// clear new population
			newPopulation.clear();

			for (i = 0; i < populationSize / 2; i++) {
				posOfParent1 = tournamentSelection(tournamentSize);
				posOfParent2 = tournamentSelection(tournamentSize);

				doPositionBasedCrossOver(posOfParent1, posOfParent2);
				doSwapMutation();
				//doMutationv2();
				//doInsertMutation();
				insertCurrentChildrenInTonewPopulation();

			}

			//if population size id odd
			if(i * 2 < populationSize){
				posOfParent1 = tournamentSelection(tournamentSize);
				posOfParent2 = tournamentSelection(tournamentSize);

				doPositionBasedCrossOver(posOfParent1, posOfParent2);
				doSwapMutation();
				newPopulation.add(new Individual(currentChild1,
						fitnessCalculation(currentChild1)));

			}
				
			createPopulationElitismV2();

			if (currentNoOfGeneration % 100 == 0)
				logger.info(currentNoOfGeneration);
			currentNoOfGeneration++;

		}
*/
		while (currentNoOfEvoltions < maxNoOfEvolutions) {

			// clear new population
			newPopulation.clear();

			for (i = 0; i < populationSize / 2; i++) {
				posOfParent1 = tournamentSelection(tournamentSize);
				posOfParent2 = tournamentSelection(tournamentSize);

				doPositionBasedCrossOver(posOfParent1, posOfParent2);
				//currentChild1 =  Arrays.copyOf(population.get(posOfParent1).getGenes(), population.get(posOfParent1).getGenes().length);
				//currentChild2 =  Arrays.copyOf(population.get(posOfParent2).getGenes(), population.get(posOfParent2).getGenes().length);
				//mutationPr =(int) (((double) (maxNoOfEvolutions - currentNoOfEvoltions)/maxNoOfEvolutions) * 100) ;
				doMutation(currentChild1);
				//mutationPr =(int) (((double) (maxNoOfEvolutions - currentNoOfEvoltions)/maxNoOfEvolutions) * 100) ;
				doMutation(currentChild2);
				
				//doSwapMutation();
				//adaptive mutation probability 
				
				//doInsertMutation();
				currentNoOfEvoltions+=2;
				insertCurrentChildrenInTonewPopulation();

			}

			//if population size id odd
			if(i * 2 < populationSize){
				posOfParent1 = tournamentSelection(tournamentSize);
				posOfParent2 = tournamentSelection(tournamentSize);

				//doPositionBasedCrossOver(posOfParent1, posOfParent2);
				mutationPr = ((maxNoOfEvolutions - currentNoOfEvoltions)/maxNoOfEvolutions)*100;
				
				doSwapMutation();
				currentNoOfEvoltions++;
				newPopulation.add(new Individual(currentChild1,
						fitnessCalculation(currentChild1)));

			}
				
			createPopulationElitismV2();
			if (currentNoOfEvoltions > 0 && currentNoOfEvoltions % 100 == 0) {
				logger.info("Finished " + currentNoOfEvoltions + " evoluations");
			}
		}
		logger.info("Total number of evolutions: "+ currentNoOfEvoltions);

		
	}

	public String[] map() throws TooFewNocNodesException {

		if (noOfNodes < noOfIpCores) {
			throw new TooFewNocNodesException(noOfIpCores, noOfNodes);
		}

		logger.info("Start Mapping...");

		long start = System.currentTimeMillis();
		runGaSteps();

		long end = System.currentTimeMillis();
		logger.info("Mapping process finished successfully.");
		logger.info("Time: " + (end - start) / 1000 + " seconds");

		MappingType mapping = new MappingType();
		mapping.setId(MAPPER_ID);
		mapping.setRuntime(new Double(end - start));

		for (int i = 0; i < cores.length; i++) {
			int j;
			// get the core number in integer and find it within the individual
			int currentIpCore = cores[i].getCoreNo();
			for (j = 0; j < this.noOfNodes; j++) {
				if (currentIpCore == population.get(0).getGenes()[j]) {
					break;
				}
			}
			MapType map = new MapType();
			map.setNode("" + j);
			map.setCore(cores[i].getCoreUid());
			map.setApcg(cores[i].getApcgId());
			mapping.getMap().add(map);
		}

		StringWriter []stringWriter = new StringWriter[1];
		stringWriter[0]= new StringWriter();
		
		ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory mappingFactory = new ro.ulbsibiu.acaps.ctg.xml.mapping.ObjectFactory();
		JAXBElement<MappingType> jaxbElement = mappingFactory
				.createMapping(mapping);
		try {
			JAXBContext jaxbContext = JAXBContext
					.newInstance(MappingType.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
			marshaller.marshal(jaxbElement, stringWriter[0]);
		} catch (JAXBException e) {
			logger.error("JAXB encountered an error", e);
		}

		String[] returnString = new String [1];
		returnString[0]=stringWriter[0].toString();
		
		return returnString;

	}

	/**
	 * assign apcgId and Uid to the cores
	 * 
	 * @param apcg
	 * 
	 */
	private void parseApcg(ApcgType apcg) {
		int i;
		for (i = 0; i < apcg.getCore().size(); i++) {
			cores[previousCoreCount + i].setApcgId(apcg.getId());
			cores[previousCoreCount + i].setCoreUid(apcg.getCore().get(i)
					.getUid());
		}
		previousCoreCount += i;
	}

	/**
	 * print the current mapping
	 */

	private void printCurrentMapping() {

		for (int i = 0; i < cores.length; i++) {
			int j;
			int currentIpCore = cores[i].getCoreNo();
			for (j = 0; j < this.noOfNodes; j++) {
				if (currentIpCore == population.get(i).getGenes()[j]) {
					break;
				}
			}

			System.out.println("core " + cores[i].getCoreUid() + " (APCG "
					+ cores[i].getApcgId() + ") is mapped to Noc Node " + j);
		}
		System.out.println("Total Communication cost " + 1/population.get(0).getFitness());

		System.out.println();
	}

	public static void main(String args[]) throws TooFewNocNodesException,
			IOException, JAXBException, GeneticAlgorithmInputException {

		int argPopulationSize=0, argNoOfGenerationToRun=0, argMaxNoOfEvolutions=0, argCrossoverPr=100, argMutationPr=100;
		
		File[] tgffFiles = null;
		String specifiedCtgId = null;
		String specifiedApcgId = null;
		boolean is1stParameterString = false;

		final int defaultPopulationSize = 1000;
		final int defaultNoOfGeneration = 5000;
		final int defaultNoOfEvolutions = 1000000;
		final double defaultCrossoverPr = 0.85;
		final double defaultMutationPr = 0.05;

		if (args.length == 0) {
			
			argPopulationSize = defaultPopulationSize;
			//argNoOfGenerationToRun = defaultNoOfGeneration;
			argMaxNoOfEvolutions = defaultNoOfEvolutions;
			argCrossoverPr = (int) (defaultCrossoverPr * 100);
			argMutationPr = (int) (defaultMutationPr * 100);
		} else {
			try {
				argPopulationSize = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				is1stParameterString = true;

			}
			if (is1stParameterString == true) {
				argPopulationSize = defaultPopulationSize;
				//noOfGenerationToRun = defaultNoOfGeneration;
				argMaxNoOfEvolutions = defaultNoOfEvolutions;
				argCrossoverPr = (int) (defaultCrossoverPr * 100);
				argMutationPr = (int) (defaultMutationPr * 100);
			} else {
				try {
					//noOfGenerationToRun = Integer.parseInt(args[1]);
					argMaxNoOfEvolutions = Integer.parseInt(args[1]);
					if (Double.parseDouble(args[2]) > 1.0)
						throw new GeneticAlgorithmInputException(
								"Crossover Probability must be less than 1.0");
					argCrossoverPr = (int) (Double.parseDouble(args[2]) * 100);
					if (Double.parseDouble(args[3]) > 1.0)
						throw new GeneticAlgorithmInputException(
								"Mutation Probability must be less than 1.0");
					argMutationPr = (int) (Double.parseDouble(args[3]) * 100);
				} catch (NumberFormatException e) {
					throw new GeneticAlgorithmInputException(
							"Please provide appropriate parameters");
				}
			}
		}

		// here 1 percent of the total population size is used as
		

		if (args.length == 0
				|| (args.length == 4 && is1stParameterString == false)) {
			File e3sDir = new File(".." + File.separator + "CTG-XML"
					+ File.separator + "xml" + File.separator + "e3s");
			logger.assertLog(e3sDir.isDirectory(),
					"Could not find the E3S benchmarks directory!");
			tgffFiles = e3sDir.listFiles(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".tgff");
				}
			});
		} else {
			List<File> tgffFileList = new ArrayList<File>(args.length);
			int i;

			if (is1stParameterString == true)
				i = 0;
			else
				i = 4;

			for (; i < args.length; i++) {
				if (args[i].startsWith("--ctg") || args[i].startsWith("--apcg")) {
					break;
				}
				tgffFileList.add(new File(args[i]));
			}
			tgffFiles = tgffFileList.toArray(new File[tgffFileList.size()]);
			if (i < args.length) {
				if (args[i].startsWith("--ctg")) {

					logger.assertLog(args.length > i + 1,
							"Expecting CTG ID after --ctg option");
					specifiedCtgId = args[i + 1];
				} else {
					if (args[i].startsWith("--apcg")) {
						logger.assertLog(args.length > i + 1,
								"Expecting APCG ID after --ctg option");
						specifiedApcgId = args[i + 1];
					}
				}

				i = i + 2;

				if (args.length > i + 1) {
					if (args[i].startsWith("--ctg")) {
						logger.assertLog(args.length > i + 1,
								"Expecting CTG ID after --ctg option");
						specifiedCtgId = args[i + 1];
					} else {
						if (args[i].startsWith("--apcg")) {
							logger.assertLog(args.length > i + 1,
									"Expecting APCG ID after --ctg option");
							specifiedApcgId = args[i + 1];
						}
					}
				}

				if (specifiedCtgId != null) {
					logger.info("Mapping only CTGs with ID " + specifiedCtgId);
				}
				if (specifiedApcgId != null) {
					logger.info("Mapping only APCGs with ID " + specifiedApcgId);
				}
			}
		}

		// input processing finished

		for (int i = 0; i < tgffFiles.length; i++) {
			String path = ".." + File.separator + "CTG-XML" + File.separator
					+ "xml" + File.separator + "e3s" + File.separator
					+ tgffFiles[i].getName() + File.separator;

			File e3sBenchmark = new File(path);
			String[] ctgs = null;
			if (specifiedCtgId != null) {
				ctgs = new String[] { "ctg-" + specifiedCtgId };
			} else {
				ctgs = e3sBenchmark.list(new FilenameFilter() {

					@Override
					public boolean accept(File dir, String name) {
						return dir.isDirectory() && name.startsWith("ctg-");
					}

				});
			}
			logger.assertLog(ctgs.length > 0, "No CTGs to work with!");

			for (int j = 0; j < ctgs.length; j++) {
				String ctgId = ctgs[j].substring("ctg-".length());
				List<CtgType> ctgTypes = new ArrayList<CtgType>();
				// if the ctg ID contains + => we need to map multiple CTGs
				// in one mapping XML
				String[] ctgIds = ctgId.split("\\+");

				List<File> apcgsList = new ArrayList<File>();

				for (int k = 0; k < ctgIds.length; k++) {
					JAXBContext jaxbContext = JAXBContext
							.newInstance("ro.ulbsibiu.acaps.ctg.xml.ctg");
					Unmarshaller unmarshaller = jaxbContext
							.createUnmarshaller();
					@SuppressWarnings("unchecked")
					CtgType ctgType = ((JAXBElement<CtgType>) unmarshaller
							.unmarshal(new File(path + "ctg-" + ctgIds[k]
									+ File.separator + "ctg-" + ctgIds[k]
									+ ".xml"))).getValue();
					ctgTypes.add(ctgType);

					String[] apcgs = null;
					String pathOfApcg = path + "ctg-" + ctgIds[k]
							+ File.separator;

					File apcgPath = new File(pathOfApcg);

					apcgsList.addAll(Arrays.asList(apcgPath
							.listFiles(new ApcgFilenameFilter(ctgIds[k],
									specifiedApcgId))));
				}
				File[] apcgFiles = apcgsList
						.toArray(new File[apcgsList.size()]);
				for (int l = 0; l < apcgFiles.length / ctgIds.length; l++) {
					String apcgId = ctgId + "_";
					if (specifiedApcgId == null) {
						apcgId += l;
					} else {
						apcgId += specifiedApcgId;
					}
					List<ApcgType> apcgTypes = new ArrayList<ApcgType>();
					for (int k = 0; k < apcgFiles.length; k++) {
						String id;
						if (specifiedApcgId == null) {
							id = Integer.toString(l);
						} else {
							id = specifiedApcgId;
						}
						if (apcgFiles[k].getName().endsWith(id + ".xml")) {
							JAXBContext jaxbContext = JAXBContext
									.newInstance("ro.ulbsibiu.acaps.ctg.xml.apcg");
							Unmarshaller unmarshaller = jaxbContext
									.createUnmarshaller();
							@SuppressWarnings("unchecked")
							ApcgType apcg = ((JAXBElement<ApcgType>) unmarshaller
									.unmarshal(new File(apcgFiles[k]
											.getAbsolutePath()))).getValue();
							apcgTypes.add(apcg);
						}
					}
					logger.assertLog(apcgTypes.size() == ctgTypes.size(),
							"An equal number of CTGs and APCGs is expected!");

					logger.info("Using a Genetic Algorithm mapper for "
							+ path + "ctg-" + ctgId + " (APCG " + apcgId + ")");

					// number of IP cores in the apcg
					int noOfIpCores = 0;
					for (int k = 0; k < apcgTypes.size(); k++) {
						noOfIpCores += apcgTypes.get(k).getCore().size();
					}

					int hSize = (int) Math.ceil(Math.sqrt(noOfIpCores));
					hSize = Math.max(4, hSize); // using at least a 4x4 2D mesh
					String meshSize = hSize + "x" + hSize;
					logger.info("The algorithm has "
							+ noOfIpCores
							+ " cores to map => working with a 2D mesh of size "
							+ meshSize);

					// working with a 2D mesh topology
					String topologyDir = ".." + File.separator + "NoC-XML"
							+ File.separator + "src" + File.separator + "ro"
							+ File.separator + "ulbsibiu" + File.separator
							+ "acaps" + File.separator + "noc" + File.separator
							+ "topology" + File.separator + "mesh2D"
							+ File.separator + meshSize;

					File nodesDir = new File(topologyDir, "nodes");
					logger.assertLog(nodesDir.isDirectory(), nodesDir.getName()
							+ " is not a directory!");
					File[] nodeXmls = nodesDir.listFiles(new FileFilter() {

						@Override
						public boolean accept(File pathname) {
							return pathname.getName().endsWith(".xml");
						}
					});

					logger.debug("Found " + nodeXmls.length + " nodes");

					GeneticAlgorithmMapper gaMapper = new GeneticAlgorithmMapper(
							ctgTypes, apcgTypes, noOfIpCores, nodeXmls.length, argPopulationSize, argMaxNoOfEvolutions,
							argCrossoverPr, argMutationPr);

					for (int k = 0; k < apcgTypes.size(); k++) {
						gaMapper.parseApcg(apcgTypes.get(k));
					}

					String[] mappingXml = gaMapper.map();

					File dir = new File(path + "ctg-" + ctgId);
					dir.mkdirs();

					String mappingXmlFilePath = path + "ctg-" + ctgId
							+ File.separator + "mapping-" + apcgId + "_"
							+ gaMapper.getMapperId() + ".xml";
					PrintWriter pw = new PrintWriter(mappingXmlFilePath);
					logger.info("Saving the mapping XML file"
							+ mappingXmlFilePath);
					logger.info("Saving the mapping XML file");
					pw.write(mappingXml[0]);
					pw.close();

					gaMapper.printCurrentMapping();
				}
			}

		}

		logger.info("Program End");

	}
}
