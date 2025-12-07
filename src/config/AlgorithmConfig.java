package config;

public class AlgorithmConfig {
    private final int k; // Nombre de top patterns
    private final int sampleSize; // N
    private final double rho; // Elite ratio
    private final int maxIterations; // max_iter
    private final int maxSequenceLength; // max_length_sequence

    // Convergence control parameters
    private final double learningRate; // Alpha: rate of PM update (0.0-1.0)
    private final double minProbability; // Lower bound for probabilities
    private final double maxProbability; // Upper bound for probabilities

    // Early stopping / convergence detection
    private final int maxStagnationIterations; // X: max iterations without k-th utility improvement
    private final int maxPMStabilizationIterations; // Y: max iterations with stable PM
    private final double pmStabilizationThreshold; // eps: threshold for PM stability

    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength) {
        this(k, sampleSize, rho, maxIterations, maxSequenceLength, 0.2, 0.05, 0.95, 20, 5, 0.01);
    }

    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength,
            double learningRate, double minProbability, double maxProbability,
            int maxStagnationIterations, int maxPMStabilizationIterations,
            double pmStabilizationThreshold) {
        this.k = k;
        this.sampleSize = sampleSize;
        this.rho = rho;
        this.maxIterations = maxIterations;
        this.maxSequenceLength = maxSequenceLength;
        this.learningRate = learningRate;
        this.minProbability = minProbability;
        this.maxProbability = maxProbability;
        this.maxStagnationIterations = maxStagnationIterations;
        this.maxPMStabilizationIterations = maxPMStabilizationIterations;
        this.pmStabilizationThreshold = pmStabilizationThreshold;
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

    public double getMinProbability() {
        return minProbability;
    }

    public double getMaxProbability() {
        return maxProbability;
    }

    public int getMaxStagnationIterations() {
        return maxStagnationIterations;
    }

    public int getMaxPMStabilizationIterations() {
        return maxPMStabilizationIterations;
    }

    public double getPMStabilizationThreshold() {
        return pmStabilizationThreshold;
    }

    @Override
    public String toString() {
        return String.format(
                "Config{k=%d, N=%d, rho=%.2f, max_iter=%d, max_seq_len=%d, alpha=%.2f, minP=%.2f, maxP=%.2f, maxStag=%d, maxPMStab=%d, pmEps=%.3f}",
                k, sampleSize, rho, maxIterations, maxSequenceLength,
                learningRate, minProbability, maxProbability,
                maxStagnationIterations, maxPMStabilizationIterations, pmStabilizationThreshold);
    }
}