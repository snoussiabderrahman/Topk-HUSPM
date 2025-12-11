package utils;

import model.Item;
import model.Itemset;
import model.Sequence;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache pour les ProjectedDatabases
 * Gère l'invalidation intelligente et les statistiques
 */
public class IncrementalCache {

    /** Cache principal : signature → ProjectedDatabase */
    private final Map<String, ProjectedDatabase> cache;

    /** Statistiques */
    private long hits = 0;
    private long misses = 0;
    private long incrementalCalculations = 0;
    private long fullCalculations = 0;

    /** Taille max du cache (pour éviter explosion mémoire) */
    private final int maxSize;

    /** Queue LRU pour éviction */
    private final LinkedHashMap<String, Long> accessOrder;

    public IncrementalCache() {
        this(10000); // 10K patterns max par défaut
    }

    public IncrementalCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>();
        this.accessOrder = new LinkedHashMap<>(maxSize, 0.75f, true);
    }

    // ========== MÉTHODES PRINCIPALES ==========

    /**
     * Récupère une projection du cache
     */
    public synchronized ProjectedDatabase get(String signature) {
        ProjectedDatabase proj = cache.get(signature);

        if (proj != null) {
            hits++;
            accessOrder.put(signature, System.currentTimeMillis());
            return proj;
        } else {
            misses++;
            return null;
        }
    }

    /**
     * Stocke une projection dans le cache
     */
    public synchronized void put(String signature, ProjectedDatabase proj) {
        // Éviction LRU si cache plein
        if (cache.size() >= maxSize) {
            evictOldest();
        }

        cache.put(signature, proj);
        accessOrder.put(signature, System.currentTimeMillis());
    }

    /**
     * Tente de trouver un préfixe dans le cache
     *
     * HEURISTIQUE : Si pattern = <{a},{b},{c}>, chercher :
     * 1. <{a},{b}> (préfixe immédiat)
     * 2. <{a}> (préfixe plus court)
     *
     * @return ProjectedDatabase du préfixe le plus long trouvé, ou null
     */
    public synchronized ProjectedDatabase findLongestPrefix(Sequence pattern) {
        int patternLength = pattern.length();

        // Chercher du plus long au plus court
        for (int len = patternLength - 1; len >= 1; len--) {
            Sequence prefix = createPrefix(pattern, len);
            String prefixSig = prefix.getSignature();

            ProjectedDatabase proj = cache.get(prefixSig);
            if (proj != null) {
                hits++; // Compter comme hit
                return proj;
            }
        }

        misses++;
        return null;
    }

    /**
     * Crée un préfixe de longueur donnée
     */
    private Sequence createPrefix(Sequence pattern, int length) {
        Sequence prefix = new Sequence();

        for (int i = 0; i < length; i++) {
            Itemset original = pattern.getItemsets().get(i);
            Itemset copy = new Itemset();

            for (Item item : original.getItems()) {
                copy.addItem(new Item(item.getId()));
            }

            prefix.addItemset(copy);
        }

        return prefix;
    }

    /**
     * Éviction LRU (Least Recently Used)
     */
    private void evictOldest() {
        if (accessOrder.isEmpty()) return;

        // Trouver l'entrée la plus ancienne
        String oldestKey = accessOrder.keySet().iterator().next();

        cache.remove(oldestKey);
        accessOrder.remove(oldestKey);
    }

    /**
     * Invalide le cache (appelé quand minUtility change)
     */
    public synchronized void clear() {
        cache.clear();
        accessOrder.clear();
    }

    /**
     * Invalide sélectivement (patterns contenant des items supprimés)
     */
    public synchronized void invalidateContaining(Collection<Integer> removedItems) {
        Set<Integer> removed = new HashSet<>(removedItems);

        Iterator<Map.Entry<String, ProjectedDatabase>> it = cache.entrySet().iterator();
        int count = 0;

        while (it.hasNext()) {
            Map.Entry<String, ProjectedDatabase> entry = it.next();
            ProjectedDatabase proj = entry.getValue();

            // Vérifier si le pattern contient un item supprimé
            boolean shouldRemove = false;
            for (Itemset itemset : proj.getPattern().getItemsets()) {
                for (Item item : itemset.getItems()) {
                    if (removed.contains(item.getId())) {
                        shouldRemove = true;
                        break;
                    }
                }
                if (shouldRemove) break;
            }

            if (shouldRemove) {
                it.remove();
                accessOrder.remove(entry.getKey());
                count++;
            }
        }
        /*
        if (count > 0) {
            System.out.printf("♻️  Invalidated %d cached projections\n", count);
        }
         */
    }

    // ========== STATISTIQUES ==========

    public synchronized void recordIncrementalCalc() {
        incrementalCalculations++;
    }

    public synchronized void recordFullCalc() {
        fullCalculations++;
    }

    public synchronized void printStatistics() {
        long totalAccess = hits + misses;
        double hitRate = totalAccess > 0 ? (100.0 * hits / totalAccess) : 0;

        long totalCalc = incrementalCalculations + fullCalculations;
        double incRate = totalCalc > 0 ? (100.0 * incrementalCalculations / totalCalc) : 0;

        System.out.println("\n╔════════════════════════════════════════════════╗");
        System.out.println(  "║     INCREMENTAL CACHE STATISTICS               ║");
        System.out.println(  "╠════════════════════════════════════════════════╣");
        System.out.printf(   "║ Cache Size      : %8d / %8d          ║\n", cache.size(), maxSize);
        System.out.printf(   "║ Cache Hits      : %8d (%.1f%%)              ║\n", hits, hitRate);
        System.out.printf(   "║ Cache Misses    : %8d                     ║\n", misses);
        System.out.printf(   "║ Incremental     : %8d (%.1f%%)              ║\n", incrementalCalculations, incRate);
        System.out.printf(   "║ Full Calculation: %8d                     ║\n", fullCalculations);
        System.out.println(  "╚════════════════════════════════════════════════╝\n");

    }

    public synchronized double getHitRate() {
        long total = hits + misses;
        return total > 0 ? ((double) hits / total) : 0.0;
    }

    public synchronized double getIncrementalRate() {
        long total = incrementalCalculations + fullCalculations;
        return total > 0 ? ((double) incrementalCalculations / total) : 0.0;
    }
}