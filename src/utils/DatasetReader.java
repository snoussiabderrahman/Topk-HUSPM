package utils;

import model.Dataset;
import model.Item;
import model.Itemset;
import model.Sequence;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DatasetReader {

    public static Dataset readDataset(String filePath) throws IOException {
        List<Sequence> sequences = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                Sequence sequence = parseLine(line);
                if (!sequence.isEmpty()) {
                    sequences.add(sequence);
                }
            }
        }

        return new Dataset(sequences);
    }

    private static Sequence parseLine(String line) {
        Sequence sequence = new Sequence();
        Itemset currentItemset = new Itemset();

        // Supprimer la partie SUtility si présente
        String[] parts = line.split("SUtility:");
        String data = parts[0].trim();

        String[] tokens = data.split("\\s+");

        for (String token : tokens) {
            if (token.equals("-1")) {
                // Fin d'un itemset
                if (!currentItemset.isEmpty()) {
                    sequence.addItemset(currentItemset);
                    currentItemset = new Itemset();
                }
            } else if (token.equals("-2")) {
                // Fin de séquence
                break;
            } else if (token.contains("[")) {
                // Format: item[utility]
                int itemId = Integer.parseInt(token.substring(0, token.indexOf('[')));
                int utility = Integer.parseInt(
                        token.substring(token.indexOf('[') + 1, token.indexOf(']'))
                );
                currentItemset.addItem(new Item(itemId, utility));
            }
        }

        return sequence;
    }
}