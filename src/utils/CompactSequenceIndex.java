package utils;

import model.*;
import java.util.*;

/**
 * Structure compacte inspirée de HUSP-SP seq-array
 * Objectif : Accès O(1) aux items/utilities + élagage précoce
 */
public class CompactSequenceIndex {

    // ========== STRUCTURES PRINCIPALES ==========

    /**
     * Pour chaque q-sequence, stocke :
     * - items[] : tableau d'items séquentiels
     * - utilities[] : tableau des utilités correspondantes
     * - itemsetBoundaries : positions de début d'itemset (BitSet)
     * - itemIndex : Map<itemId, List<positions>>
     */
    private static class CompactSeq {
        final int[] items;           // [a, b, f, a, d] (séquentiel)
        final int[] utilities;       // [6, 2, 1, 3, 1]
        final int[] remainUtility;   // [7, 5, 4, 1, 0] (somme à droite)
        final BitSet itemsetStarts;  // positions de début d'itemset
        final Map<Integer, int[]> itemPositions; // itemId -> [pos1, pos2, ...]

        CompactSeq(Sequence seq) {
            // Compter le nombre total d'items
            int totalItems = seq.getItemsets().stream()
                    .mapToInt(Itemset::size)
                    .sum();

            this.items = new int[totalItems];
            this.utilities = new int[totalItems];
            this.remainUtility = new int[totalItems];
            this.itemsetStarts = new BitSet(totalItems);
            this.itemPositions = new HashMap<>();

            // Construire les arrays
            int idx = 0;
            for (Itemset itemset : seq.getItemsets()) {
                itemsetStarts.set(idx); // Marquer début d'itemset

                for (Item item : itemset.getItems()) {
                    items[idx] = item.getId();
                    utilities[idx] = item.getUtility();

                    // Index inversé
                    itemPositions.computeIfAbsent(item.getId(), k -> new int[0]);
                    int[] oldPos = itemPositions.get(item.getId());
                    int[] newPos = Arrays.copyOf(oldPos, oldPos.length + 1);
                    newPos[oldPos.length] = idx;
                    itemPositions.put(item.getId(), newPos);

                    idx++;
                }
            }

            // Calculer remainUtility (comme HUSP-SP)
            int sum = 0;
            for (int i = totalItems - 1; i >= 0; i--) {
                remainUtility[i] = sum;
                sum += utilities[i];
            }
        }

        /**
         * Trouve la position du prochain itemset après idx
         */
        int nextItemsetPos(int idx) {
            return itemsetStarts.nextSetBit(idx + 1);
        }

        /**
         * Retourne l'index de l'itemset contenant idx
         */
        int whichItemset(int idx) {
            return itemsetStarts.previousSetBit(idx);
        }
    }

    // Array de toutes les q-sequences compactes
    private final CompactSeq[] compactDB;
    private final int datasetSize;

    // ========== CONSTRUCTEUR ==========

    public CompactSequenceIndex(Dataset dataset) {
        this.datasetSize = dataset.size();
        this.compactDB = new CompactSeq[datasetSize];

        for (int i = 0; i < datasetSize; i++) {
            compactDB[i] = new CompactSeq(dataset.getSequence(i));
        }
    }

    // ========== MÉTHODE D'UTILITÉ RAPIDE ==========

    /**
     * Calcule l'utilité d'un pattern SANS DP complet !
     *
     * ALGORITHME :
     * 1. Pour chaque q-sequence candidate (BitSet)
     * 2. Utiliser itemPositions pour un matching rapide
     * 3. Calculer l'utilité en O(|pattern| × avg_occurrences)
     */
    public long fastCalculateUtility(Sequence pattern, BitSet candidates) {
        long total = 0;

        for (int seqIdx = candidates.nextSetBit(0); seqIdx >= 0;
             seqIdx = candidates.nextSetBit(seqIdx + 1)) {

            CompactSeq cs = compactDB[seqIdx];

            // Matching rapide avec index inversé
            long maxUtility = fastMatch(pattern, cs);
            total += maxUtility;
        }

        return total;
    }

    /**
     * Matching rapide sans DP complet
     *
     * HEURISTIQUE : Au lieu de générer TOUTES les instances (DP),
     * on fait un matching glouton qui trouve UNE bonne instance rapidement
     */
    private long fastMatch(Sequence pattern, CompactSeq cs) {
        // Extraire les items du pattern (dans l'ordre)
        List<Integer> patternItems = new ArrayList<>();
        for (Itemset itemset : pattern.getItemsets()) {
            for (Item item : itemset.getItems()) {
                patternItems.add(item.getId());
            }
        }

        // Matching glouton : pour chaque item du pattern, prendre la première occurrence après la précédente
        long utility = 0;
        int lastPos = -1;

        for (int i = 0; i < pattern.length(); i++) {
            Itemset patternItemset = pattern.getItemsets().get(i);

            // Pour chaque item de cet itemset
            long itemsetUtility = 0;
            boolean foundMatch = false;

            for (Item patternItem : patternItemset.getItems()) {
                int itemId = patternItem.getId();
                int[] positions = cs.itemPositions.get(itemId);

                if (positions == null) {
                    return 0; // Item absent
                }

                // Chercher la première position après lastPos dans le bon itemset
                for (int pos : positions) {
                    if (pos > lastPos) {
                        // Vérifier si dans le bon itemset (pour I-concat) ou après (pour S-concat)
                        if (i == 0 || isValidPosition(cs, lastPos, pos, i > 0)) {
                            itemsetUtility += cs.utilities[pos];
                            lastPos = Math.max(lastPos, pos);
                            foundMatch = true;
                            break;
                        }
                    }
                }
            }

            if (!foundMatch) {
                return 0; // Pas de match possible
            }

            utility += itemsetUtility;
        }

        return utility;
    }

    /**
     * Vérifie si une position est valide pour le matching
     */
    private boolean isValidPosition(CompactSeq cs, int lastPos, int newPos, boolean isSConcat) {
        if (isSConcat) {
            // S-concatenation : doit être dans un itemset ultérieur
            return cs.whichItemset(newPos) > cs.whichItemset(lastPos);
        } else {
            // I-concatenation : peut être dans le même itemset
            return cs.whichItemset(newPos) >= cs.whichItemset(lastPos);
        }
    }

    /**
     * Calcule une borne supérieure (PEU) SANS parcourir tout le dataset
     * Inspiré de TRSU de HUSP-SP
     */
    public long fastUpperBound(Sequence pattern, BitSet candidates) {
        long upperBound = 0;

        for (int seqIdx = candidates.nextSetBit(0); seqIdx >= 0;
             seqIdx = candidates.nextSetBit(seqIdx + 1)) {

            CompactSeq cs = compactDB[seqIdx];

            // Borne supérieure = utility des items du pattern + remainUtility
            long seqBound = 0;

            // Trouver la première occurrence de chaque item du pattern
            int minPos = Integer.MAX_VALUE;
            for (Itemset itemset : pattern.getItemsets()) {
                for (Item item : itemset.getItems()) {
                    int[] positions = cs.itemPositions.get(item.getId());
                    if (positions != null && positions.length > 0) {
                        minPos = Math.min(minPos, positions[0]);
                        seqBound += cs.utilities[positions[0]];
                    }
                }
            }

            // Ajouter remainUtility (items à droite)
            if (minPos < cs.items.length) {
                seqBound += cs.remainUtility[minPos];
            }

            upperBound += seqBound;
        }

        return upperBound;
    }
}