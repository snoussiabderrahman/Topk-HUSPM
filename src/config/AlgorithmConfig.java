package config;

public class AlgorithmConfig {
    private final int k;                    // Nombre de top patterns
    private final int sampleSize;           // N
    private final double rho;               // Elite ratio
    private final int maxIterations;        // max_iter
    private final int maxSequenceLength;    // max_length_sequence

    public AlgorithmConfig(int k, int sampleSize, double rho, int maxIterations, int maxSequenceLength) {
        this.k = k;
        this.sampleSize = sampleSize;
        this.rho = rho;
        this.maxIterations = maxIterations;
        this.maxSequenceLength = maxSequenceLength;
    }

    // Getters
    public int getK() { return k; }
    public int getSampleSize() { return sampleSize; }
    public double getRho() { return rho; }
    public int getMaxIterations() { return maxIterations; }
    public int getMaxSequenceLength() { return maxSequenceLength; }

    @Override
    public String toString() {
        return String.format(
                "Config{k=%d, N=%d, rho=%.2f, max_iter=%d, max_seq_len=%d}",
                k, sampleSize, rho, maxIterations, maxSequenceLength
        );
    }
}