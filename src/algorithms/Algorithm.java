package algorithms;

import config.AlgorithmConfig;
import model.Dataset;
import model.Sequence;

import java.util.List;

public interface Algorithm {
    /**
     * Exécute l'algorithme et retourne les top-k patterns
     */
    List<Sequence> run(Dataset dataset, AlgorithmConfig config);

    /**
     * Retourne le nom de l'algorithme
     */
    String getName();

    /**
     * Retourne le temps d'exécution en millisecondes
     */
    long getRuntime();

    /**
     * Retourne la mémoire utilisée en MB
     */
    double getMemoryUsage();
}