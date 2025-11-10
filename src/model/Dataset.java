package model;

import java.util.ArrayList;
import java.util.List;

public class Dataset {
    private final List<Sequence> sequences;
    private final int totalUtility;

    public Dataset(List<Sequence> sequences) {
        this.sequences = new ArrayList<>(sequences);
        this.totalUtility = sequences.stream()
                .mapToInt(Sequence::getUtility)
                .sum();
    }

    public List<Sequence> getSequences() {
        return new ArrayList<>(sequences);
    }

    public int size() {
        return sequences.size();
    }

    public int getTotalUtility() {
        return totalUtility;
    }

    @Override
    public String toString() {
        return "Dataset{sequences=" + sequences.size() +
                ", totalUtility=" + totalUtility + "}";
    }
}