package utils;

import model.*;
import java.util.BitSet;
import java.util.Collection;

/**
 * UtilityCalculator : utilise UtilityCache pour mémoriser utilités.
 */
public class UtilityCalculator {

    private static final UtilityCache cache = new UtilityCache();

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

        long totalUtility = getTotalUtility(generated, dataStructures, candidateBitSet);

        cache.put(signature, totalUtility, generated.getDistinctItemIds());

        return totalUtility;
    }

    private static long getTotalUtility(Sequence generated, OptimizedDataStructures dataStructures,
            BitSet candidateBitSet) {

        long totalUtility = 0;

        for (int seqIdx = candidateBitSet.nextSetBit(0); seqIdx >= 0; seqIdx = candidateBitSet.nextSetBit(seqIdx + 1)) {

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