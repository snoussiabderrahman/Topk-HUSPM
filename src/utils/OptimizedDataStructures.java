package utils;

import model.*;
import java.util.*;

/**
 * Structures de données optimisées pour le calcul rapide d'utilité
 * Inspiré de TKU-CE (BitSet, index inversés, SWU)
 *
 * POURQUOI CES STRUCTURES ?
 * - BitSet : représentation compacte et rapide pour les opérations ensemblistes (AND, OR)
 * - Index inversé : évite de parcourir tout le dataset à chaque calcul
 * - SWU : élagage précoce des items non prometteurs
 */
public class OptimizedDataStructures {

    // ==================== STRUCTURES PRINCIPALES ====================

    /**
     * SWU (Sequence Weighted Utility) de chaque item
     * SWU(item) = somme des utilités des q-sequences contenant cet item
     * Utilisé pour l'élagage : si SWU(item) < minUtil, on ignore cet item
     */
    private final Map<Integer, Long> mapItemToSWU;

    /**
     * Items prometteurs (SWU >= minUtil)
     * Triés par ordre croissant pour une recherche efficace
     */
    private final List<Integer> promisingItems;

    /**
     * Index inversé : pour chaque item, BitSet des q-sequences qui le contiennent
     *
     * POURQUOI BITSET ?
     * - Compact : 1 bit par séquence au lieu de 32 bits (int) ou 64 bits (long)
     * - Rapide : opérations AND/OR en temps linéaire sur des mots machine (64 bits)
     * - Exemple : pour 10000 séquences, BitSet = 1.25 KB vs List<Integer> = 40 KB
     */
    private final Map<Integer, BitSet> itemToSequenceBitSet;

    /**
     * Le dataset original (nécessaire pour calculer les utilités)
     */
    private final Dataset dataset;

    /**
     * Nombre total de q-sequences dans le dataset
     */
    private final int datasetSize;

    // ==================== CACHE POUR LES INTERSECTIONS ====================

    /**
     * Cache des intersections de BitSets pour éviter les recalculs
     * Clé : signature des items (ex: "1,3,5")
     * Valeur : BitSet des q-sequences contenant TOUS ces items
     */
    private final Map<String, BitSet> intersectionCache;

    // ==================== CONSTRUCTEUR ====================

    /**
     * Construit les structures optimisées à partir du dataset
     *
     * @param dataset le dataset de q-sequences
     * @param minUtil le seuil minimum d'utilité (peut être 0 au début)
     */
    public OptimizedDataStructures(Dataset dataset, long minUtil) {
        this.dataset = dataset;
        this.datasetSize = dataset.size();
        this.mapItemToSWU = new HashMap<>();
        this.promisingItems = new ArrayList<>();
        this.itemToSequenceBitSet = new HashMap<>();
        this.intersectionCache = new HashMap<>();

        // Étape 1 : Calculer SWU de chaque item
        buildSWU();

        // Étape 2 : Élager les items non prometteurs
        filterPromisingItems(minUtil);

        // Étape 3 : Construire les index inversés (BitSet)
        buildInvertedIndex();
    }

    // ==================== CONSTRUCTION DES STRUCTURES ====================

    /**
     * Étape 1 : Calcule SWU de chaque item
     *
     * DÉFINITION : SWU(item) = Σ u(s) pour toutes les q-sequences s contenant item
     *
     * COMPLEXITÉ : O(|dataset| × avg_seq_length × avg_itemset_size)
     */
    private void buildSWU() {
        int n = dataset.size();
        for (int seqIdx = 0; seqIdx < n; seqIdx++) {
            Sequence seq = dataset.getSequence(seqIdx); // accès direct, pas de copie
            int seqUtility = seq.getUtility(); // utilisera SUtility si elle a été parsée
            Set<Integer> itemsInSeq = new HashSet<>();

            // Collecter tous les items distincts dans cette séquence
            for (Itemset itemset : seq.getItemsets()) {
                for (Item item : itemset.getItems()) {
                    itemsInSeq.add(item.getId());
                }
            }

            // Ajouter l'utilité de la séquence au SWU de chaque item
            for (Integer itemId : itemsInSeq) {
                mapItemToSWU.put(itemId,
                        mapItemToSWU.getOrDefault(itemId, 0L) + seqUtility);
            }
        }
    }

