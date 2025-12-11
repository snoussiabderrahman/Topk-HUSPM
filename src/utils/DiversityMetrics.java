package utils;

import model.Sequence;
import java.util.*;

/**
 * Métriques pour mesurer la diversité d'un échantillon de séquences
 * Utilisé pour l'early stopping adaptatif
 */
public class DiversityMetrics {

    /**
     * Calcule la diversité d'un échantillon basée sur les signatures uniques
     *
     * Diversité = (nombre de séquences uniques) / (taille totale de l'échantillon)
     *
     * @param sample l'échantillon de séquences
     * @return diversité entre 0.0 (toutes identiques) et 1.0 (toutes uniques)
     */
    public static double calculateSignatureDiversity(List<Sequence> sample) {
        if (sample == null || sample.isEmpty()) {
            return 0.0;
        }

        Set<String> uniqueSignatures = new HashSet<>();
        for (Sequence seq : sample) {
            uniqueSignatures.add(seq.getSignature());
        }

        return (double) uniqueSignatures.size() / sample.size();
    }

    /**
     * Calcule la diversité basée sur la distribution des utilités
     *
     * Mesure la variance normalisée des utilités :
     * - Faible variance = peu de diversité (tous proches)
     * - Haute variance = haute diversité (utilités variées)
     *
     * @param sample l'échantillon de séquences
     * @return coefficient de variation normalisé [0, 1]
     */
    public static double calculateUtilityDiversity(List<Sequence> sample) {
        if (sample == null || sample.size() < 2) {
            return 0.0;
        }

        // Calculer moyenne et écart-type
        double sum = 0.0;
        double sumSquares = 0.0;

        for (Sequence seq : sample) {
            double util = seq.getUtility();
            sum += util;
            sumSquares += util * util;
        }

        double mean = sum / sample.size();
        double variance = (sumSquares / sample.size()) - (mean * mean);
        double stdDev = Math.sqrt(variance);

        // Coefficient de variation (normalisé)
        if (mean == 0) {
            return 0.0;
        }

        double cv = stdDev / mean; // Coefficient de variation

        // Normaliser à [0, 1] (utilise sigmoid pour limiter)
        return Math.tanh(cv); // tanh(x) ∈ [-1, 1], mais cv ≥ 0 donc ∈ [0, 1]
    }

    /**
     * Calcule la diversité basée sur les longueurs de séquences
     *
     * @param sample l'échantillon de séquences
     * @return entropie normalisée de la distribution des longueurs [0, 1]
     */
    public static double calculateLengthDiversity(List<Sequence> sample) {
        if (sample == null || sample.isEmpty()) {
            return 0.0;
        }

        // Compter les occurrences de chaque longueur
        Map<Integer, Integer> lengthCounts = new HashMap<>();
        for (Sequence seq : sample) {
            int len = seq.length();
            lengthCounts.put(len, lengthCounts.getOrDefault(len, 0) + 1);
        }

        // Calculer l'entropie de Shannon
        double entropy = 0.0;
        int totalCount = sample.size();

        for (int count : lengthCounts.values()) {
            if (count > 0) {
                double prob = (double) count / totalCount;
                entropy -= prob * Math.log(prob);
            }
        }

        // Normaliser par l'entropie maximale (log(n))
        double maxEntropy = Math.log(lengthCounts.size());

        return maxEntropy > 0 ? entropy / maxEntropy : 0.0;
    }

    /**
     * Calcule la diversité COMBINÉE (moyenne pondérée)
     *
     * @param sample l'échantillon de séquences
     * @param wSignature poids de la diversité des signatures (défaut: 0.5)
     * @param wUtility poids de la diversité des utilités (défaut: 0.3)
     * @param wLength poids de la diversité des longueurs (défaut: 0.2)
     * @return diversité combinée [0, 1]
     */
    public static double calculateCombinedDiversity(List<Sequence> sample,
                                                    double wSignature,
                                                    double wUtility,
                                                    double wLength) {
        double divSignature = calculateSignatureDiversity(sample);
        double divUtility = calculateUtilityDiversity(sample);
        double divLength = calculateLengthDiversity(sample);

        return wSignature * divSignature + wUtility * divUtility + wLength * divLength;
    }

    /**
     * Calcule la diversité combinée avec poids par défaut
     */
    public static double calculateCombinedDiversity(List<Sequence> sample) {
        return calculateCombinedDiversity(sample, 0.5, 0.3, 0.2);
    }

    /**
     * Affiche un rapport détaillé de diversité
     */
    public static void printDiversityReport(List<Sequence> sample) {
        System.out.println("\n=== Diversity Report ===");
        System.out.printf("Sample size: %d\n", sample.size());
        System.out.printf("Signature diversity: %.3f\n", calculateSignatureDiversity(sample));
        System.out.printf("Utility diversity: %.3f\n", calculateUtilityDiversity(sample));
        System.out.printf("Length diversity: %.3f\n", calculateLengthDiversity(sample));
        System.out.printf("Combined diversity: %.3f\n", calculateCombinedDiversity(sample));
        System.out.println("========================\n");
    }
}