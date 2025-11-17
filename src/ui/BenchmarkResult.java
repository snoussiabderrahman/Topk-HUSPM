package ui;

import javafx.beans.property.*;

public class BenchmarkResult {
    private final StringProperty algorithm = new SimpleStringProperty();
    private final StringProperty dataset = new SimpleStringProperty();
    private final IntegerProperty k = new SimpleIntegerProperty();
    private final LongProperty runtimeMs = new SimpleLongProperty();
    private final DoubleProperty memoryMb = new SimpleDoubleProperty();
    private final StringProperty status = new SimpleStringProperty();

    public BenchmarkResult(String algorithm, String dataset, int k,
                           long runtimeMs, double memoryMb, String status) {
        this.algorithm.set(algorithm);
        this.dataset.set(dataset);
        this.k.set(k);
        this.runtimeMs.set(runtimeMs);
        this.memoryMb.set(memoryMb);
        this.status.set(status);
    }

    // Getters for TableView binding
    public String getAlgorithm() { return algorithm.get(); }
    public String getDataset() { return dataset.get(); }
    public int getK() { return k.get(); }
    public long getRuntimeMs() { return runtimeMs.get(); }
    public double getMemoryMb() { return memoryMb.get(); }
    public String getStatus() { return status.get(); }

    public StringProperty algorithmProperty() { return algorithm; }
    public StringProperty datasetProperty() { return dataset; }
    public IntegerProperty kProperty() { return k; }
    public LongProperty runtimeMsProperty() { return runtimeMs; }
    public DoubleProperty memoryMbProperty() { return memoryMb; }
    public StringProperty statusProperty() { return status; }
}