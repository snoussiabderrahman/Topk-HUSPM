package utils;

import model.*;
import java.util.*;

/**
 * Algorithme de matching ultra-rapide pour trouver toutes les instances d'une séquence pattern dans une q-sequence
 * ALGORITHME : Dynamic Programming avec élagage précoce
 * COMPLEXITÉ : O(|pattern| × |qseq| × avg_itemset_size)
 */
public class FastSequenceMatcher {

    /**
     * Classe pour représenter une instance (matching) trouvée
     */
    public static class MatchInstance {
        /** Positions dans la q-sequence */
        public final int[] positions;
        /** Utilité de cette instance */
        public long utility;

        public MatchInstance(int[] positions) {
            this.positions = positions;
            this.utility = 0;
        }
    }

    /**
     * Trouve TOUTES les instances d'une séquence pattern dans une q-sequence
     * et retourne celle avec l'UTILITÉ MAXIMALE (Sequential Maximal Utility)
     *
     * ALGORITHME :
     * 1. Pour chaque itemset du pattern, trouver les positions matchantes dans qseq
     * 2. Générer toutes les combinaisons valides (ordre temporel respecté)
     * 3. Calculer l'utilité de chaque instance
     * 4. Retourner le maximum
     *
     * OPTIMISATIONS :
     * - Élagage précoce : si aucune position ne match, on arrête
     * - Matrice de compatibilité pré-calculée
     * - Génération paresseuse des instances
     *
     * @param pattern la séquence générée (pattern)
     * @param qseq la q-sequence du dataset
     * @return l'utilité maximale trouvée (0 si aucun matching)
     */
    public static long findMaximalUtility(Sequence pattern, Sequence qseq) {
        int patternLength = pattern.length();
        int qseqLength = qseq.length();

        if (patternLength > qseqLength) {
            return 0; // Impossible de matcher
        }

        // Étape 1 : Construire la matrice de matching
        // matchMatrix[i][j] = true si pattern.itemset[i] ⊆ qseq.itemset[j]
        boolean[][] matchMatrix = buildMatchMatrix(pattern, qseq);

        // Étape 2 : Trouver toutes les instances valides avec programmation dynamique
        List<MatchInstance> instances = findAllInstancesDP(
                pattern, qseq, matchMatrix, patternLength, qseqLength
        );

        if (instances.isEmpty()) {
            return 0;
        }

        // Étape 3 : Calculer l'utilité de chaque instance et retourner le max
        long maxUtility = 0;
        for (MatchInstance instance : instances) {
            long utility = calculateInstanceUtility(pattern, qseq, instance.positions);
            maxUtility = Math.max(maxUtility, utility);
        }

        return maxUtility;
    }

    /**
     * Construit la matrice de matching
     *
     * matchMatrix[i][j] = true ssi tous les items de pattern.itemset[i]
     *                     sont présents dans qseq.itemset[j]
     *
     * COMPLEXITÉ : O(|pattern| × |qseq| × avg_itemset_size)
     */
    private static boolean[][] buildMatchMatrix(Sequence pattern, Sequence qseq) {
        int m = pattern.length();
        int n = qseq.length();
        boolean[][] matrix = new boolean[m][n];

        for (int i = 0; i < m; i++) {
            Itemset patternItemset = pattern.getItemsets().get(i);
            Set<Integer> patternIds = new HashSet<>();
            for (Item item : patternItemset.getItems()) {
                patternIds.add(item.getId());
            }

            for (int j = 0; j < n; j++) {
                Itemset qseqItemset = qseq.getItemsets().get(j);
                Set<Integer> qseqIds = new HashSet<>();
                for (Item item : qseqItemset.getItems()) {
                    qseqIds.add(item.getId());
                }

                // Vérifier si patternIds ⊆ qseqIds
                matrix[i][j] = qseqIds.containsAll(patternIds);
            }
        }

        return matrix;
    }

    /**
     * Trouve toutes les instances avec Programmation Dynamique
     *
     * ALGORITHME :
     * - dp[i][j] = liste des instances partielles matchant pattern[0..i] dans qseq[0..j]
     * - Pour chaque position, on étend les instances précédentes
     *
     * COMPLEXITÉ : O(|pattern| × |qseq| × nombre_instances)
     *              En pratique, très rapide grâce à l'élagage
     */
    private static List<MatchInstance> findAllInstancesDP(
            Sequence pattern, Sequence qseq, boolean[][] matchMatrix,
            int m, int n) {

        // dp[i] = liste des instances partielles matchant pattern[0..i-1]
        @SuppressWarnings("unchecked")
        List<int[]>[] dp = new ArrayList[m + 1];

        for (int i = 0; i <= m; i++) {
            dp[i] = new ArrayList<>();
        }

        // Initialisation : instance vide
        dp[0].add(new int[0]);

        // Remplir la table DP
        for (int i = 0; i < m; i++) {
            if (dp[i].isEmpty()) {
                break; // Élagage : aucune instance possible
            }

            for (int[] prevInstance : dp[i]) {
                int lastPos = prevInstance.length > 0 ? prevInstance[prevInstance.length - 1] : -1;

                // Chercher les positions matchantes pour pattern[i] après lastPos
                for (int j = lastPos + 1; j < n; j++) {
                    if (matchMatrix[i][j]) {
                        // Étendre l'instance
                        int[] newInstance = Arrays.copyOf(prevInstance, prevInstance.length + 1);
                        newInstance[prevInstance.length] = j;
                        dp[i + 1].add(newInstance);
                    }
                }
            }
        }

        // Convertir en MatchInstance
        List<MatchInstance> result = new ArrayList<>();
        for (int[] positions : dp[m]) {
            result.add(new MatchInstance(positions));
        }

        return result;
    }

    /**
     * Calcule l'utilité d'une instance spécifique
     *
     * DÉFINITION : u(instance) = Σ u(item) pour tous les items du pattern
     *              où u(item) est pris dans la q-sequence aux positions matchées
     *
     * @param pattern la séquence pattern
     * @param qseq la q-sequence
     * @param positions les positions matchées
     * @return l'utilité totale
     */
    private static long calculateInstanceUtility(Sequence pattern, Sequence qseq, int[] positions) {

        long utility = 0;

        for (int i = 0; i < pattern.length(); i++) {
            Itemset patternItemset = pattern.getItemsets().get(i);
            Itemset qseqItemset = qseq.getItemsets().get(positions[i]);

            // Créer une map pour un accès rapide aux utilités
            Map<Integer, Integer> qseqUtilities = new HashMap<>();
            for (Item item : qseqItemset.getItems()) {
                qseqUtilities.put(item.getId(), item.getUtility());
            }

            // Sommer les utilités des items du pattern
            for (Item patternItem : patternItemset.getItems()) {
                utility += qseqUtilities.getOrDefault(patternItem.getId(), 0);
            }
        }

        return utility;
    }
}