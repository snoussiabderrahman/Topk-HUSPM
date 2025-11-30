package experiments;

import algorithms.Algorithm;
import algorithms.TKUSP;
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

    private static final int[] SAMPLE_SIZES = { 500, 1000, 2000, 3000, 5000 };
    private static final double RHO = 0.2;
    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_SEQUENCE_LENGTH = 10;
    private static final String JSON_OUTPUT_DIR = "filesJSON";

    public static void main(String[] args) {
        // Default values
        String datasetPath = "data/Yoochoose.txt";
        int k = 40;

        // Override with command-line arguments if provided
        if (args.length >= 2) {
            datasetPath = args[0];
            k = Integer.parseInt(args[1]);
        } else if (args.length == 0) {
            System.out.println("No arguments provided, using defaults:");
            System.out.println("  Dataset: " + datasetPath);
            System.out.println("  K: " + k);
            System.out.println();
        } else {
            System.err.println("Usage: java experiments.ExperimentRunner <dataset_path> <k>");
            System.err.println("Example: java experiments.ExperimentRunner data/SIGN.txt 100");
            System.exit(1);
        }

        // Extract dataset name from path (e.g., "data/SIGN.txt" -> "SIGN")
        String datasetName = extractDatasetName(datasetPath);

        System.out.println("=== Experiment Runner ===");
        System.out.println("Dataset: " + datasetName);
        System.out.println("K: " + k);
        System.out.println("Sample sizes: " + Arrays.toString(SAMPLE_SIZES));
        System.out.println();

        try {
            runExperiments(datasetPath, datasetName, k);
        } catch (Exception e) {
            System.err.println("Error running experiments: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runExperiments(String datasetPath, String datasetName, int k) throws IOException {
        // Load dataset once
        System.out.println("Loading dataset from: " + datasetPath);
        Dataset dataset = DatasetReader.readDataset(datasetPath);
        System.out.println("Dataset loaded: " + dataset);
        System.out.println();

        // Load exact patterns for accuracy comparison
        String exactPatternsPath = "output_exacts/" + datasetName + "_" + k + ".txt";
        Set<String> exactPatterns = parseExactPatterns(exactPatternsPath);
        System.out.println("Loaded " + exactPatterns.size() + " exact patterns from: " + exactPatternsPath);
        System.out.println();

        // Store results
        List<AvgUtilityResult> avgUtilityResults = new ArrayList<>();
        List<AccuracyResult> accuracyResults = new ArrayList<>();
        List<RuntimeResult> runtimeResults = new ArrayList<>();

        // Run TKUSP for each sample size
        for (int N : SAMPLE_SIZES) {
            System.out.println("Running TKUSP with N=" + N + "...");

            AlgorithmConfig config = new AlgorithmConfig(k, N, RHO, MAX_ITERATIONS, MAX_SEQUENCE_LENGTH);
            Algorithm algorithm = new TKUSP(42); // Fixed seed for reproducibility
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

        // Create JSON output directory if it doesn't exist
        File jsonDir = new File(JSON_OUTPUT_DIR);
        if (!jsonDir.exists()) {
            jsonDir.mkdirs();
            System.out.println("Created directory: " + JSON_OUTPUT_DIR);
        }

        // Write JSON files
        String avgUtilityFile = JSON_OUTPUT_DIR + "/" + datasetName + "_avgUtil_" + k + ".json";
        String accuracyFile = JSON_OUTPUT_DIR + "/" + datasetName + "_accuracy_" + k + ".json";
        String runtimeFile = JSON_OUTPUT_DIR + "/" + datasetName + "_runtime_" + k + ".json";

        writeAvgUtilityJSON(avgUtilityResults, avgUtilityFile);
        writeAccuracyJSON(accuracyResults, accuracyFile);
        writeRuntimeJSON(runtimeResults, runtimeFile);

        System.out.println("Results saved to:");
        System.out.println("  - " + avgUtilityFile);
        System.out.println("  - " + accuracyFile);
        System.out.println("  - " + runtimeFile);
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
