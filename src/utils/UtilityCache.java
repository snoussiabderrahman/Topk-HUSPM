package utils;

import java.util.*;

/**
 * UtilityCache : cache simple pour utilités de séquences, avec
 * invalidation sélective par items supprimés.
 */
public class UtilityCache {
    private static class Entry {
        final long value;
        final int[] itemIds; // ids distincts de la séquence mise en cache (triés)

        Entry(long value, int[] itemIds) {
            this.value = value;
            if (itemIds == null || itemIds.length == 0) {
                this.itemIds = new int[0];
            } else {
                this.itemIds = Arrays.copyOf(itemIds, itemIds.length);
                Arrays.sort(this.itemIds);
            }
        }
    }

    private final Map<String, Entry> cache = new HashMap<>();
    private int hits = 0;
    private int misses = 0;

    public synchronized Long get(String signature) {
        Entry e = cache.get(signature);
        if (e != null) {
            hits++;
            return e.value;
        } else {
            misses++;
            return null;
        }
    }

    /**
     * Stocke la valeur et la liste des itemIds associés.
     */
    public synchronized void put(String signature, long value, int[] itemIds) {
        cache.put(signature, new Entry(value, itemIds));
    }

    public synchronized void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
    }

    public synchronized double getHitRate() {
        int total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public synchronized void printStatistics() {
        System.out.printf("Cache - Entries: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%%n",
                cache.size(), hits, misses, getHitRate() * 100);
    }

    /**
     * Invalide (supprime) les entrées du cache qui contiennent au moins
     * un des itemIds dans removedItems.
     */
    public synchronized void invalidateEntriesContainingAny(Collection<Integer> removedItems) {
        if (removedItems == null || removedItems.isEmpty())
            return;
        Set<Integer> removed = (removedItems instanceof Set) ? (Set<Integer>) removedItems
                : new HashSet<>(removedItems);

        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> en = it.next();
            Entry entry = en.getValue();
            int[] ids = entry.itemIds;
            // test d'intersection (ids triés mais ici HashSet lookup est suffisant)
            for (int id : ids) {
                if (removed.contains(id)) {
                    it.remove();
                    break;
                }
            }
        }
    }
}