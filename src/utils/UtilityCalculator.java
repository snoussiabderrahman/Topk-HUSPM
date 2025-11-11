package utils;

import model.*;
import java.util.*;

public class UtilityCalculator {

    /**
     * Calcule l'utilité d'une séquence générée selon la Définition 5 du papier
     * u(t) = Σ u(t → s) pour toutes les séquences s du dataset
     * où u(t → s) = max{u(matching_i)} pour tous les matchings de t dans s
     */
    public static int calculateSequenceUtility(Sequence generated, Dataset dataset) {
        int totalUtility = 0;

        // Pour chaque séquence quantifiée dans le dataset
        for (Sequence dataSeq : dataset.getSequences()) {
            // Trouver le maximum des utilités de tous les matchings
            int maxUtility = findMaxMatchingUtility(generated, dataSeq);
            totalUtility += maxUtility;
        }

        return totalUtility;
    }

    /**
     * Trouve le maximum des utilités de tous les matchings de 'pattern' dans 'sequence'
     */
    private static int findMaxMatchingUtility(Sequence pattern, Sequence sequence) {
        List<List<Integer>> allMatchings = findAllMatchings(pattern, sequence);

        if (allMatchings.isEmpty()) {
            return 0;
        }

        int maxUtility = 0;
        for (List<Integer> matching : allMatchings) {
            int utility = calculateMatchingUtility(pattern, sequence, matching);
            maxUtility = Math.max(maxUtility, utility);
        }

        return maxUtility;
    }

    /**
     * Trouve tous les matchings possibles de 'pattern' dans 'sequence'
     * Retourne une liste de listes d'indices (positions dans sequence)
     */
    private static List<List<Integer>> findAllMatchings(Sequence pattern, Sequence sequence) {
        List<List<Integer>> allMatchings = new ArrayList<>();

        if (pattern.isEmpty() || sequence.isEmpty() || pattern.length() > sequence.length()) {
            return allMatchings;
        }

        // Backtracking pour trouver tous les matchings
        findMatchingsRecursive(pattern, sequence, 0, 0, new ArrayList<>(), allMatchings);

        return allMatchings;
    }

    /**
     * Recherche récursive de tous les matchings
     */
    private static void findMatchingsRecursive(
            Sequence pattern,
            Sequence sequence,
            int patternIdx,
            int sequenceIdx,
            List<Integer> currentMatching,
            List<List<Integer>> allMatchings) {

        // Si on a matché tout le pattern
        if (patternIdx == pattern.length()) {
            allMatchings.add(new ArrayList<>(currentMatching));
            return;
        }

        // Si on a dépassé la séquence
        if (sequenceIdx >= sequence.length()) {
            return;
        }

        Itemset patternItemset = pattern.getItemsets().get(patternIdx);

        // Essayer de matcher à partir de chaque position restante
        for (int i = sequenceIdx; i < sequence.length(); i++) {
            Itemset seqItemset = sequence.getItemsets().get(i);

            // Vérifier si patternItemset est contenu dans seqItemset
            if (isSubset(patternItemset, seqItemset)) {
                currentMatching.add(i);
                findMatchingsRecursive(pattern, sequence, patternIdx + 1, i + 1,
                        currentMatching, allMatchings);
                currentMatching.remove(currentMatching.size() - 1);
            }
        }
    }

    /**
     * Vérifie si tous les items de 'subset' sont dans 'superset'
     */
    private static boolean isSubset(Itemset subset, Itemset superset) {
        Set<Integer> superIds = new HashSet<>();
        for (Item item : superset.getItems()) {
            superIds.add(item.getId());
        }

        for (Item item : subset.getItems()) {
            if (!superIds.contains(item.getId())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calcule l'utilité d'un matching spécifique
     */
    private static int calculateMatchingUtility(
            Sequence pattern,
            Sequence sequence,
            List<Integer> matching) {

        int utility = 0;

        for (int i = 0; i < pattern.length(); i++) {
            Itemset patternItemset = pattern.getItemsets().get(i);
            int seqIdx = matching.get(i);
            Itemset seqItemset = sequence.getItemsets().get(seqIdx);

            // Pour chaque item du pattern, trouver son utilité dans la séquence
            for (Item patternItem : patternItemset.getItems()) {
                for (Item seqItem : seqItemset.getItems()) {
                    if (patternItem.getId() == seqItem.getId()) {
                        utility += seqItem.getUtility();
                        break;
                    }
                }
            }
        }

        return utility;
    }
}