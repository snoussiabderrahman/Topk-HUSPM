package utils;

import model.Sequence;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class OutputWriter {

    public static void writeTopK(List<Sequence> topK, String outputPath)
            throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write("=== Top-" + topK.size() + " High Utility Sequential Patterns ===\n");
            writer.write("\n");

            for (int i = 0; i < topK.size(); i++) {
                Sequence seq = topK.get(i);
                writer.write(String.format("Rank %d: %s\n",
                        i + 1, seq.toCompactString()));
                writer.write(seq.toString() + "\n");
                writer.write("\n");
            }
        }
    }

    public static void writeResults(List<Sequence> topK, String outputPath,
                                    long runtime, double memory) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write("=== Algorithm Results ===\n");
            writer.write(String.format("Runtime: %d ms\n", runtime));
            writer.write(String.format("Memory: %.2f MB\n", memory));
            writer.write(String.format("Top-K size: %d\n", topK.size()));
            writer.write("\n");
            writer.write("=== Top-K Patterns ===\n");

            for (int i = 0; i < topK.size(); i++) {
                Sequence seq = topK.get(i);
                writer.write(String.format("\n[%d] Utility: %d\n",
                        i + 1, seq.getUtility()));
                writer.write(seq.toString() + "\n");
            }
        }
    }
}