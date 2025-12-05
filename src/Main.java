import algorithms.*;
import config.AlgorithmConfig;
import model.Dataset;
import model.Sequence;
import utils.DatasetReader;
import utils.OutputWriter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {

            System.out.println("=== TK-HUSPM: Top-K HIGH Utility Sequential Pattern Mining ===\n");

            // 1. Lire le dataset
            String inputPath = "data/BIBLE.txt";
            System.out.println("Reading dataset from: " + inputPath);
            Dataset dataset = DatasetReader.readDataset(inputPath);
            System.out.println("Dataset loaded: " + dataset);
            System.out.println();

            // 2. Configuration de l'algorithme
            AlgorithmConfig config = new AlgorithmConfig(
                    100,      // k = 10 (top-10 patterns)
                    2000,     // N = 2000 (sample size)
                    0.3,     // rho = 0.2 (20% elite)
                    100,      // max_iter = 2000
                    10        // max_length_sequence = 10
            );

            // 3. Exécuter l'algorithme
            Algorithm algorithm = new TKUSP_V1(42); // seed pour reproductibilité
            List<Sequence> topK = algorithm.run(dataset, config);

            // 4. Afficher les résultats
            System.out.println("\n=== Results ===");
            System.out.println("Algorithm: " + algorithm.getName());
            System.out.println("Top-K patterns found: " + topK.size());
            System.out.println();

            for (int i = 0; i < topK.size(); i++) {
                Sequence seq = topK.get(i);
                System.out.printf("[%d] Utility: %d - %s\n",
                        i + 1, seq.getUtility(), seq.toCompactString());
            }

            // 5. Sauvegarder les résultats
            String outputPath = "output/SIGN.txt";
            OutputWriter.writeResults(
                    topK,
                    outputPath,
                    algorithm.getRuntime(),
                    algorithm.getMemoryUsage()
            );
            System.out.println("\nResults saved to: " + outputPath);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}