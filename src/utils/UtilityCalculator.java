package utils;

import model.*;
import java.util.BitSet;
import java.util.Collection;

/**
 * UtilityCalculator : utilise UtilityCache pour mémoriser utilités.
 */
public class UtilityCalculator {

    private static final UtilityCache cache = new UtilityCache();
    private static CompactSequenceIndex compactIndex = null;

    /**
     * ⚡ INITIALISATION : Construire l'index compact UNE SEULE FOIS
     */
    public static void initializeCompactIndex(Dataset dataset) {
        System.out.println("🔧 Building compact sequence index (inspired by HUSP-SP seq-array)...");
        long start = System.currentTimeMillis();

        compactIndex = new CompactSequenceIndex(dataset);

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("✅ Compact index built in %.2f seconds\n", elapsed / 1000.0);
    }

    /**
     * ⚡ CALCUL D'UTILITÉ OPTIMISÉ
     */
    public static long calculateSequenceUtility(
            Sequence generated,
            OptimizedDataStructures dataStructures) {

        String signature = generated.getSignature();
        Long cachedUtility = cache.get(signature);
        if (cachedUtility != null) {
            return cachedUtility;
        }

        BitSet candidateBitSet = dataStructures.findCandidateSequences(generated);

        if (candidateBitSet.isEmpty()) {
            cache.put(signature, 0L, generated.getDistinctItemIds());
            return 0L;
        }

        // ⭐ UTILISER LE MATCHING RAPIDE AU LIEU DE DP COMPLET
        long totalUtility;
        if (compactIndex != null) {
            // Version optimisée (3-5x plus rapide)
            totalUtility = compactIndex.fastCalculateUtility(generated, candidateBitSet);
        } else {
            // Fallback à la version originale
            totalUtility = getTotalUtility(generated, dataStructures, candidateBitSet);
        }

        cache.put(signature, totalUtility, generated.getDistinctItemIds());

        return totalUtility;
    }

    private static long getTotalUtility(Sequence generated, OptimizedDataStructures dataStructures,
                                        BitSet candidateBitSet) {
        long totalUtility = 0;
        for (int seqIdx = candidateBitSet.nextSetBit(0); seqIdx >= 0;
             seqIdx = candidateBitSet.nextSetBit(seqIdx + 1)) {
            Sequence qseq = dataStructures.getSequence(seqIdx);
            long maxUtility = FastSequenceMatcher.findMaximalUtility(generated, qseq);
            totalUtility += maxUtility;
        }
        return totalUtility;
    }

    /**
     * Réinitialise complètement le cache.
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * Invalide sélectivement les entrées du cache qui utilisent des items
     * qui viennent d'être supprimés des promising items.
     */
    public static void invalidateCacheForRemovedItems(Collection<Integer> removedItems) {
        cache.invalidateEntriesContainingAny(removedItems);
    }

    public static void printCacheStatistics() {
        cache.printStatistics();
    }
}