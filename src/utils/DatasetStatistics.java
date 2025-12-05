package utils;

import model.Dataset;
import model.Itemset;
import model.Sequence;

import java.util.*;

public class DatasetStatistics {
    private final Set<Integer> distinctItems;
    private final Map<Integer, Integer> itemsetSizeDistribution;
    private final int maxSequenceLength;
    private final List<Integer> itemsetSizes;

    public DatasetStatistics(Dataset dataset) {
        this.distinctItems = new HashSet<>();
        this.itemsetSizeDistribution = new HashMap<>();
        this.itemsetSizes = new ArrayList<>();
        int maxLen = 0;

        for (Sequence seq : dataset.getSequences()) {
            maxLen = Math.max(maxLen, seq.length());

            for (Itemset itemset : seq.getItemsets()) {
                // Collecter les items distincts
                itemset.getItems().forEach(item -> distinctItems.add(item.getId()));

                // Distribution de taille
                int size = itemset.size();
                itemsetSizeDistribution.put(size, itemsetSizeDistribution.getOrDefault(size, 0) + 1);
                itemsetSizes.add(size);
            }
        }

        this.maxSequenceLength = maxLen;
    }

    public Set<Integer> getDistinctItems() {
        return new HashSet<>(distinctItems);
    }

    public int getMaxSequenceLength() {
        return maxSequenceLength;
    }

    public Map<Integer, Integer> getItemsetSizeDistribution() {
        return new HashMap<>(itemsetSizeDistribution);
    }

    public List<Integer> getItemsetSizes() {
        return new ArrayList<>(itemsetSizes);
    }

    /**
     * Tire aléatoirement une taille d'itemset selon la distribution empirique
     */
    public int sampleItemsetSize(Random random) {
        if (itemsetSizes.isEmpty()) {
            return 1;
        }
        return itemsetSizes.get(random.nextInt(itemsetSizes.size()));
    }

    public void printStatistics() {
        System.out.println("=== Dataset Statistics ===");
        System.out.println("Distinct items: " + distinctItems.size());
        // System.out.println("Items: " + distinctItems);
        System.out.println("Max sequence length: " + maxSequenceLength);
        System.out.println("\nItemset size distribution:");

        itemsetSizeDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int size = entry.getKey();
                    int count = entry.getValue();
                    double percentage = (count * 100.0) / itemsetSizes.size();
                    System.out.printf("  Size %d: %d occurrences (%.2f%%)\n",
                            size, count, percentage);
                });
    }
}