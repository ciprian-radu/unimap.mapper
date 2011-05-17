package ro.ulbsibiu.acaps.mapper.ga;

public class GeneticAlgorithmInputException extends Exception {

	String exceptionString;

	public GeneticAlgorithmInputException(String exceptionString) {
		this.exceptionString = exceptionString;
	}

	public String getLocalizedMessage() {
		/*
		 * return "The number of NoC nodes (" + nodes +
		 * ") is too small. At least " + cores +
		 * " are needed (this is the amount of cores which must be mapped)."; }
		 */

		return "\n"
				+ exceptionString
				+ "\n"
				+ "usage:   java GeneticAlgorithmMapperV1.class [populationSize generationSize crossoverPr mutationPr] [E3S benchmarks]"
				+ "\n"
				+ "1st parameter is number of population (population size)"
				+ "\n"
				+ "2nd parameter is number of generation to run (Maximun number of evolution"
				+ "\n"
				+ "3rd parameter is crossover probability (in real number) crossover probability < 1.00"
				+ "\n"
				+ "4th parameter is mutation probability (in real number) mutation probability < 1.00"
				+ "\n"
				+ "example 1 (specify the tgff file): java GeneticAlgorithmMapperV1.class 100 1000 0.8 0.2 ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff"
				+ "\n"
				+ "example 2 (specify the tgff file with default parameter): java GeneticAlgorithmMapperV1.class ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff ../CTG-XML/xml/e3s/telecom-mocsyn.tgff"
				+ "\n"
				+ "example 3 (specify the ctg): java GeneticAlgorithmMapperV1.class 100 1000 0.8 0.2 ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3"
				+ "\n"
				+ "example 4 (specify the ctg with default parameter): java GeneticAlgorithmMapperV1.class ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3"
				+ "\n"
				+ "example 5 (specify the ctg and apcg): java GeneticAlgorithmMapperV1.class 100 1000 0.8 0.2 ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3 --apcg 2"
				+ "\n"
				+ "example 6 (specify the ctg and apcg with default parameter): java GeneticAlgorithmMapperV1.class ../CTG-XML/xml/e3s/auto-indust-mocsyn.tgff --ctg 0+1+2+3 --apcg 2"
				+ "\n"
				+ "example 7 (map the entire E3S benchmark suite): java GeneticAlgorithmMapper.class 100 1000 0.8 0.2"
				+ "\n"
				+ "example 8 (map the entire E3S benchmark suite with default parameter): java GeneticAlgorithmMapper.class";
	}
}
