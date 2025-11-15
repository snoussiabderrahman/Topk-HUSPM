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

        // Séparer la partie principale et la partie SUtility si elle existe
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

        // prend la 1ère valeur numérique après "SUtility:"
        if (parts.length > 1) {
            String utilPart = parts[1].trim();
            if (!utilPart.isEmpty()) {
                try {
                    String numberStr = utilPart.split("\\s+")[0]; // prendre le premier token après SUtility:
                    int sUtil = Integer.parseInt(numberStr);
                    sequence.setUtility(sUtil);
                } catch (NumberFormatException ignored) {
                    // si parsing échoue, on laisse la Sequence calculer sa utilité plus tard
                }
            }
        }

        return sequence;
    }
}