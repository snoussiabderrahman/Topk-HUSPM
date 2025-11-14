package utils;

import model.Sequence;

import java.util.HashMap;
import java.util.Map;

/**
 * UtilityCache : cache simple pour utilités de séquences.
 * Fournit :
 *  - Integer get(String signature)   // retourne null si absent
 *  - void put(String signature, int value)
 *  - int getUtility(Sequence sequence) // ancienne API compatible
 *  - void clear(), printStatistics(), getHitRate()
 */
public class UtilityCache {
    private final Map<String, Integer> cache;
    private int hits;
    private int misses;

    public UtilityCache() {
        this.cache = new HashMap<>();
        this.hits = 0;
        this.misses = 0;
    }

    /**
     * Récupère la valeur en cache par clé (signature).
     * Retourne null si absent.
     */
    public Integer get(String signature) {
        Integer v = cache.get(signature);
        if (v != null) {
            hits++;
        } else {
            misses++;
        }
        return v;
    }

    /**
     * Stocke une valeur dans le cache.
     */
    public void put(String signature, int value) {
        cache.put(signature, value);
    }

    public void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
    }

    public double getHitRate() {
        int total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public void printStatistics() {
        System.out.printf("Cache - Hits: %d, Misses: %d, Hit Rate: %.2f%%\n",
                hits, misses, getHitRate() * 100);
    }
}