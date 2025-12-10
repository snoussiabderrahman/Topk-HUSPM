package config;

public class AlgorithmConfig {
    private final int k; // Nombre de top patterns
    private final int sampleSize; // N
    private final double rho; // Elite ratio
    private final int maxIterations; // max_iter
    private final int maxSequenceLength; // max_length_sequence

    // Convergence control parameters
    private final double learningRate; // Alpha: rate of PM update (0.0-1.0)

    // Early stopping / convergence detection
    private final int maxStagnationIterations; // X: max iterations without k-th utility improvement

    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength) {
        this(k, sampleSize, rho, maxIterations, maxSequenceLength, 0.2, 5);
    }

    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength,
            double learningRate, int maxStagnationIterations) {
        this.k = k;
        this.sampleSize = sampleSize;
        this.rho = rho;
        this.maxIterations = maxIterations;
        this.maxSequenceLength = maxSequenceLength;
        this.learningRate = learningRate;
        this.maxStagnationIterations = maxStagnationIterations;
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

    public double getLearningRate() {
        return learningRate;
    }

    public int getMaxStagnationIterations() {
        return maxStagnationIterations;
    }

    @Override
    public String toString() {
        return String.format(
                "Config{k=%d, N=%d, rho=%.2f, max_iter=%d, max_seq_len=%d, alpha=%.2f, maxStag=%d}",
                k, sampleSize, rho, maxIterations, maxSequenceLength,
                learningRate, maxStagnationIterations);
    }
}