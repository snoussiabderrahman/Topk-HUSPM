package algorithms;

import config.AlgorithmConfig;
import model.*;
import utils.DatasetStatistics;
import utils.UtilityCache;

import java.util.*;
import java.util.stream.Collectors;

public class TKUSP implements Algorithm {
    private long runtime;
    private double memoryUsage;
    private final UtilityCache cache;
    private final Random random;

    public TKUSP() {
        this.cache = new UtilityCache();
        this.random = new Random();
    }

    public TKUSP(long seed) {
        this.cache = new UtilityCache();
        this.random = new Random(seed);
    }

    @Override
    public List<Sequence> run(Dataset dataset, AlgorithmConfig config) {
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Obtenir les statistiques du dataset
        DatasetStatistics stats = new DatasetStatistics(dataset);
        stats.printStatistics();

        List<Integer> items = new ArrayList<>(stats.getDistinctItems());
        Collections.sort(items);

        // Initialiser la matrice de probabilité PM
        double[][] PM = initializeProbabilityMatrix(
                items.size(),
                config.getMaxSequenceLength()
        );

        List<Sequence> topK = new ArrayList<>();
        int iteration = 1;

        System.out.println("\n=== Starting TKU-SP Algorithm ===");
        System.out.println(config);
        System.out.println("Items: " + items);

        while (iteration <= config.getMaxIterations() && !isBinaryMatrix(PM)) {
            System.out.printf("\n--- Iteration %d ---\n", iteration);

            // Générer l'échantillon
            List<Sequence> sample = generateSample(
                    PM,
                    config.getSampleSize(),
                    items,
                    stats,
                    config.getMaxSequenceLength()
            );

            // Calculer les utilités (avec cache)
            for (Sequence seq : sample) {
                cache.getUtility(seq);
                //System.out.printf("\nsequence : %s",seq.toString());
            }


            // Trier par utilité décroissante
            sample.sort((s1, s2) -> Integer.compare(s2.getUtility(), s1.getUtility()));

            // Sélectionner l'élite
            int eliteSize = Math.max(1, (int) Math.ceil(config.getRho() * sample.size()));
            List<Sequence> elite = sample.subList(0, eliteSize);

            System.out.printf("Sample size: %d, Elite size: %d\n", sample.size(), elite.size());
            System.out.printf("Best utility: %d, Worst elite: %d\n",
                    elite.get(0).getUtility(), elite.get(elite.size()-1).getUtility());

            // Mettre à jour top-k
            topK = updateTopK(topK, sample, config.getK());

            // Mettre à jour la matrice de probabilité
            PM = updateProbabilityMatrix(PM, elite, items);

            iteration++;
        }

        long endTime = System.currentTimeMillis();
        this.runtime = endTime - startTime;

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        this.memoryUsage = (memoryAfter - memoryBefore) / (1024.0 * 1024.0);

        System.out.println("\n=== Algorithm Completed ===");
        System.out.printf("Total iterations: %d\n", iteration - 1);
        System.out.printf("Runtime: %.2f s\n", this.runtime/1000.0);
        System.out.printf("Memory: %.2f MB\n", this.memoryUsage);
        cache.printStatistics();

        return topK;
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

    private List<Sequence> generateSample(double[][] PM, int N, List<Integer> items,
                                          DatasetStatistics stats, int maxLength) {
        List<Sequence> sample = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            // Tirer une longueur de séquence uniformément
            int seqLength = 1 + random.nextInt(maxLength);
            //System.out.println(seqLength);

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

    private Itemset generateItemset(double[][] PM, List<Integer> items,
                                    int position, DatasetStatistics stats) {
        List<Item> chosenItems = new ArrayList<>();

        // Pour chaque item, tirer selon PM[item][position]
        for (int itemIdx = 0; itemIdx < items.size(); itemIdx++) {
            if (random.nextDouble() < PM[itemIdx][position]) {
                int itemId = items.get(itemIdx);
                // Générer une utilité aléatoire (simplifié ici)
                int utility = 1 + random.nextInt(50);
                chosenItems.add(new Item(itemId, utility));
            }
        }

        // Limiter la taille selon la distribution empirique
        if (!chosenItems.isEmpty()) {
            int maxElemSize = stats.sampleItemsetSize(random);

            if (chosenItems.size() > maxElemSize) {
                // Réduire aléatoirement
                Collections.shuffle(chosenItems, random);
                chosenItems = chosenItems.subList(0, maxElemSize);
            }
        }

        // Fallback si aucun item n'est choisi
        if (chosenItems.isEmpty()) {
            chosenItems.add(fallbackItem(PM, items, position));
        }

        return new Itemset(chosenItems);
    }

    private Item fallbackItem(double[][] PM, List<Integer> items, int position) {
        // Normaliser les probabilités pour cette position
        double sum = 0.0;
        for (int i = 0; i < items.size(); i++) {
            sum += PM[i][position];
        }

        if (sum == 0.0) {
            // Choisir un item au hasard
            int itemId = items.get(random.nextInt(items.size()));
            return new Item(itemId, 1 + random.nextInt(50));
        }

        // Tirer proportionnellement aux probabilités
        double r = random.nextDouble() * sum;
        double cumulative = 0.0;

        for (int i = 0; i < items.size(); i++) {
            cumulative += PM[i][position];
            if (cumulative >= r) {
                int itemId = items.get(i);
                return new Item(itemId, 1 + random.nextInt(50));
            }
        }

        // Par défaut
        int itemId = items.get(0);
        return new Item(itemId, 1 + random.nextInt(50));
    }

    private List<Sequence> updateTopK(List<Sequence> currentTopK,
                                      List<Sequence> sample, int k) {
        // Combiner currentTopK et sample
        Set<String> seen = new HashSet<>();
        List<Sequence> combined = new ArrayList<>();

        for (Sequence seq : currentTopK) {
            String sig = seq.getSignature();
            if (!seen.contains(sig)) {
                combined.add(seq);
                seen.add(sig);
            }
        }

        for (Sequence seq : sample) {
            String sig = seq.getSignature();
            if (!seen.contains(sig)) {
                combined.add(seq);
                seen.add(sig);
            }
        }

        // Trier et garder top-k
        combined.sort((s1, s2) -> Integer.compare(s2.getUtility(), s1.getUtility()));

        return combined.stream()
                .limit(k)
                .collect(Collectors.toList());
    }

    private double[][] updateProbabilityMatrix(double[][] PM, List<Sequence> elite, List<Integer> items) {
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
                if (total > 0) {
                    newPM[itemIdx][pos] = (double) count / total;
                } else {
                    newPM[itemIdx][pos] = PM[itemIdx][pos];
                }
            }
        }

        return newPM;
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