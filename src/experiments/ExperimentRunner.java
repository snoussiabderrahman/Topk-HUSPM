package experiments;

import algorithms.*;
import config.AlgorithmConfig;
import model.Dataset;
import model.Sequence;
import utils.DatasetReader;

import java.io.*;
import java.util.*;

/**
 * ExperimentRunner executes TKUSP with fixed sample size (N=2000)
 * and varying k values, generating JSON files for:
 * 1. Average utility (avgUtil.json)
 * 2. Accuracy (acc.json)
 * 3. Runtime (runtime.json)
 */
public class ExperimentRunner {

    private static final int SAMPLE_SIZE = 2000;
    private static final int[] K_VALUES = { 1000 };
    private static final double RHO = 0.3;
    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_SEQUENCE_LENGTH = 10;
    private static final String JSON_OUTPUT_DIR = "filesJSON7";

    public static void main(String[] args) {
        // Datasets to process
        String[] datasets = new String[] { "SIGN" };

        for (String datasetName : datasets) {
            System.out.println("\n===============  " + datasetName + "  ===============");
            runExperimentsForDataset(datasetName);
        }
    }

    private static void runExperimentsForDataset(String datasetName) {
        String datasetPath = "data/" + datasetName + ".txt";

        try {
            // Load dataset
            Dataset dataset = DatasetReader.readDataset(datasetPath);

            // Load ALL existing results first (to preserve old k values)
            String baseDir = JSON_OUTPUT_DIR + "/" + datasetName;
            List<AvgUtilityResult> avgUtilityResults = new ArrayList<>();
            List<AccuracyResult> accuracyResults = new ArrayList<>();
            List<RuntimeResult> runtimeResults = new ArrayList<>();

            try {
                avgUtilityResults = loadAllAvgUtilityResults(baseDir + "/avgUtil.json");
                accuracyResults = loadAllAccuracyResults(baseDir + "/acc.json");
                runtimeResults = loadAllRuntimeResults(baseDir + "/runtime.json");

                if (!avgUtilityResults.isEmpty()) {
                    System.out.println("Loaded " + avgUtilityResults.size() + " existing results from JSON files.");
                }
            } catch (IOException e) {
                System.out.println("No existing results found. Starting fresh.");
            }

            // Create sets to track which k values we already have
            Set<Integer> existingKValues = new HashSet<>();
            for (AvgUtilityResult r : avgUtilityResults) {
                existingKValues.add(r.k);
            }

            for (int k : K_VALUES) {
                System.out.println("--------------------------------------------------");
                System.out.println("Running experiment for k = " + k);
                System.out.println("--------------------------------------------------");

                // Check if results already exist for this k value
                if (existingKValues.contains(k)) {
                    System.out.println("✓ Results already exist for " + datasetName + " with k=" + k + ". Skipping...");
                    continue; // Skip to next k value
                } else {
                    System.out.println(
                            "⚙ Results not found for " + datasetName + " with k=" + k + ". Running calculation...");
                }

                // Load exact patterns for accuracy comparison
                String exactPatternsPath = "output_exacts/" + datasetName + "/" + datasetName + "_" + k + ".txt";
                Set<String> exactPatterns = new HashSet<>();
                File exactFile = new File(exactPatternsPath);

                if (exactFile.exists()) {
                    exactPatterns = parseExactPatterns(exactPatternsPath);
                } else {
                    System.out.println("WARNING: Exact patterns file not found: " + exactPatternsPath);
                    System.out.println("Accuracy will be 0.");
                }

                // Run TKUSP
                AlgorithmConfig config = new AlgorithmConfig(k, SAMPLE_SIZE, RHO, MAX_ITERATIONS, MAX_SEQUENCE_LENGTH);
                Algorithm algorithm = new TKUSP_V7(42);
                List<Sequence> topK = algorithm.run(dataset, config);

                // Calculate metrics
                double avgUtility = calculateAverageUtility(topK);
                int accuracy = calculateAccuracy(topK, exactPatterns);
                double runtimeSeconds = algorithm.getRuntime() / 1000.0;

                // Add new results (or replace if k already existed)
                avgUtilityResults.removeIf(r -> r.k == k);
                accuracyResults.removeIf(r -> r.k == k);
                runtimeResults.removeIf(r -> r.k == k);

                avgUtilityResults.add(new AvgUtilityResult(k, avgUtility));
                accuracyResults.add(new AccuracyResult(k, accuracy));
                runtimeResults.add(new RuntimeResult(k, runtimeSeconds));

                System.out.println("  Calculated: avgUtility=" + avgUtility + ", accuracy=" + accuracy + ", runtime="
                        + runtimeSeconds + "s");
            }

            // Sort results by k value before writing to ensure consistent ordering
            avgUtilityResults.sort(Comparator.comparingInt(r -> r.k));
            accuracyResults.sort(Comparator.comparingInt(r -> r.k));
            runtimeResults.sort(Comparator.comparingInt(r -> r.k));

            // Write JSON files
            createDirectoryIfNotExists(baseDir);

            String avgUtilityFile = baseDir + "/avgUtil.json";
            String accuracyFile = baseDir + "/acc.json";
            String runtimeFile = baseDir + "/runtime.json";

            writeAvgUtilityJSON(avgUtilityResults, avgUtilityFile);
            writeAccuracyJSON(accuracyResults, accuracyFile);
            writeRuntimeJSON(runtimeResults, runtimeFile);

        } catch (Exception e) {
            System.err.println("Error running experiments for " + datasetName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDirectoryIfNotExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Load ALL existing average utility results from JSON file.
     */
    private static List<AvgUtilityResult> loadAllAvgUtilityResults(String filename) throws IOException {
        List<AvgUtilityResult> results = new ArrayList<>();
        if (!new File(filename).exists()) {
            return results;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("\"k\":")) {
                    // Extract k and avgUtility
                    int kIdx = line.indexOf("\"k\":");
                    int utilityIdx = line.indexOf("\"avgUtility\":");

                    if (kIdx != -1 && utilityIdx != -1) {
                        String kPart = line.substring(kIdx + 4).trim();
                        kPart = kPart.substring(0, kPart.indexOf(',')).trim();
                        int k = Integer.parseInt(kPart);

                        String utilityPart = line.substring(utilityIdx + 13).trim();
                        utilityPart = utilityPart.replaceAll("[},]", "").trim();
                        double avgUtility = Double.parseDouble(utilityPart);

                        results.add(new AvgUtilityResult(k, avgUtility));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Load ALL existing accuracy results from JSON file.
     */
    private static List<AccuracyResult> loadAllAccuracyResults(String filename) throws IOException {
        List<AccuracyResult> results = new ArrayList<>();
        if (!new File(filename).exists()) {
            return results;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("\"k\":")) {
                    // Extract k and accuracy
                    int kIdx = line.indexOf("\"k\":");
                    int accIdx = line.indexOf("\"accuracy\":");

                    if (kIdx != -1 && accIdx != -1) {
                        String kPart = line.substring(kIdx + 4).trim();
                        kPart = kPart.substring(0, kPart.indexOf(',')).trim();
                        int k = Integer.parseInt(kPart);

                        String accPart = line.substring(accIdx + 11).trim();
                        accPart = accPart.replaceAll("[},]", "").trim();
                        int accuracy = Integer.parseInt(accPart);

                        results.add(new AccuracyResult(k, accuracy));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Load ALL existing runtime results from JSON file.
     */
    private static List<RuntimeResult> loadAllRuntimeResults(String filename) throws IOException {
        List<RuntimeResult> results = new ArrayList<>();
        if (!new File(filename).exists()) {
            return results;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("\"k\":")) {
                    // Extract k and runtime
                    int kIdx = line.indexOf("\"k\":");
                    int runtimeIdx = line.indexOf("\"runtime\":");

                    if (kIdx != -1 && runtimeIdx != -1) {
                        String kPart = line.substring(kIdx + 4).trim();
                        kPart = kPart.substring(0, kPart.indexOf(',')).trim();
                        int k = Integer.parseInt(kPart);

                        String runtimePart = line.substring(runtimeIdx + 10).trim();
                        runtimePart = runtimePart.replaceAll("[},]", "").trim();
                        double runtime = Double.parseDouble(runtimePart);

                        results.add(new RuntimeResult(k, runtime));
                    }
                }
            }
        }
        return results;
    }

    /**
     * Parse exact patterns from output_exacts file.
     * Format: "1) 17 -1 143 #UTIL: 37800"
     * Returns a set of pattern signatures (normalized form).
     */
    private static Set<String> parseExactPatterns(String filePath) throws IOException {
        Set<String> patterns = new HashSet<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                // Extract pattern part (between ")" and "#UTIL:")
                int startIdx = line.indexOf(")");
                int endIdx = line.indexOf("#UTIL:");

                if (startIdx != -1 && endIdx != -1) {
                    String patternPart = line.substring(startIdx + 1, endIdx).trim();
                    // Normalize: convert "17 -1 143" to a signature
                    String signature = normalizePattern(patternPart);
                    patterns.add(signature);
                }
            }
        }

        return patterns;
    }

    /**
     * Normalize a pattern string to a canonical signature.
     * Input: "17 -1 143" or "17 -1 143 -1 245"
     * Output: normalized form that matches Sequence signature
     */
    private static String normalizePattern(String pattern) {
        // Split by -1 to get itemsets
        String[] itemsets = pattern.split("-1");

        List<String> normalizedItemsets = new ArrayList<>();
        for (String itemset : itemsets) {
            String trimmed = itemset.trim();
            if (!trimmed.isEmpty()) {
                // Split items and sort them within itemset
                String[] items = trimmed.split("\\s+");
                Arrays.sort(items);
                normalizedItemsets.add(String.join(",", items));
            }
        }

        return String.join("|", normalizedItemsets);
    }

    /**
     * Convert Sequence to normalized signature matching exact patterns.
     */
    private static String sequenceToSignature(Sequence seq) {
        // Use getSignature() method from Sequence class
        // This already produces a normalized form like "17|143"
        return seq.getSignature();
    }

    /**
     * Calculate average utility of patterns.
     */
    private static double calculateAverageUtility(List<Sequence> patterns) {
        if (patterns.isEmpty())
            return 0.0;

        double sum = 0.0;
        for (Sequence seq : patterns) {
            sum += seq.getUtility();
        }

        return sum / patterns.size();
    }

    /**
     * Calculate accuracy: number of patterns found in exact set (common patterns)
     */
    private static int calculateAccuracy(List<Sequence> foundPatterns, Set<String> exactPatterns) {
        int matchCount = 0;
        for (Sequence seq : foundPatterns) {
            String signature = sequenceToSignature(seq);
            if (exactPatterns.contains(signature)) {
                matchCount++;
            }
        }

        return matchCount;
    }

    /**
     * Write average utility results to JSON file.
     */
    private static void writeAvgUtilityJSON(List<AvgUtilityResult> results, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("[");
            for (int i = 0; i < results.size(); i++) {
                AvgUtilityResult r = results.get(i);
                writer.print("  {\"k\": " + r.k + ", \"avgUtility\": " + r.avgUtility + "}");
                if (i < results.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            writer.println("]");
        }
    }

    /**
     * Write accuracy results to JSON file.
     */
    private static void writeAccuracyJSON(List<AccuracyResult> results, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("[");
            for (int i = 0; i < results.size(); i++) {
                AccuracyResult r = results.get(i);
                writer.print("  {\"k\": " + r.k + ", \"accuracy\": " + r.accuracy + "}");
                if (i < results.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            writer.println("]");
        }
    }

    /**
     * Write runtime results to JSON file.
     */
    private static void writeRuntimeJSON(List<RuntimeResult> results, String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("[");
            for (int i = 0; i < results.size(); i++) {
                RuntimeResult r = results.get(i);
                writer.printf("  {\"k\": %d, \"runtime\": %.2f}", r.k, r.runtimeSeconds);
                if (i < results.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            writer.println("]");
        }
    }

    // Result data classes
    private static class AvgUtilityResult {
        int k;
        double avgUtility;

        AvgUtilityResult(int k, double avgUtility) {
            this.k = k;
            this.avgUtility = avgUtility;
        }
    }

    private static class AccuracyResult {
        int k;
        int accuracy;

        AccuracyResult(int k, int accuracy) {
            this.k = k;
            this.accuracy = accuracy;
        }
    }

    private static class RuntimeResult {
        int k;
        double runtimeSeconds;

        RuntimeResult(int k, double runtimeSeconds) {
            this.k = k;
            this.runtimeSeconds = runtimeSeconds;
        }
    }
}
