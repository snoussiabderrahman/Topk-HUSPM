package model;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Retourne une vue non modifiable sur la liste interne.
     * Plus rapide que de renvoyer une copie à chaque appel.
     */
    public List<Sequence> getSequences() {
        return Collections.unmodifiableList(sequences);
    }

    /**
     * Accès direct et rapide par index (évite la création d'une copie).
     */
    public Sequence getSequence(int index) {
        return sequences.get(index);
    }

    public int size() {
        return sequences.size();
    }

    @Override
    public String toString() {
        return "Dataset{sequences=" + sequences.size() +
                ", totalUtility=" + totalUtility + "}";
    }
}