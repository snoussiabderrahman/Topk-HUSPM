package model;

import java.util.*;
import java.util.stream.Collectors;

public class Sequence {
    private final List<Itemset> itemsets;
    private Integer cachedUtility;
    private int[] distinctItemIds = null;

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
        return Collections.unmodifiableList(itemsets);
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

    /**
     * Retourne un tableau trié d'ids d'items distincts présents dans la sequence.
     * Calcul lazy : construit le tableau une seule fois.
     */
    public int[] getDistinctItemIds() {
        if (distinctItemIds == null) {
            computeDistinctItemIds();
        }
        return distinctItemIds;
    }

    /**
     * Construit et met en cache le tableau d'ids distincts (trié).
     * Utilise un HashSet temporaire puis crée un int[] trié.
     */
    private void computeDistinctItemIds() {
        // Utilisation d'un HashSet temporaire (allocation ponctuelle)
        Set<Integer> set = new java.util.HashSet<>();
        for (Itemset itemset : this.itemsets) {
            for (Item it : itemset.getItems()) {
                set.add(it.getId());
            }
        }
        if (set.isEmpty()) {
            distinctItemIds = new int[0];
            return;
        }
        int[] arr = new int[set.size()];
        int i = 0;
        for (Integer v : set) {
            arr[i++] = v;
        }
        Arrays.sort(arr);
        distinctItemIds = arr;
    }

    /**
     * Libère la mémoire du cache distinctItemIds si besoin.
     * Appeler après buildInvertedIndex() si vous n'avez plus besoin par-séquence.
     */
    public void clearDistinctItemIds() {
        distinctItemIds = null;
    }

    /**
     * Conserve uniquement les ids distincts qui sont dans le set 'promisingItems'.
     * Si distinctItemIds est null, il est calculé (lazy) puis filtré.
     *
     * @param promisingItems ensemble des item ids promis à conserver
     */
    public void retainDistinctItemIds(Set<Integer> promisingItems) {
        if (promisingItems == null || promisingItems.isEmpty()) {
            // Rien à conserver : vider le cache
            this.distinctItemIds = new int[0];
            return;
        }
        // S'assurer d'avoir le cache
        if (this.distinctItemIds == null) {
            computeDistinctItemIds();
        }
        if (this.distinctItemIds.length == 0) return;

        // Filtrer en place : garder les ids qui sont dans promisingItems
        int write = 0;
        for (int id : this.distinctItemIds) {
            if (promisingItems.contains(id)) {
                this.distinctItemIds[write++] = id;
            }
        }
        if (write == 0) {
            this.distinctItemIds = new int[0];
        } else if (write < this.distinctItemIds.length) {
            // raccourcir le tableau pour libérer la mémoire
            this.distinctItemIds = Arrays.copyOf(this.distinctItemIds, write);
        }
    }

    @Override
    public String toString() {
        return itemsets.stream()
                .map(Itemset::toString)
                .collect(Collectors.joining(" ")) + " #UTIL:" + getUtility();
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