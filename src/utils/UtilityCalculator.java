package utils;

import model.*;
import java.util.*;

/**
 * UtilityCalculator avec support du calcul incrémental
 */
public class UtilityCalculator {

    // Cache classique (pour patterns sans préfixe)
    private static final UtilityCache cache = new UtilityCache();

    // ⚡ NOUVEAU : Cache incrémental
    private static final IncrementalCache incrementalCache = new IncrementalCache();

    // Index compact
    private static CompactSequenceIndex compactIndex = null;

    // Flag pour activer/désactiver le mode incrémental
    private static boolean incrementalMode = false;

    // ========== INITIALISATION ==========

    public static void initializeCompactIndex(Dataset dataset) {
        System.out.println("🔧 Building compact sequence index...");
        long start = System.currentTimeMillis();

        compactIndex = new CompactSequenceIndex(dataset);

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("✅ Compact index built in %.2f seconds\n", elapsed / 1000.0);
    }

    /**
     * ⚡ ACTIVER LE MODE INCRÉMENTAL
     */
    public static void enableIncrementalMode() {
        incrementalMode = true;
        System.out.println("🚀 Incremental calculation mode ENABLED");
    }

    public static void disableIncrementalMode() {
        incrementalMode = false;
        System.out.println("⏸️  Incremental calculation mode DISABLED");
    }

    // ========== CALCUL D'UTILITÉ (AVEC DÉTECTION AUTOMATIQUE) ==========

    /**
     * ⚡ CALCUL INTELLIGENT : Détecte automatiquement si incrémental possible
     */
    public static long calculateSequenceUtility(
            Sequence generated,
            OptimizedDataStructures dataStructures) {

        String signature = generated.getSignature();

        // 1. Vérifier cache classique
        Long cachedUtility = cache.get(signature);
        if (cachedUtility != null) {
            return cachedUtility;
        }

        // 2. Trouver les séquences candidates
        BitSet candidateBitSet = dataStructures.findCandidateSequences(generated);

        if (candidateBitSet.isEmpty()) {
            cache.put(signature, 0L, generated.getDistinctItemIds());
            incrementalCache.put(signature, new ProjectedDatabase(generated));
            return 0L;
        }

        // 3. ⚡ CALCUL INCRÉMENTAL SI POSSIBLE
        long totalUtility;

        if (incrementalMode && generated.length() > 1) {
            // ✅ PASSER dataStructures en paramètre
            totalUtility = calculateIncremental(generated, candidateBitSet, dataStructures);
        } else {
            // ✅ PASSER dataStructures en paramètre
            totalUtility = calculateFull(generated, candidateBitSet, dataStructures);
        }

        // 4. Mettre en cache
        cache.put(signature, totalUtility, generated.getDistinctItemIds());

        return totalUtility;
    }

    /**
     * ⚡ CALCUL INCRÉMENTAL (si préfixe trouvé dans le cache)
     *
     * ✅ AJOUT DU PARAMÈTRE dataStructures
     */
    private static long calculateIncremental(Sequence pattern, BitSet candidates,
                                             OptimizedDataStructures dataStructures) {
        // Chercher un préfixe dans le cache incrémental
        ProjectedDatabase prefixProj = incrementalCache.findLongestPrefix(pattern);

        if (prefixProj != null) {
            // ⚡ CALCUL INCRÉMENTAL DEPUIS LE PRÉFIXE
            incrementalCache.recordIncrementalCalc();

            // Déterminer le type d'extension (I-concat ou S-concat)
            int prefixLength = prefixProj.getPattern().length();
            boolean isSConcat = (prefixLength < pattern.length()); // Simplification

            // Obtenir le nouvel itemset
            Itemset newItemset = pattern.getItemsets().get(pattern.length() - 1);

            // Étendre la projection
            ProjectedDatabase extended = prefixProj.extend(newItemset, isSConcat, compactIndex);

            if (extended != null) {
                // Stocker dans le cache
                incrementalCache.put(pattern.getSignature(), extended);
                return extended.getTotalUtility();
            } else {
                // Extension échouée : fallback au calcul complet
                return calculateFull(pattern, candidates, dataStructures);
            }
        } else {
            // Pas de préfixe : calcul complet
            return calculateFull(pattern, candidates, dataStructures);
        }
    }

    /**
     * CALCUL COMPLET (méthode classique)
     *
     * ✅ AJOUT DU PARAMÈTRE dataStructures
     */
    private static long calculateFull(Sequence pattern, BitSet candidates,
                                      OptimizedDataStructures dataStructures) {
        incrementalCache.recordFullCalc();

        if (compactIndex != null) {
            // Utiliser le matching rapide et construire la projection
            ProjectedDatabase proj = compactIndex.buildProjectedDatabase(pattern, candidates);

            // Stocker dans le cache incrémental pour futures extensions
            incrementalCache.put(pattern.getSignature(), proj);

            return proj.getTotalUtility();
        } else {
            // ✅ FALLBACK : méthode originale (maintenant dataStructures est accessible)
            long totalUtility = 0;
            for (int seqIdx = candidates.nextSetBit(0); seqIdx >= 0;
                 seqIdx = candidates.nextSetBit(seqIdx + 1)) {

                Sequence qseq = dataStructures.getSequence(seqIdx);
                long maxUtility = FastSequenceMatcher.findMaximalUtility(pattern, qseq);
                totalUtility += maxUtility;
            }
            return totalUtility;
        }
    }

    // ========== GESTION DU CACHE ==========

    public static void clearCache() {
        cache.clear();
        incrementalCache.clear();
    }

    public static void invalidateCacheForRemovedItems(Collection<Integer> removedItems) {
        cache.invalidateEntriesContainingAny(removedItems);
        incrementalCache.invalidateContaining(removedItems);
    }

    public static void printCacheStatistics() {
        System.out.println("\n╔════════════════════════════════════════════════╗");
        System.out.println("║         UTILITY CALCULATOR STATISTICS         ║");
        System.out.println("╚════════════════════════════════════════════════╝");

        cache.printStatistics();
        incrementalCache.printStatistics();
    }

    /**
     * ⚡ MÉTHODES UTILITAIRES POUR STATISTIQUES
     */
    public static double getIncrementalRate() {
        return incrementalCache.getIncrementalRate();
    }

    public static double getCacheHitRate() {
        return cache.getHitRate();
    }
}