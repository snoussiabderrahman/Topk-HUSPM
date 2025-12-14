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
    private static final int[] K_VALUES = {10, 50,100,200,300,400,500};
    private static final double RHO = 0.3;
    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_SEQUENCE_LENGTH = 10;
    private static final String JSON_OUTPUT_DIR = "filesJSON7";

    public static void main(String[] args) {
        // Datasets to process
        String[] datasets = new String[] { "SIGN"};

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

            // Store results
            List<AvgUtilityResult> avgUtilityResults = new ArrayList<>();
            List<AccuracyResult> accuracyResults = new ArrayList<>();
            List<RuntimeResult> runtimeResults = new ArrayList<>();

            for (int k : K_VALUES) {
                System.out.println("--------------------------------------------------");
                System.out.println("Running experiment for k = " + k);
                System.out.println("--------------------------------------------------");

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
                avgUtilityResults.add(new AvgUtilityResult(k, avgUtility));

                int accuracy = calculateAccuracy(topK, exactPatterns);
                accuracyResults.add(new AccuracyResult(k, accuracy));

                double runtimeSeconds = algorithm.getRuntime() / 1000.0;
                runtimeResults.add(new RuntimeResult(k, runtimeSeconds));
            }

            // Write JSON files
            String baseDir = JSON_OUTPUT_DIR + "/" + datasetName;
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