    /**
     * Étape 2 : Filtre les items ayant SWU >= minUtil
     *
     * ÉLAGAGE : Si SWU(item) < minUtil, aucune séquence contenant cet item
     *           ne peut avoir une utilité >= minUtil
     *
     * @param minUtil le seuil minimum
     */
    private void filterPromisingItems(long minUtil) {
        for (Map.Entry<Integer, Long> entry : mapItemToSWU.entrySet()) {
            if (entry.getValue() >= minUtil) {
                promisingItems.add(entry.getKey());
            }
        }
        Collections.sort(promisingItems);
    }

    /**
     * Étape 3 : Construit l'index inversé avec BitSet
     *
     * STRUCTURE : itemToSequenceBitSet[item] = BitSet des indices des q-sequences
     *             contenant cet item
     *
     * EXEMPLE : item=5 apparaît dans sequences 0, 3, 7
     *           → BitSet[5] = {0: 1, 1: 0, 2: 0, 3: 1, 4: 0, 5: 0, 6: 0, 7: 1, ...}
     *
     * COMPLEXITÉ : O(|dataset| × avg_seq_length × avg_itemset_size)
     */
    private void buildInvertedIndex() {
        int n = dataset.size();

        // Pour accélérer contains() lors du remplissage, transformer promisingItems en HashSet
        Set<Integer> promisingSet = new HashSet<>(promisingItems);
        for (Integer itemId : promisingItems) {
            itemToSequenceBitSet.put(itemId, new BitSet(datasetSize));
        }

        for (int seqIdx = 0; seqIdx < n; seqIdx++) {
            Sequence seq = dataset.getSequence(seqIdx);
            Set<Integer> itemsInSeq = new HashSet<>();

            for (Itemset itemset : seq.getItemsets()) {
                for (Item item : itemset.getItems()) {
                    if (promisingSet.contains(item.getId())) {
                        itemsInSeq.add(item.getId());
                    }
                }
            }

            for (Integer itemId : itemsInSeq) {
                itemToSequenceBitSet.get(itemId).set(seqIdx);
            }
        }
    }

    // ==================== MÉTHODES DE REQUÊTE ====================

    /**
     * Trouve les q-sequences contenant TOUS les items d'une séquence générée
     *
     * ALGORITHME : Intersection des BitSets avec cache
     *
     * POURQUOI C'EST RAPIDE ?
     * - BitSet.and() utilise des opérations 64-bits (mots machine)
     * - Complexité : O(datasetSize / 64) au lieu de O(datasetSize)
     * - Cache : évite de recalculer les mêmes intersections
     *
     * @param generatedSeq la séquence générée
     * @return BitSet des indices des q-sequences candidates
     */
    public BitSet findCandidateSequences(Sequence generatedSeq) {
        // Extraire les items distincts de la séquence générée
        Set<Integer> itemSet = new TreeSet<>();
        for (Itemset itemset : generatedSeq.getItemsets()) {
            for (Item item : itemset.getItems()) {
                if (promisingItems.contains(item.getId())) {
                    itemSet.add(item.getId());
                }
            }
        }

        if (itemSet.isEmpty()) {
            return new BitSet(); // Aucune séquence candidate
        }

        // Générer la signature pour le cache
        String signature = itemSet.toString();

        // Vérifier le cache
        if (intersectionCache.containsKey(signature)) {
            return (BitSet) intersectionCache.get(signature).clone();
        }

        // Calculer l'intersection des BitSets
        List<Integer> items = new ArrayList<>(itemSet);
        BitSet result = (BitSet) itemToSequenceBitSet.get(items.getFirst()).clone();

        for (int i = 1; i < items.size(); i++) {
            result.and(itemToSequenceBitSet.get(items.get(i)));

            // Optimisation : si result est vide, pas besoin de continuer
            if (result.isEmpty()) {
                intersectionCache.put(signature, new BitSet());
                return new BitSet();
            }
        }

        // Mettre en cache
        intersectionCache.put(signature, (BitSet) result.clone());

        return result;
    }

