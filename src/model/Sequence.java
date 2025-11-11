package model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Sequence {
    private final List<Itemset> itemsets;
    private Integer cachedUtility;

    public Sequence() {
        this.itemsets = new ArrayList<>();
        this.cachedUtility = null;
    }

    public Sequence(List<Itemset> itemsets) {
        this.itemsets = new ArrayList<>(itemsets);
        this.cachedUtility = null;
    }

    public void addItemset(Itemset itemset) {
        if (!itemset.isEmpty()) {
            itemsets.add(itemset);
            cachedUtility = null; // Invalider le cache
        }
    }

    public List<Itemset> getItemsets() {
        return new ArrayList<>(itemsets);
    }

    public int length() {
        return itemsets.size();
    }

    public boolean isEmpty() {
        return itemsets.isEmpty();
    }

    public int getUtility() {
        if (cachedUtility == null) {
            cachedUtility = itemsets.stream()
                    .mapToInt(Itemset::getUtility)
                    .sum();
        }
        return cachedUtility;
    }

    public void setUtility(int utility) {
        this.cachedUtility = utility;
    }

    @Override
    public String toString() {
        return itemsets.stream()
                .map(Itemset::toString)
                .collect(Collectors.joining(" ")) + " #SUtility:" + getUtility();
    }

    public String toCompactString() {
        return "<" + itemsets.stream()
                .map(Itemset::toCompactString)
                .collect(Collectors.joining(",")) + ">";
    }

    // Pour utiliser comme clé dans le cache
    public String getSignature() {
        return itemsets.stream()
                .map(itemset -> itemset.getItems().stream()
                        .map(item -> String.valueOf(item.getId()))
                        .sorted()
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("|"));
    }
}