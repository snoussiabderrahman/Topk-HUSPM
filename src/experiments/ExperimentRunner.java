package experiments;

import algorithms.Algorithm;
import algorithms.TKUSP;
import algorithms.TKUSP_V1;
import config.AlgorithmConfig;
import model.Dataset;
import model.Sequence;
import utils.DatasetReader;

import java.io.*;
import java.util.*;

/**
 * ExperimentRunner executes TKUSP with varying sample sizes (N)
 * and generates JSON files containing:
 * 1. Average utility for each N
 * 2. Accuracy compared to exact results for each N
 */
public class ExperimentRunner {

    private static final int[] SAMPLE_SIZES = { 200, 500,1000,2000,5000};
    private static final int[] K_VALUES = { 10, 50,100,500,1000};
    private static final double RHO = 0.3;
    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_SEQUENCE_LENGTH = 10;
    private static final String JSON_OUTPUT_DIR = "filesJSON";

    public static void main(String[] args) {
        // Default values
        String datasetPath = "data/BIBLE.txt";

        // Override with command-line arguments if provided
        if (args.length >= 1) {
            datasetPath = args[0];
        } else if (args.length == 0) {
            System.out.println("No arguments provided, using default dataset:");
            System.out.println("  Dataset: " + datasetPath);
            System.out.println();
        }

        // Extract dataset name from path (e.g., "data/SIGN.txt" -> "SIGN")
        String datasetName = extractDatasetName(datasetPath);

        System.out.println("=== Experiment Runner ===");
        System.out.println("Dataset: " + datasetName);
        System.out.println("Sample sizes: " + Arrays.toString(SAMPLE_SIZES));
        System.out.println("K values: " + Arrays.toString(K_VALUES));
        System.out.println();

        try {
            // Load dataset once
            System.out.println("Loading dataset from: " + datasetPath);
            Dataset dataset = DatasetReader.readDataset(datasetPath);
            System.out.println("Dataset loaded: " + dataset);
            System.out.println();

            for (int k : K_VALUES) {
                System.out.println("\n--------------------------------------------------");
                System.out.println("Starting experiments for k = " + k);
                System.out.println("--------------------------------------------------");
                runExperimentsForK(dataset, datasetName, k);
            }

        } catch (Exception e) {
            System.err.println("Error running experiments: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runExperimentsForK(Dataset dataset, String datasetName, int k) throws IOException {
        // Load exact patterns for accuracy comparison
        String exactPatternsPath = "output_exacts/" + datasetName + "/" + datasetName + "_" + k + ".txt";
        Set<String> exactPatterns = new HashSet<>();
        File exactFile = new File(exactPatternsPath);

        if (exactFile.exists()) {
            exactPatterns = parseExactPatterns(exactPatternsPath);
            System.out.println("Loaded " + exactPatterns.size() + " exact patterns from: " + exactPatternsPath);
        } else {
            System.out.println("WARNING: Exact patterns file not found: " + exactPatternsPath);
            System.out.println("Accuracy will be 0 for all runs.");
        }
        System.out.println();

        // Store results
        List<AvgUtilityResult> avgUtilityResults = new ArrayList<>();
        List<AccuracyResult> accuracyResults = new ArrayList<>();
        List<RuntimeResult> runtimeResults = new ArrayList<>();

        // Run TKUSP for each sample size
        for (int N : SAMPLE_SIZES) {
            System.out.println("Running TKUSP with N=" + N + ", k=" + k + "...");

            // Note: Using 5-parameter constructor as per recent changes
            AlgorithmConfig config = new AlgorithmConfig(k, N, RHO, MAX_ITERATIONS, MAX_SEQUENCE_LENGTH);

            // Re-initialize algorithm for each run to ensure clean state
            Algorithm algorithm = new TKUSP_V1(42); // Use TKUSP_V1 as requested
            List<Sequence> topK = algorithm.run(dataset, config);

            // Calculate average utility
            double avgUtility = calculateAverageUtility(topK);
            avgUtilityResults.add(new AvgUtilityResult(N, avgUtility));
            System.out.println("  Average Utility: " + avgUtility);

            // Calculate accuracy (number of common patterns)
            int accuracy = calculateAccuracy(topK, exactPatterns);
            accuracyResults.add(new AccuracyResult(N, accuracy));
            System.out.println("  Accuracy (common patterns): " + accuracy);

            // Get runtime in seconds
            double runtimeSeconds = algorithm.getRuntime() / 1000.0;
            runtimeResults.add(new RuntimeResult(N, runtimeSeconds));
            System.out.printf("  Runtime: %.2f s%n", runtimeSeconds);
            System.out.println();
        }

        // Create directory structure: filesJSON/<dataset>/{accuracy, runtime, avgUtil}
        String baseDir = JSON_OUTPUT_DIR + "/" + datasetName;
        createDirectoryIfNotExists(baseDir + "/accuracy");
        createDirectoryIfNotExists(baseDir + "/runtime");
        createDirectoryIfNotExists(baseDir + "/avgUtil");

        // Write JSON files
        String avgUtilityFile = baseDir + "/avgUtil/k_" + k + ".json";
        String accuracyFile = baseDir + "/accuracy/k_" + k + ".json";
        String runtimeFile = baseDir + "/runtime/k_" + k + ".json";

        writeAvgUtilityJSON(avgUtilityResults, avgUtilityFile);
        writeAccuracyJSON(accuracyResults, accuracyFile);
        writeRuntimeJSON(runtimeResults, runtimeFile);

        System.out.println("Results for k=" + k + " saved to:");
        System.out.println("  - " + avgUtilityFile);
        System.out.println("  - " + accuracyFile);
        System.out.println("  - " + runtimeFile);
    }

    private static void createDirectoryIfNotExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
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
                writer.print("  {\"N\": " + r.N + ", \"avgUtility\": " + r.avgUtility + "}");
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
                writer.print("  {\"N\": " + r.N + ", \"accuracy\": " + r.accuracy + "}");
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
                writer.printf("  {\"N\": %d, \"runtime\": %.2f}", r.N, r.runtimeSeconds);
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
     * Extract dataset name from file path.
     * Example: "data/SIGN.txt" -> "SIGN"
     */
    private static String extractDatasetName(String path) {
        File f = new File(path);
        String name = f.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            return name.substring(0, dotIndex);
        }
        return name;
    }

    // Result data classes
    private static class AvgUtilityResult {
        int N;
        double avgUtility;

        AvgUtilityResult(int N, double avgUtility) {
            this.N = N;
            this.avgUtility = avgUtility;
        }
    }

    private static class AccuracyResult {
        int N;
        int accuracy;

        AccuracyResult(int N, int accuracy) {
            this.N = N;
            this.accuracy = accuracy;
        }
    }

    private static class RuntimeResult {
        int N;
        double runtimeSeconds;

        RuntimeResult(int N, double runtimeSeconds) {
            this.N = N;
            this.runtimeSeconds = runtimeSeconds;
        }
    }
}