    /**
     * Calcule rapidement l'utilité des séquences singleton <[item]>
     * pour chaque item de targetItems.
     * u(<[item]>) = Σ_{s ∈ D contenant item} (max utility of item in s)
     *
     * Complexité : somme pour chaque item du nombre de q-sequences qui le contiennent × avg_len(seq)
     */
    public Map<Integer, Long> computeSingletonUtilities(Collection<Integer> targetItems) {
        Map<Integer, Long> result = new HashMap<>();
        if (targetItems == null || targetItems.isEmpty()) return result;

        for (Integer itemId : targetItems) {
            long total = 0L;
            BitSet bs = itemToSequenceBitSet.get(itemId);
            if (bs == null) {
                result.put(itemId, 0L);
                continue;
            }
            for (int seqIdx = bs.nextSetBit(0); seqIdx >= 0; seqIdx = bs.nextSetBit(seqIdx + 1)) {
                int maxUtilityInSeq = getMaxUtilityInSeq(itemId, seqIdx);
                total += maxUtilityInSeq;
            }
            result.put(itemId, total);
        }
        return result;
    }

    private int getMaxUtilityInSeq(Integer itemId, int seqIdx) {
        Sequence seq = dataset.getSequences().get(seqIdx);
        int maxUtilityInSeq = 0;
        // rechercher l'utilité maximale de itemId dans cette q-sequence
        for (Itemset it : seq.getItemsets()) {
            for (Item itItem : it.getItems()) {
                if (itItem.getId() == itemId) {
                    int u = itItem.getUtility();
                    if (u > maxUtilityInSeq) maxUtilityInSeq = u;
                }
            }
        }
        return maxUtilityInSeq;
    }

    /**
     * Récupère une q-sequence par son index
     */
    public Sequence getSequence(int index) {
        return dataset.getSequences().get(index);
    }

    /**
     * Vérifie si un item est prometteur
     */
    public boolean isPromisingItem(int itemId) {
        return promisingItems.contains(itemId);
    }

    /**
     * Retourne la liste des items prometteurs
     */
    public List<Integer> getPromisingItems() {
        return new ArrayList<>(promisingItems);
    }

    /**
     * Retourne le SWU d'un item
     */
    public long getSWU(int itemId) {
        return mapItemToSWU.getOrDefault(itemId, 0L);
    }

    /**
     * Met à jour les items prometteurs avec un nouveau minUtil
     *
     * @param newMinUtil le nouveau seuil
     */
    public void updatePromisingItems(long newMinUtil) {
        promisingItems.clear();
        intersectionCache.clear(); // Invalider le cache
        filterPromisingItems(newMinUtil);
        buildInvertedIndex();
    }

    /**
     * Statistiques pour le débogage
     */
    public void printStatistics() {
        System.out.println("\n=== Optimized Data Structures Statistics ===");
        System.out.println("Dataset size: " + datasetSize);
        System.out.println("Total items with SWU: " + mapItemToSWU.size());
        System.out.println("Promising items: " + promisingItems.size());
        System.out.println("Cache size: " + intersectionCache.size());

        // Taille mémoire estimée des BitSets
        long bitSetMemory = (long) promisingItems.size() * (datasetSize / 8); // bits → bytes
        System.out.printf("BitSet memory estimate: %.2f KB\n", bitSetMemory / 1024.0);
    }
}