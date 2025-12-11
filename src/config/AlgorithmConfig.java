package config;

public class AlgorithmConfig {
    private final int k;
    private final int sampleSize; // N
    private final double rho;
    private final int maxIterations;
    private final int maxSequenceLength;
    private final double learningRate;
    private final int maxStagnationIterations;

    // ⭐ NOUVEAUX PARAMÈTRES POUR SAMPLING ADAPTATIF
    private final boolean enableAdaptiveSampling; // Activer/désactiver l'adaptation
    private final double minDiversityThreshold;   // Seuil minimal de diversité (0.0-1.0)
    private final double sampleReductionFactor;   // Facteur de réduction de N (ex: 0.5 = divise par 2)
    private final int warmupIterations;          // Nombre d'itérations avant adaptation

    // Constructeur par défaut (inchangé)
    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength) {
        this(k, sampleSize, rho, maxIterations, maxSequenceLength,
                0.2, 5, true, 0.7, 0.5, 10);
    }

    // Constructeur avec convergence control (inchangé)
    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength,
                           double learningRate, int maxStagnationIterations) {
        this(k, sampleSize, rho, maxIterations, maxSequenceLength,
                learningRate, maxStagnationIterations, true, 0.7, 0.5, 10);
    }

    // ⭐ NOUVEAU CONSTRUCTEUR COMPLET
    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength,
                           double learningRate, int maxStagnationIterations,
                           boolean enableAdaptiveSampling, double minDiversityThreshold,
                           double sampleReductionFactor, int warmupIterations) {
        this.k = k;
        this.sampleSize = sampleSize;
        this.rho = rho;
        this.maxIterations = maxIterations;
        this.maxSequenceLength = maxSequenceLength;
        this.learningRate = learningRate;
        this.maxStagnationIterations = maxStagnationIterations;
        this.enableAdaptiveSampling = enableAdaptiveSampling;
        this.minDiversityThreshold = minDiversityThreshold;
        this.sampleReductionFactor = sampleReductionFactor;
        this.warmupIterations = warmupIterations;
    }

    // Getters existants (inchangés)
    public int getK() { return k; }
    public int getSampleSize() { return sampleSize; }
    public double getRho() { return rho; }
    public int getMaxIterations() { return maxIterations; }
    public int getMaxSequenceLength() { return maxSequenceLength; }
    public double getLearningRate() { return learningRate; }
    public int getMaxStagnationIterations() { return maxStagnationIterations; }

    // ⭐ NOUVEAUX GETTERS
    public boolean isAdaptiveSamplingEnabled() { return enableAdaptiveSampling; }
    public double getMinDiversityThreshold() { return minDiversityThreshold; }
    public double getSampleReductionFactor() { return sampleReductionFactor; }
    public int getWarmupIterations() { return warmupIterations; }

    @Override
    public String toString() {
        return String.format(
                "Config{k=%d, N=%d, rho=%.2f, max_iter=%d, max_seq_len=%d, alpha=%.2f, maxStag=%d, " +
                        "adaptive=%b, diversity=%.2f, reduction=%.2f, warmup=%d}",
                k, sampleSize, rho, maxIterations, maxSequenceLength,
                learningRate, maxStagnationIterations,
                enableAdaptiveSampling, minDiversityThreshold, sampleReductionFactor, warmupIterations);
    }
}