package utils;

import model.*;
import java.util.BitSet;

/**
 * Calculateur d'utilité ULTRA-OPTIMISÉ pour HUSPM
 *
 * AMÉLIORATIONS PAR RAPPORT À LA VERSION PRÉCÉDENTE :
 * 1. Utilise OptimizedDataStructures pour éviter de parcourir tout le dataset
 * 2. Utilise FastSequenceMatcher pour un matching rapide
 * 3. Cache les résultats avec signature structurelle
 * 4. Élagage précoce avec SWU
 *
 * COMPLEXITÉ :
 * - Avant : O(|dataset| × complexité_matching)
 * - Maintenant : O(|candidates| × complexité_matching)
 *   où |candidates| << |dataset| grâce aux BitSets
 */
public class UtilityCalculator {

    /**
     * Cache des utilités calculées
     */
    private static final UtilityCache cache = new UtilityCache();

    /**
     * Calcule l'utilité d'une séquence générée selon la Définition 5
     *
     * ALGORITHME OPTIMISÉ :
     * 1. Extraire les items de la séquence générée
     * 2. Utiliser l'index inversé (BitSet) pour trouver les q-sequences candidates
     * 3. Pour chaque q-sequence candidate, calculer Sequential Maximal Utility
     * 4. Sommer tous les maximums
     *
     * @param generated la séquence générée
     * @param dataStructures les structures optimisées
     * @return l'utilité totale
     */
    public static long calculateSequenceUtility(
            Sequence generated,
            OptimizedDataStructures dataStructures) {

        // Vérifier le cache
        String signature = generated.getSignature();
        Integer cachedUtility = cache.get(signature);
        if (cachedUtility != null) {
            return cachedUtility;
        }

        // Étape 1 : Trouver les q-sequences candidates avec BitSet
        BitSet candidateBitSet = dataStructures.findCandidateSequences(generated);

        if (candidateBitSet.isEmpty()) {
            cache.put(signature, 0);
            return 0;
        }

        // Étape 2 : Pour chaque q-sequence candidate, calculer max utility
        long totalUtility = getTotalUtility(generated, dataStructures, candidateBitSet);

        // Mettre en cache
        cache.put(signature, (int) totalUtility);

        return totalUtility;
    }

    private static long getTotalUtility(Sequence generated, OptimizedDataStructures dataStructures, BitSet candidateBitSet) {

        long totalUtility = 0;

        for (int seqIdx = candidateBitSet.nextSetBit(0);
             seqIdx >= 0;
             seqIdx = candidateBitSet.nextSetBit(seqIdx + 1)) {

            Sequence qseq = dataStructures.getSequence(seqIdx);

            // Calculer Sequential Maximal Utility pour cette q-sequence
            long maxUtility = FastSequenceMatcher.findMaximalUtility(generated, qseq);

            totalUtility += maxUtility;
        }
        return totalUtility;
    }

    /**
     * Réinitialise le cache
     */
    public static void clearCache() {
        cache.clear();
    }

}