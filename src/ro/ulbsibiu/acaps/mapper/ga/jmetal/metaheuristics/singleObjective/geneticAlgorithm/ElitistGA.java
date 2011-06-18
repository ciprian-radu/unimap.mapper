package ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.geneticAlgorithm;

import java.util.Comparator;

import jmetal.base.Operator;
import jmetal.base.Problem;
import jmetal.base.Solution;
import jmetal.base.SolutionSet;
import jmetal.base.operator.comparator.ObjectiveComparator;
import jmetal.util.JMException;
import ro.ulbsibiu.acaps.mapper.ga.GeneticAlgorithmMapper;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.TrackedAlgorithm;
import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.operator.crossover.NocPositionBasedCrossover;

/**
 * Elitist genetic algorithm. Actually this is the jMetal version of
 * {@link GeneticAlgorithmMapper}.
 * 
 * @author shaikat
 * @author cipi
 * 
 */
public class ElitistGA extends TrackedAlgorithm {
	private Problem problem_;

	public ElitistGA(Problem problem) {
		problem_ = problem;
	}

	/**
	 * Execute the elitist genetic algorithm algorithm
	 * 
	 * @throws JMException
	 */
	public SolutionSet execute() throws JMException, ClassNotFoundException {
		int maxEvaluations;
		int populationSize;
		int evaluations;

		SolutionSet population;
		SolutionSet offspringPopulation;

		Operator mutationOperator;
		Operator crossoverOperator;
		Operator selectionOperator;
		Comparator comparator;

		comparator = new ObjectiveComparator(0); // Single objective comparator

		// Read the parameter
		maxEvaluations = ((Integer) this.getInputParameter("maxEvaluations"))
				.intValue();
		populationSize = ((Integer) this.getInputParameter("populationSize"))
				.intValue();

		// Initialize the variables
		population = new SolutionSet(populationSize);
		offspringPopulation = new SolutionSet(2 * populationSize);
		evaluations = 0;

		// Read the operators
		mutationOperator = this.operators_.get("mutation");
		crossoverOperator = this.operators_.get("crossover");
		selectionOperator = this.operators_.get("selection");

		// Create the initial population
		Solution newIndividual;
		for (int i = 0; i < populationSize; i++) {
			newIndividual = new Solution(problem_);
			problem_.evaluate(newIndividual);
			evaluations++;
			population.add(newIndividual);
		} // for
		
		algorithmTracker.processIntermediateSolution("generations",
				Integer.toString(evaluations / populationSize),
				population.get(0));

		// main loop
		while (evaluations < maxEvaluations) {

			for (int i = 0; i < populationSize / 2; i++) {

				Solution[] parents = new Solution[2];

				// Selection
				parents[0] = (Solution) selectionOperator.execute(population);
				parents[1] = (Solution) selectionOperator.execute(population);

				// if crossover operator is NocpositionBasedCrossover the
				// crossover return one offspring in each call of the function
				if (NocPositionBasedCrossover.class
						.isAssignableFrom(crossoverOperator.getClass())) {

					// crossover
					Solution[] offsprings = (Solution[]) crossoverOperator
							.execute(parents);

					// Mutation
					mutationOperator.execute(offsprings[0]);
					
					problem_.evaluate(offsprings[0]);
					offspringPopulation.add(offsprings[0]);

					//selection of next set of parents
					parents[0] = (Solution) selectionOperator.execute(population);
					parents[1] = (Solution) selectionOperator.execute(population);
					// crossover
					offsprings = (Solution[]) crossoverOperator
							.execute(parents);

					// Mutation
					mutationOperator.execute(offsprings[0]);
					
					problem_.evaluate(offsprings[0]);
					offspringPopulation.add(offsprings[0]);

				} else {

					// crossover
					Solution[] offsprings = (Solution[]) crossoverOperator
							.execute(parents);

					// Mutation
					mutationOperator.execute(offsprings[0]);
					mutationOperator.execute(offsprings[1]);

					problem_.evaluate(offsprings[0]);
					problem_.evaluate(offsprings[1]);

					offspringPopulation.add(offsprings[0]);
					offspringPopulation.add(offsprings[1]);

				}

				evaluations += 2;

			}

			// copy the population to offspring population
			for (int i = 0; i < populationSize; i++) {
				offspringPopulation.add(population.get(i));
			} // for

			population.clear();

			// sort the offspring population according to the fitness
			offspringPopulation.sort(comparator);

			// take best populationsize number of individuals from the whole
			// population (population + offspring population)
			for (int i = 0; i < populationSize; i++)
				population.add(offspringPopulation.get(i));

			offspringPopulation.clear();
			
			algorithmTracker.processIntermediateSolution("generations",
					Integer.toString(evaluations / populationSize),
					population.get(0));
		}

		// Return a population with the best individual
		SolutionSet resultPopulation = new SolutionSet(1);
		resultPopulation.add(population.get(0));

		return resultPopulation;
	}
}
