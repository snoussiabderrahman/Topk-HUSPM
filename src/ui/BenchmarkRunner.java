package ui;

import algorithms.Algorithm;
import config.AlgorithmConfig;
import javafx.concurrent.Task;
import model.Dataset;
import model.Sequence;
import utils.DatasetReader;
import utils.UtilityCalculator;
import utils.OutputWriter;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A JavaFX Task that runs the benchmark sequentially and writes results.
 */
public class BenchmarkRunner extends Task<Void> {
    private final List<Class<? extends Algorithm>> algorithmClasses;
    private final List<File> datasetFiles;
    private final List<Integer> ks;
    private final File outputRoot;
    private final int sampleSize;
    private final double rho;
    private final int maxIterations;
    private final int maxSequenceLength;
    private final long seed;

    public BenchmarkRunner(List<Class<? extends Algorithm>> algorithmClasses,
                           List<File> datasetFiles,
                           List<Integer> ks,
                           File outputRoot,
                           int sampleSize,
                           double rho,
                           int maxIterations,
                           int maxSequenceLength,
                           long seed) {
        this.algorithmClasses = algorithmClasses;
        this.datasetFiles = datasetFiles;
        this.ks = ks;
        this.outputRoot = outputRoot;
        this.sampleSize = sampleSize;
        this.rho = rho;
        this.maxIterations = maxIterations;
        this.maxSequenceLength = maxSequenceLength;
        this.seed = seed;
    }

    @Override
    protected Void call() throws Exception {
        int total = algorithmClasses.size() * datasetFiles.size() * ks.size();
        int done = 0;

        // Create output root
        Files.createDirectories(outputRoot.toPath());

        // Also write a CSV summary
        File summaryCsv = new File(outputRoot, "summary.csv");
        try (BufferedWriter summaryWriter = new BufferedWriter(new FileWriter(summaryCsv))) {
            summaryWriter.write("algorithm,dataset,k,runtime_ms,memory_mb,status\n");

            for (Class<? extends Algorithm> algoClass : algorithmClasses) {
                if (isCancelled()) break;

                // instantiate only to get algorithm name
                Algorithm tmp = AlgorithmFactory.createInstance(algoClass, seed);
                String algoName = tmp.getName();

                for (File datasetFile : datasetFiles) {
                    if (isCancelled()) break;
                    String datasetName = datasetFile.getName();
                    String datasetDirName = datasetName;
                    if (datasetName.contains(".")) {
                        datasetDirName = datasetName.substring(0, datasetName.lastIndexOf('.'));
                    }

                    for (int k : ks) {
                        if (isCancelled()) break;
                        updateMessage(String.format("Running %s on %s (k=%d)", algoName, datasetName, k));

                        try {
                            // ensure static caches cleared (UtilityCalculator has static cache)
                            UtilityCalculator.clearCache();

                            // read dataset
                            Dataset dataset = DatasetReader.readDataset(datasetFile.getAbsolutePath());

                            // create new instance
                            Algorithm algorithm = AlgorithmFactory.createInstance(algoClass, seed);

                            AlgorithmConfig config = new AlgorithmConfig(k, sampleSize, rho, maxIterations, maxSequenceLength);

                            long runStart = System.currentTimeMillis();
                            List<Sequence> topK = algorithm.run(dataset, config); // blocking
                            long runEnd = System.currentTimeMillis();

                            long runtimeMs = algorithm.getRuntime();
                            double memoryMb = algorithm.getMemoryUsage();

                            // create folder structure: outputRoot/AlgorithmName/DatasetName/k_<k>.txt
                            File algoDir = new File(outputRoot, sanitizeFilename(algoName));
                            File dsDir = new File(algoDir, sanitizeFilename(datasetDirName));
                            Files.createDirectories(dsDir.toPath());

                            File outFile = new File(dsDir, "k_" + k + ".txt");

                            // Use OutputWriter to write results (it writes runtime/memory/top-k)
                            try {
                                OutputWriter.writeResults(topK, outFile.getAbsolutePath(), runtimeMs, memoryMb);
                            } catch (Exception e) {
                                // fallback: write basic info
                                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile))) {
                                    writer.write(String.format("Algorithm: %s\n", algoName));
                                    writer.write(String.format("Dataset: %s\n", datasetName));
                                    writer.write(String.format("k: %d\n", k));
                                    writer.write(String.format("Runtime(ms): %d\n", runtimeMs));
                                    writer.write(String.format("Memory(MB): %.2f\n", memoryMb));
                                    writer.write("\nTop-K patterns:\n");
                                    if (topK != null) {
                                        for (Sequence s : topK) writer.write(s.toString() + "\n");
                                    }
                                }
                            }

                            // append to summary csv
                            summaryWriter.write(String.format("%s,%s,%d,%d,%.2f,OK\n",
                                    csvEscape(algoName), csvEscape(datasetName), k, runtimeMs, memoryMb));
                            summaryWriter.flush();

                            // publish result to UI via updateValue / updateMessage or custom listener
                            // (we use message/progress; actual UI code can also read the produced files)

                        } catch (Throwable th) {
                            // on error, log and continue
                            String err = th.getMessage() == null ? th.toString() : th.getMessage();
                            summaryWriter.write(String.format("%s,%s,%d,0,0,ERROR: %s\n",
                                    csvEscape(algoClass.getSimpleName()), csvEscape(datasetFile.getName()), k, err));
                            summaryWriter.flush();
                        }

                        done++;
                        updateProgress(done, total);
                    }
                }
            }
        }

        updateMessage("Completed");
        updateProgress(1, 1);
        return null;
    }

    private static String sanitizeFilename(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String csvEscape(String s) {
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        } else {
            return s;
        }
    }
}