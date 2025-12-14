package algorithms;

import config.AlgorithmConfig;
import model.*;
import utils.DatasetStatistics;
import utils.OptimizedDataStructures;
import utils.UtilityCalculator;

import java.util.*;

public class TKUSP_V2 implements Algorithm {
    private long runtime;
    private double memoryUsage;
    private final Random random;
    private double[] lengthProbabilities; // p(i) for i in [1..max_length_seq]

    public TKUSP_V2() {
        this.random = new Random();
    }

    public TKUSP_V2(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public List<Sequence> run(Dataset dataset, AlgorithmConfig config) {
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 1) Statistiques dataset
        DatasetStatistics stats = new DatasetStatistics(dataset);

        // 2) INITIALISER dataStructures AVANT toute utilisation !
        long currentMinUtil = 0L; // ou valeur d'init voulue
        OptimizedDataStructures dataStructures = new OptimizedDataStructures(dataset, currentMinUtil);

        // 4) calculer utilités des singletons
        List<Integer> items = dataStructures.getPromisingItems();
        Map<Integer, Long> singletonUtils = dataStructures.computeSingletonUtilities(items);

        // 5) trier les singletons par utilité décroissante
        List<Map.Entry<Integer, Long>> singleList = new ArrayList<>(singletonUtils.entrySet());
        singleList.sort((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()));

        // 6) construire topK initial à partir des k singletons les plus utiles
        List<Sequence> topK = new ArrayList<>();
        int initialCount = Math.min(config.getK(), singleList.size());
        for (int idx = 0; idx < initialCount; idx++) {
            int itemId = singleList.get(idx).getKey();
            long util = singleList.get(idx).getValue();
            Sequence s = new Sequence();
            Itemset is = new Itemset();
            is.addItem(new Item(itemId)); // Item utility = 0 for pattern, sequence utility will be set
            s.addItemset(is);
            s.setUtility((int) util); // store singleton utility as initial cached utility
            topK.add(s);
        }

        // 7) initialiser currentMinUtil avec la k-ème utilité (ou la plus petite si
        // moins de k)
        if (singleList.size() >= config.getK()) {
            currentMinUtil = singleList.get(config.getK() - 1).getValue();
        } else if (!singleList.isEmpty())
            currentMinUtil = singleList.get(singleList.size() - 1).getValue();

        // 8) Pruner les items : reconstruire promising items / index en utilisant
        // currentMinUtil
        dataStructures.updatePromisingItems(currentMinUtil);
        // réduire le cache par-sequence en supprimant les ids non-prometteurs
        dataStructures.releaseNonPromisingDistinctIds();

        // Vider les caches d'utilité (les patterns précédemment calculés peuvent ne
        // plus être pertinents)
        UtilityCalculator.clearCache();

        // 9) reconstruire la liste active d'items et initialiser PM sur les items
        // prometteurs
        items = dataStructures.getPromisingItems();
        double[][] PM = initializeProbabilityMatrix(
                items.size(),
                config.getMaxSequenceLength());

        // Initialize sequence length probability uniformly
        lengthProbabilities = initializeLengthProbabilities(config.getMaxSequenceLength());

        // --------------------------------------------------------------------------
        List<Sequence> elite = null; // elite from previous iteration for smooth factor

        int iteration = 1;

        while (iteration <= config.getMaxIterations() && !isBinaryMatrix(PM)) {
            //System.out.printf("\n Iteration %d ", iteration);

            // Calculate smooth factor from elite (use rho for first iteration)
            double smoothFactor = config.getRho();
            if (iteration > 1 && elite != null && !elite.isEmpty()) {
                smoothFactor = calculateSmoothFactor(elite, config.getRho());
            }

            // Générer l'échantillon avec smoothing mutation et fusion
            List<Sequence> sample = generateSample(
                    PM,
                    config.getSampleSize(),
                    items,
                    stats,
                    config.getMaxSequenceLength(),
                    smoothFactor,
                    elite,
                    topK);

            // ===== CALCUL OPTIMISÉ DES UTILITÉS =====
            for (Sequence seq : sample) {
                long utility = UtilityCalculator.calculateSequenceUtility(seq, dataStructures);
                seq.setUtility((int) utility);
            }
            // =========================================

            // Trier par utilité décroissante
            sample.sort((s1, s2) -> Integer.compare(s2.getUtility(), s1.getUtility()));

            // Sélectionner l'élite
            int eliteSize = Math.max(1, (int) Math.ceil(config.getRho() * sample.size()));
            elite = sample.subList(0, eliteSize);

            // Mettre à jour top-k
            topK = updateTopK(topK, sample, config.getK());

            // Mettre à jour currentMinUtil en utilisant le k-ième utilité de topK
            long newMinUtilFromTopK = 0;
            if (topK.size() >= config.getK()) {
                newMinUtilFromTopK = topK.get(config.getK() - 1).getUtility();
            } else if (!topK.isEmpty()) {
                newMinUtilFromTopK = topK.get(topK.size() - 1).getUtility();
            }

            if (newMinUtilFromTopK > currentMinUtil) {
                // System.out.printf("TopK increased minUtil: %d -> %d\n", currentMinUtil,
                // newMinUtilFromTopK);
                currentMinUtil = newMinUtilFromTopK;

                // sauvegarder ancien mapping items -> PM (ou juste les items)
                List<Integer> oldItemsForPM = new ArrayList<>(items);
                double[][] oldPMForMerge = copyMatrix(PM);

                // pruning
                dataStructures.updatePromisingItems(currentMinUtil);
                dataStructures.releaseNonPromisingDistinctIds();

                // nouvelle liste d'items prometteurs
                items = dataStructures.getPromisingItems();

                // calculer les items supprimés
                Set<Integer> removed = new HashSet<>(oldItemsForPM);
                removed.removeAll(items);

                if (!removed.isEmpty()) {
                    // invalider seulement les entrées qui contiennent les items supprimés
                    UtilityCalculator.invalidateCacheForRemovedItems(removed);
                }

                // reconstruire PM en préservant les lignes des items communs ...
                PM = rebuildProbabilityMatrixPreserving(oldPMForMerge, oldItemsForPM, items,
                        config.getMaxSequenceLength());

                // Filtrer l'élite pour retirer les items qui ne sont plus prometteurs
                elite = filterEliteSequences(elite, items);
            }

            // Mettre à jour la matrice de probabilité
            PM = updateProbabilityMatrix(PM, elite, items, config);

            // Update sequence length probabilities based on elite statistics
            lengthProbabilities = updateLengthProbabilities(elite, config.getMaxSequenceLength(), config);

            // Afficher la matrice PM après la mise à jour
            /*
             * System.out.
             * println("\n=== Matrice PM après updateProbabilityMatrix (Iteration " +
             * iteration + ") ===");
             * printProbabilityMatrix(PM, items);
             *
             */

            iteration++;
        }

        dataStructures.releasePerSequenceDistinctIds();
        long endTime = System.currentTimeMillis();
        this.runtime = endTime - startTime;

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        this.memoryUsage = (memoryAfter - memoryBefore) / (1024.0 * 1024.0);
        /*
        System.out.println("\n=== Algorithm Completed ===");
        System.out.printf("Total iterations: %d\n", iteration - 1);
        System.out.printf("Runtime: %.2f s\n", this.runtime / 1000.0);
        System.out.printf("Memory: %.2f MB\n", this.memoryUsage);
        UtilityCalculator.printCacheStatistics();
         */

        return topK;
    }

