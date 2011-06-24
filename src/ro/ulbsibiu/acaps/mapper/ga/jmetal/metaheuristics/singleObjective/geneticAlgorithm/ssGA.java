/**
 * ssGA.java
 * @author Antonio J. Nebro
 * @author cradu the jMetal ssGA algorithm is now a {@link TrackedAlgorithm}
 * @version 1.1
 */
package ro.ulbsibiu.acaps.mapper.ga.jmetal.metaheuristics.singleObjective.geneticAlgorithm;

import jmetal.base.*;
import jmetal.base.operator.comparator.* ;
import jmetal.base.operator.selection.WorstSolutionSelection;
import java.util.Comparator;

import ro.ulbsibiu.acaps.mapper.ga.jmetal.base.TrackedAlgorithm;

import jmetal.util.*;

/** 
 * Class implementing a steady state genetic algorithm
 */
public class ssGA extends TrackedAlgorithm {
  private Problem           problem_;        
  
 /**
  *
  * Constructor
  * Create a new SSGA instance.
  * @param problem Problem to solve
  *
  */
  public ssGA(Problem problem){
    this.problem_ = problem;                        
  } // SSGA
  
 /**
  * Execute the SSGA algorithm
 * @throws JMException 
  */
  public SolutionSet execute() throws JMException, ClassNotFoundException {
    int populationSize ;
    int maxEvaluations ;
    int evaluations    ;

    SolutionSet population        ;
    Operator    mutationOperator  ;
    Operator    crossoverOperator ;
    Operator    selectionOperator ;
    
    Comparator  comparator        ;
    
    comparator = new ObjectiveComparator(0) ; // Single objective comparator
    
    Operator findWorstSolution ;
    findWorstSolution = new WorstSolutionSelection(comparator) ;

    // Read the parameters
    populationSize = ((Integer)this.getInputParameter("populationSize")).intValue();
    maxEvaluations = ((Integer)this.getInputParameter("maxEvaluations")).intValue();                
   
    // Initialize the variables
    population   = new SolutionSet(populationSize);        
    evaluations  = 0;                

    // Read the operators
    mutationOperator  = this.operators_.get("mutation");
    crossoverOperator = this.operators_.get("crossover");
    selectionOperator = this.operators_.get("selection");  

    // Create the initial population
    Solution newIndividual;
    for (int i = 0; i < populationSize; i++) {
      newIndividual = new Solution(problem_);                    
      problem_.evaluate(newIndividual);            
      evaluations++;
      population.add(newIndividual);
    } //for       

	algorithmTracker.processIntermediateSolution("generations",
			Integer.toString(evaluations / populationSize),
			population.get(0));
    
    // main loop
    while (evaluations < maxEvaluations) {
      Solution [] parents = new Solution[2];
      
      // Selection
      parents[0] = (Solution)selectionOperator.execute(population);
      parents[1] = (Solution)selectionOperator.execute(population);
 
      // Crossover
      Solution [] offspring = (Solution []) crossoverOperator.execute(parents);  

      // Mutation
      mutationOperator.execute(offspring[0]);

      // Evaluation of the new individual
      problem_.evaluate(offspring[0]);            
          
      evaluations ++;
    
      // Replacement: replace the last individual is the new one is better
      int worstIndividual = (Integer)findWorstSolution.execute(population) ;
          
      if (comparator.compare(population.get(worstIndividual), offspring[0]) > 0) {
        population.remove(worstIndividual) ;
        population.add(offspring[0]);
      } // if
      
		algorithmTracker.processIntermediateSolution("generations",
				Integer.toString(evaluations / populationSize),
				population.get(0));
    } // while
    
    // Return a population with the best individual
    population.sort(comparator) ;

    SolutionSet resultPopulation = new SolutionSet(1) ;
    resultPopulation.add(population.get(0)) ;
    
    System.out.println("Evaluations: " + evaluations ) ;
    
    return resultPopulation ;
  } // execute
} // SSGA
