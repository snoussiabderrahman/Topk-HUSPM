import algorithms.Algorithm;
import config.AlgorithmConfig;
import model.Dataset;
import model.Sequence;
import utils.DatasetReader;
import utils.OutputWriter;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runner CLI:
 * - Version selection: -V <num> ou -v <num>
 * 0 -> TKUSP
 * 1 -> TKUSP_V1
 * - Si --algo <ClassName> est fourni, il prend la priorité.
 * - Seed par défaut = 42 (si non fourni avec -s ou --seed).
 */
public class Main {
    public static void main(String[] args) {
        try {
            Map<String, String> opts = parseArgs(args);

            // paramètres principaux (valeurs par défaut)
            String datasetPath = opts.getOrDefault("dataset", "data/BIBLE.txt");
            int k = parseInt(opts.get("k"), 100);
            int N = parseInt(opts.get("N"), 2000);
            double rho = parseDouble(opts.get("rho"), 0.3);
            int maxIter = parseInt(opts.get("maxIter"), 100);
            int maxLen = parseInt(opts.get("maxLen"), 10);

            // learning rate / bounds (optionnel)
            String alphaS = opts.get("alpha");

            // seed: DEFAULT = 42 si non fourni
            long seed = parseLong(opts.get("seed"), 42L);

            // Déterminer la classe de l'algorithme
            String algoClass;
            if (opts.containsKey("algo")) {
                algoClass = opts.get("algo");
            } else if (opts.containsKey("version")) {
                String v = opts.get("version");
                if ("0".equals(v)) {
                    algoClass = "TKUSP";
                } else if ("1".equals(v)) {
                    algoClass = "TKUSP_V2";
                } else {
                    System.err.println(
                            "Version inconnue '" + v + "'. Utilisation de TKUSP_V1 par défaut.");
                    algoClass = "TKUSP_V2";
                }
            } else {
                // par défaut
                algoClass = "TKUSP_V4";
            }

            // Construire la configuration
            AlgorithmConfig config;
            if (alphaS != null) {
                double alpha = parseDouble(alphaS, 0.5);
                // Utiliser les valeurs par défaut pour les paramètres de convergence
                config = new AlgorithmConfig(k, N, rho, maxIter, maxLen, alpha, 10);
            } else {
                config = new AlgorithmConfig(k, N, rho, maxIter, maxLen);
            }

            Dataset dataset = DatasetReader.readDataset(datasetPath);

            Algorithm algorithm = createAlgorithmInstance(algoClass, seed);

            List<Sequence> topK = algorithm.run(dataset, config);

            // Extract dataset name from path
            String datasetName = new File(datasetPath).getName().replace(".txt", "");

            System.out.println("======== DB : " + datasetName + " ========");
            System.out.println("✅ k = " + config.getK());
            System.out.printf("Runtime = %.2f s\n", algorithm.getRuntime() / 1000.0);
            System.out.printf("memory = %.2f MB\n", algorithm.getMemoryUsage());
            System.out.println("================================");

            String outputPath = opts.getOrDefault("output", "output/BIBLE.txt");
            File outFile = new File(outputPath);
            File parent = outFile.getParentFile();
            if (parent != null)
                parent.mkdirs();

            OutputWriter.writeResults(topK, outputPath, algorithm.getRuntime(),
                    algorithm.getMemoryUsage());
            // System.out.println("Results saved to: " + outputPath);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("\nExemple d'utilisation:");
            System.err.println("  java -jar tkhuspm.jar -V 1 -d data/BIBLE.txt -k 100 -N 2000");
            System.exit(1);
        }
    }

    private static Algorithm createAlgorithmInstance(String algoClass, long seed)
            throws Exception {
        Class<?> cls = Class.forName("algorithms." + algoClass);
        try {
            // essaie le constructeur (long)
            return (Algorithm) cls.getConstructor(long.class).newInstance(seed);
        } catch (NoSuchMethodException e) {
            // fallback au constructeur par défaut
            return (Algorithm) cls.getConstructor().newInstance();
        }
    }

    /**
     * Parse simple des arguments :
     * - supporte --long value, --long=value, -x value, -xvalue (ex: -V1)
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq > 2) {
                    String key = a.substring(2, eq);
                    String val = a.substring(eq + 1);
                    map.put(key, val);
                } else {
                    String key = a.substring(2);
                    String val = (i + 1 < args.length && !args[i + 1].startsWith("-"))
                            ? args[++i]
                            : "true";
                    map.put(key, val);
                }
            } else if (a.startsWith("-")) {
                if (a.length() == 2) {
                    char c = a.charAt(1);
                    String key = shortOptToKey(c);
                    String val = (i + 1 < args.length && !args[i + 1].startsWith("-"))
                            ? args[++i]
                            : "true";
                    map.put(key, val);
                } else {
                    // ex: -V1 ou -d/path (valeur collée)
                    char c = a.charAt(1);
                    String key = shortOptToKey(c);
                    String val = a.substring(2);
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    private static String shortOptToKey(char c) {
        switch (c) {
            case 'V':
            case 'v':
                return "version";
            case 's':
                return "seed";
            case 'd':
                return "dataset";
            case 'k':
                return "k";
            case 'N':
                return "N";
            case 'r':
                return "rho";
            case 'i':
            case 'I':
                return "maxIter";
            case 'l':
            case 'L':
                return "maxLen";
            case 'o':
            case 'O':
                return "output";
            default:
                return String.valueOf(c);
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null)
            return def;
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseLong(String s, long def) {
        if (s == null)
            return def;
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        if (s == null)
            return def;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return def;
        }
    }
}