    /**
     * Copie profonde d'une matrice double.
     */
    private double[][] copyMatrix(double[][] src) {
        if (src == null)
            return null;
        int rows = src.length;
        int cols = src[0].length;
        double[][] dst = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
        return dst;
    }

    /**
     * Reconstruit une PM de taille newItems.size() x maxCols :
     * - copie les lignes de oldPM correspondant aux itemIds communs (par id),
     * - initialise à defaultProb les lignes des nouveaux items,
     * - copie au plus min(oldCols, maxCols) colonnes (oldCols peut être égal à
     * maxCols).
     */
    private double[][] rebuildProbabilityMatrixPreserving(
            double[][] oldPM,
            List<Integer> oldItems,
            List<Integer> newItems,
            int maxCols) {

        // Hypothèses : oldPM != null, oldItems != null, newItems subset of oldItems,
        // oldPM[0].length == maxCols, et newItems.size() <= oldItems.size()

        int newRows = newItems.size();
        double[][] newPM = new double[newRows][maxCols];

        // initialiser à defaultProb
        for (int i = 0; i < newRows; i++) {
            Arrays.fill(newPM[i], 0.5);
        }

        // construire map itemId -> oldIndex (nécessaire pour aligner par id)
        Map<Integer, Integer> oldIndex = new HashMap<>(oldItems.size());
        for (int i = 0; i < oldItems.size(); i++)
            oldIndex.put(oldItems.get(i), i);

        // copier directement maxCols colonnes (on assume oldPM[oi].length == maxCols)
        for (int r = 0; r < newRows; r++) {
            Integer oi = oldIndex.get(newItems.get(r));
            if (oi != null) {
                System.arraycopy(oldPM[oi], 0, newPM[r], 0, maxCols);
            }
        }

        return newPM;
    }

