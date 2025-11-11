package utils;

import model.Dataset;
import model.Sequence;

import java.util.HashMap;
import java.util.Map;

public class UtilityCache {
    private final Map<String, Integer> cache;
    private int hits;
    private int misses;

    public UtilityCache() {
        this.cache = new HashMap<>();
        this.hits = 0;
        this.misses = 0;
    }

    public int getUtility(Sequence sequence, Dataset dataset) {
        String signature = sequence.getSignature();

        if (cache.containsKey(signature)) {
            hits++;
            return cache.get(signature);
        }

        misses++;
        int utility = UtilityCalculator.calculateSequenceUtility(sequence, dataset);
        cache.put(signature, utility);
        return utility;
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