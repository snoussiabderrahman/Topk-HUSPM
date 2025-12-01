package config;

public class AlgorithmConfig {
    private final int k; // Nombre de top patterns
    private final int sampleSize; // N
    private final double rho; // Elite ratio
    private final int maxIterations; // max_iter
    private final int maxSequenceLength; // max_length_sequence

    // Early stopping parameters
    private final int stagnationThreshold; // X: iterations without k-th utility improvement
    private final int stabilizationThreshold; // Y: iterations with stable PM
    private final double pmVariationEpsilon; // epsilon: threshold for PM variation

    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength) {
        this(k, sampleSize, rho, maxIterations, maxSequenceLength, 15, 10, 0.005);
    }

    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength,
            int stagnationThreshold, int stabilizationThreshold, double pmVariationEpsilon) {
        this.k = k;
        this.sampleSize = sampleSize;
        this.rho = rho;
        this.maxIterations = maxIterations;
        this.maxSequenceLength = maxSequenceLength;
        this.stagnationThreshold = stagnationThreshold;
        this.stabilizationThreshold = stabilizationThreshold;
        this.pmVariationEpsilon = pmVariationEpsilon;
    }

    // Getters
    public int getK() {
        return k;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public double getRho() {
        return rho;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getMaxSequenceLength() {
        return maxSequenceLength;
    }

    public int getStagnationThreshold() {
        return stagnationThreshold;
    }

    public int getStabilizationThreshold() {
        return stabilizationThreshold;
    }

    public double getPmVariationEpsilon() {
        return pmVariationEpsilon;
    }

    @Override
    public String toString() {
        return String.format(
                "Config{k=%d, N=%d, rho=%.2f, max_iter=%d, max_seq_len=%d, stag_thresh=%d, stab_thresh=%d, epsilon=%.4f}",
                k, sampleSize, rho, maxIterations, maxSequenceLength,
                stagnationThreshold, stabilizationThreshold, pmVariationEpsilon);
    }
}