    private double[][] initializeProbabilityMatrix(int numItems, int maxLength) {
        double[][] PM = new double[numItems][maxLength];
        for (int i = 0; i < numItems; i++) {
            for (int j = 0; j < maxLength; j++) {
                PM[i][j] = 0.5;
            }
        }
        return PM;
    }

    /**
     * Initialize sequence length probabilities uniformly.
     * p(i) = 1 / max_length_seq for all i in [1..max_length_seq]
     */
    private double[] initializeLengthProbabilities(int maxLength) {
        double[] probs = new double[maxLength];
        double uniformProb = 1.0 / maxLength;
        Arrays.fill(probs, uniformProb);
        return probs;
    }

    /**
     * Update sequence length probabilities based on elite statistics.
     * p_new(i) = (occ(i) + 1) / (elite_size + max_length_seq)
     * where occ(i) = number of sequences of length i in elite
     */
    private double[] updateLengthProbabilities(List<Sequence> elite, int maxLength, AlgorithmConfig config) {
        int eliteSize = elite.size();
        double[] newProbs = new double[maxLength];

        // Count occurrences of each length in elite
        int[] occurrences = new int[maxLength];
        for (Sequence seq : elite) {
            int len = seq.length();
            if (len >= 1 && len <= maxLength) {
                occurrences[len - 1]++; // length i maps to index i-1
            }
        }

        // Calculate frequency in elite (with Laplace smoothing)
        double denominator = eliteSize + maxLength;

        for (int i = 0; i < maxLength; i++) {
            double frequency = (occurrences[i] + 1) / denominator;

            // 1. Apply Learning Rate (Alpha)
            // P_new = (1 - alpha) * P_old + alpha * P_elite
            newProbs[i] = (1.0 - config.getLearningRate()) * lengthProbabilities[i]
                    + config.getLearningRate() * frequency;
            // 2. Apply Minimum Probability Bound
            // Ensure at least minProbability (or a small fraction like 0.01) for each
            // length
            //double minLenProb = Math.max(config.getMinProbability(), 0.01);
            newProbs[i] = Math.max(0.05, newProbs[i]);
        }

        // 3. Renormalize to ensure sum is 1.0
        double sum = 0.0;
        for (double p : newProbs)
            sum += p;

        for (int i = 0; i < maxLength; i++) {
            newProbs[i] /= sum;
        }

        return newProbs;
    }

