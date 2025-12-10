package utils;

import model.Dataset;
import model.Itemset;
import model.Sequence;

import java.util.*;

public class DatasetStatistics {
    private final int maxSequenceLength;
    private final List<Integer> itemsetSizes;

    public DatasetStatistics(Dataset dataset) {
        this.itemsetSizes = new ArrayList<>();
        int maxLen = 0;

        for (Sequence seq : dataset.getSequences()) {
            maxLen = Math.max(maxLen, seq.length());

            for (Itemset itemset : seq.getItemsets()) {
                // Distribution de taille
                int size = itemset.size();
                itemsetSizes.add(size);
            }
        }

        this.maxSequenceLength = maxLen;
    }

    public int getMaxSequenceLength() {
        return maxSequenceLength;
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
}