    /**
     * Sample a sequence length from the current probability distribution.
     * Uses cumulative distribution function for sampling.
     */
    private int sampleSequenceLength() {
        double r = random.nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < lengthProbabilities.length; i++) {
            cumulative += lengthProbabilities[i];
            if (r <= cumulative) {
                return i + 1; // length = index + 1
            }
        }

        // Fallback to last length (should rarely happen due to rounding)
        return lengthProbabilities.length;
    }

    private boolean isBinaryMatrix(double[][] PM) {
        for (double[] row : PM) {
            for (double val : row) {
                if (val != 0.0 && val != 1.0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Calculate smooth factor α based on elite diversity.
     * α = [(u_best − u_quantile) / u_best] × ρ
     */
    private double calculateSmoothFactor(List<Sequence> elite, double rho) {
        if (elite == null || elite.isEmpty())
            return rho;

        int eliteSize = elite.size();
        int quantileIdx = (int) Math.floor(eliteSize * rho);
        if (quantileIdx >= eliteSize)
            quantileIdx = eliteSize - 1;

        int uBest = elite.get(0).getUtility(); // elite is sorted descending
        int uQuantile = elite.get(quantileIdx).getUtility();

        if (uBest == 0)
            return rho; // avoid division by zero

        double alpha = ((double) (uBest - uQuantile) / uBest) * rho;
        return Math.max(0.0, Math.min(alpha, 1.0)); // clamp to [0,1]
    }

    /**
     * Generate a completely random sequence for exploration.
     * Uses uniform random length and random items from promising items.
     */
    private Sequence generateRandomSequence(int maxLength, List<Integer> items,
                                            DatasetStatistics stats) {
        // Random sequence length (uniform for true exploration)
        int seqLength = 1 + random.nextInt(maxLength);

        Sequence sequence = new Sequence();

        for (int pos = 0; pos < seqLength; pos++) {
            // Random itemset size from dataset distribution
            int itemsetSize = stats.sampleItemsetSize(random);
            if (itemsetSize <= 0)
                itemsetSize = 1;

            // Random items from promising items
            List<Item> chosenItems = new ArrayList<>();
            int maxAttempts = Math.min(itemsetSize, items.size());

            Set<Integer> selectedIds = new HashSet<>();
            for (int i = 0; i < maxAttempts; i++) {
                int randomIdx = random.nextInt(items.size());
                int itemId = items.get(randomIdx);
                if (selectedIds.add(itemId)) {
                    chosenItems.add(new Item(itemId));
                }
            }

            if (!chosenItems.isEmpty()) {
                sequence.addItemset(new Itemset(chosenItems));
            }
        }

        return sequence;
    }

    private List<Sequence> generateSample(double[][] PM, int N, List<Integer> items,
                                          DatasetStatistics stats, int maxLength, double smoothFactor,
                                          List<Sequence> elite, List<Sequence> topK) {
        List<Sequence> sample = new ArrayList<>();

        // Paramètres de génération
        double fusionRatio = 0.10; // 10% pour fusion-based exploration
        int numFusion = (int) Math.floor(N * fusionRatio);
        int numRandom = (int) Math.floor(N * smoothFactor);
        int numPMBased = N - numFusion - numRandom;

        // 1. Generate fusion-based sequences (exploration via crossover)
        if (numFusion > 0 && elite != null && !elite.isEmpty()) {
            List<Sequence> fusionSeqs = generateFusionSequences(elite, topK, numFusion, maxLength);
            sample.addAll(fusionSeqs);
        }

        // 2. Generate random sequences (exploration)
        for (int i = 0; i < numRandom; i++) {
            Sequence seq = generateRandomSequence(maxLength, items, stats);
            if (!seq.isEmpty()) {
                sample.add(seq);
            }
        }

        // 3. Generate PM-based sequences (exploitation)
        for (int i = 0; i < numPMBased; i++) {
            // Sample sequence length from learned distribution
            int seqLength = sampleSequenceLength();
            // System.out.println(seqLength);

            Sequence sequence = new Sequence();

            for (int pos = 0; pos < seqLength; pos++) {
                // Générer un itemset pour cette position
                Itemset itemset = generateItemset(PM, items, pos, stats);

                if (!itemset.isEmpty()) {
                    sequence.addItemset(itemset);
                }
            }

            if (!sequence.isEmpty()) {
                sample.add(sequence);
            }
        }

        return sample;
    }

    /**
     * Génère des séquences par fusion (crossover) de deux séquences existantes.
     * Sélectionne des séquences de taille < maxLength/2 et les fusionne dans les
     * deux ordres.
     */
    private List<Sequence> generateFusionSequences(List<Sequence> elite, List<Sequence> topK,
                                                   int numFusion, int maxLength) {
        List<Sequence> fusionSeqs = new ArrayList<>();
        Set<String> signatures = new HashSet<>(); // Pour éviter les doublons

        // Collecter les candidats: séquences de longueur <= maxLength/2
        List<Sequence> candidates = new ArrayList<>();
        int maxLenForFusion = maxLength / 2;

        // Ajouter d'abord les séquences de l'élite
        if (elite != null) {
            for (Sequence seq : elite) {
                if (seq.length() > 0 && seq.length() <= maxLenForFusion) {
                    candidates.add(seq);
                }
            }
        }

        // Si pas assez de candidats, retourner une liste vide
        if (candidates.size() < 2) {
            return fusionSeqs;
        }

        // Générer les fusions
        int attempts = 0;
        int maxAttempts = numFusion * 3; // Éviter boucle infinie

        while (fusionSeqs.size() < numFusion && attempts < maxAttempts) {
            attempts++;

            // Sélectionner deux séquences aléatoires
            int idx1 = random.nextInt(candidates.size());
            int idx2 = random.nextInt(candidates.size());

            if (idx1 == idx2 && candidates.size() > 1) {
                continue; // Éviter de fusionner une séquence avec elle-même
            }

            Sequence s1 = candidates.get(idx1);
            Sequence s2 = candidates.get(idx2);

            // Vérifier que la fusion ne dépasse pas maxLength
            if (s1.length() + s2.length() > maxLength) {
                continue;
            }

            // Fusion dans les deux ordres: s1+s2 et s2+s1
            Sequence fusion1 = fuseSequences(s1, s2);
            Sequence fusion2 = fuseSequences(s2, s1);

            // Ajouter fusion1 si non dupliquée
            if (!fusion1.isEmpty()) {
                String sig1 = fusion1.getSignature();
                if (signatures.add(sig1)) {
                    fusionSeqs.add(fusion1);
                }
            }

            // Ajouter fusion2 si non dupliquée et différente de fusion1
            if (fusionSeqs.size() < numFusion && !fusion2.isEmpty()) {
                String sig2 = fusion2.getSignature();
                if (signatures.add(sig2)) {
                    fusionSeqs.add(fusion2);
                }
            }
        }

        return fusionSeqs;
    }

    /**
     * Fusionne deux séquences en concaténant leurs itemsets.
     */
    private Sequence fuseSequences(Sequence s1, Sequence s2) {
        Sequence fused = new Sequence();

        // Copier tous les itemsets de s1
        for (Itemset itemset : s1.getItemsets()) {
            Itemset copy = new Itemset();
            for (Item item : itemset.getItems()) {
                copy.addItem(new Item(item.getId()));
            }
            fused.addItemset(copy);
        }

        // Copier tous les itemsets de s2
        for (Itemset itemset : s2.getItemsets()) {
            Itemset copy = new Itemset();
            for (Item item : itemset.getItems()) {
                copy.addItem(new Item(item.getId()));
            }
            fused.addItemset(copy);
        }

        return fused;
    }

    private Itemset generateItemset(double[][] PM, List<Integer> items, int position, DatasetStatistics stats) {
        List<Item> chosenItems = new ArrayList<>();

        // Construire d'abord la liste des candidats (comme avant)
        for (int itemIdx = 0; itemIdx < items.size(); itemIdx++) {
            if (random.nextDouble() < PM[itemIdx][position]) {
                int itemId = items.get(itemIdx);
                chosenItems.add(new Item(itemId));
            }
        }

        int maxElemSize = stats.sampleItemsetSize(random);
        if (maxElemSize <= 0)
            maxElemSize = 1;

        // Si plus de candidats que k, faire un Fisher-Yates PARTIEL :
        // pour i in [0..k-1] swap chosenItems[i] avec chosenItems[i + rnd(0..n-i-1)]
        if (chosenItems.size() > maxElemSize) {
            int n = chosenItems.size();
            for (int i = 0; i < maxElemSize; i++) {
                int j = i + random.nextInt(n - i); // j in [i, n-1]
                // swap elements i et j
                Item tmp = chosenItems.get(i);
                chosenItems.set(i, chosenItems.get(j));
                chosenItems.set(j, tmp);
            }
            // Gagner du temps : on ne fait que k swaps (au lieu de n swaps d'un shuffle
            // complet)
            chosenItems = chosenItems.subList(0, maxElemSize);
        }

        // Fallback si aucun item choisi
        if (chosenItems.isEmpty()) {
            chosenItems.add(fallbackItem(PM, items, position));
        }
        // System.out.printf(" size itemset = %d ",chosenItems.size());

        return new Itemset(chosenItems);
    }

    private Item fallbackItem(double[][] PM, List<Integer> items, int position) {
        double sum = 0.0;
        for (int i = 0; i < items.size(); i++) {
            sum += PM[i][position];
        }

        if (sum == 0.0) {
            int itemId = items.get(random.nextInt(items.size()));
            return new Item(itemId);
        }

        double r = random.nextDouble() * sum;
        double cumulative = 0.0;

        for (int i = 0; i < items.size(); i++) {
            cumulative += PM[i][position];
            if (cumulative >= r) {
                return new Item(items.get(i));
            }
        }

        return new Item(items.get(0));
    }

    private List<Sequence> updateTopK(List<Sequence> currentTopK, List<Sequence> sample, int k) {

        // Min-heap : la plus petite utilité est en tête
        PriorityQueue<Sequence> pq = new PriorityQueue<>(
                Comparator.comparingInt(Sequence::getUtility));

        // Déduplication par signature (évite d'ajouter la même séquence plusieurs fois)
        Set<String> seen = new HashSet<>();

        // Ajoute les éléments actuels
        for (Sequence seq : currentTopK) {
            String sig = seq.getSignature();
            if (seen.add(sig)) {
                pq.offer(seq);
                if (pq.size() > k) { // si dépassé, on retire le plus petit
                    pq.poll();
                }
            }
        }

        // Ajoute les nouveaux candidats (sample)
        for (Sequence seq : sample) {
            String sig = seq.getSignature();
            if (seen.add(sig)) {
                pq.offer(seq);
                if (pq.size() > k) {
                    pq.poll();
                }
            }
        }

        // Récupérer les éléments du heap dans l'ordre décroissant
        List<Sequence> result = new ArrayList<>(pq);
        result.sort((a, b) -> Integer.compare(b.getUtility(), a.getUtility()));
        return result;
    }

    /**
     * Filtre les séquences de l'élite en ne gardant que les items prometteurs.
     * Retourne une nouvelle liste de séquences filtrées.
     */
    private List<Sequence> filterEliteSequences(List<Sequence> elite, List<Integer> promisingItems) {
        Set<Integer> promisingSet = new HashSet<>(promisingItems);
        List<Sequence> filteredElite = new ArrayList<>();

        for (Sequence seq : elite) {
            Sequence filteredSeq = new Sequence();
            filteredSeq.setUtility(seq.getUtility());

            for (Itemset itemset : seq.getItemsets()) {
                Itemset filteredItemset = new Itemset();

                for (Item item : itemset.getItems()) {
                    if (promisingSet.contains(item.getId())) {
                        filteredItemset.addItem(item);
                    }
                }

                // N'ajouter l'itemset que s'il n'est pas vide
                if (!filteredItemset.isEmpty()) {
                    filteredSeq.addItemset(filteredItemset);
                }
            }

            // N'ajouter la séquence que si elle n'est pas vide
            if (!filteredSeq.isEmpty()) {
                filteredElite.add(filteredSeq);
            }
        }

        return filteredElite;
    }

    private double[][] updateProbabilityMatrix(double[][] PM, List<Sequence> elite, List<Integer> items,
                                               AlgorithmConfig config) {
        double[][] newPM = new double[PM.length][PM[0].length];

        for (int itemIdx = 0; itemIdx < items.size(); itemIdx++) {
            int itemId = items.get(itemIdx);

            for (int pos = 0; pos < PM[0].length; pos++) {
                int count = 0;
                int total = 0;

                for (Sequence seq : elite) {
                    if (pos < seq.length()) {
                        total++;
                        Itemset itemset = seq.getItemsets().get(pos);

                        // Vérifier si l'item est dans cet itemset
                        boolean containsItem = itemset.getItems().stream()
                                .anyMatch(item -> item.getId() == itemId);

                        if (containsItem) {
                            count++;
                        }
                    }
                }

                // Calculer la nouvelle probabilité
                double probability = (total > 0) ? (double) count / total : PM[itemIdx][pos];

                // 1. Application du Learning Rate (Alpha)
                double newProb = (1.0 - config.getLearningRate()) * PM[itemIdx][pos]
                        + config.getLearningRate() * probability;

                newPM[itemIdx][pos] = newProb;
            }
        }

        return newPM;
    }

    /**
     * Affiche la matrice de probabilité PM de manière formatée.
     */
    private void printProbabilityMatrix(double[][] PM, List<Integer> items) {
        if (PM == null || PM.length == 0) {
            System.out.println("Matrice PM vide.");
            return;
        }

        int numItems = PM.length;
        int maxLength = PM[0].length;

        System.out.printf("Dimensions: %d items x %d positions\n", numItems, maxLength);

        // Afficher seulement les premières lignes et colonnes si la matrice est grande
        int maxRowsToShow = Math.min(10, numItems);
        int maxColsToShow = Math.min(10, maxLength);

        // En-tête avec les positions
        System.out.print("Item\\Pos\t");
        for (int j = 0; j < maxColsToShow; j++) {
            System.out.printf("P%d\t", j);
        }
        if (maxColsToShow < maxLength) {
            System.out.print("...");
        }
        System.out.println();

        // Ligne de séparation
        System.out.println("--------" + "--------".repeat(maxColsToShow));

        // Afficher les lignes
        for (int i = 0; i < maxRowsToShow; i++) {
            System.out.printf("Item %d\t", items.get(i));
            for (int j = 0; j < maxColsToShow; j++) {
                System.out.printf("%.2f\t", PM[i][j]);
            }
            if (maxColsToShow < maxLength) {
                System.out.print("...");
            }
            System.out.println();
        }

        if (maxRowsToShow < numItems) {
            System.out.println("...");
        }
        System.out.println();
    }

    @Override
    public String getName() {
        return "TKU-SP (Top-K Utility Sequential Patterns)";
    }

    @Override
    public long getRuntime() {
        return runtime;
    }

    @Override
    public double getMemoryUsage() {
        return memoryUsage;
    }
}