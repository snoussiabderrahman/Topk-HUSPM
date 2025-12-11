package utils;

import model.*;
import java.util.*;

/**
 * ProjectedDatabase : Structure pour le calcul incrémental d'utilité
 * Inspirée de la projection database de HUSP-SP
 *
 * PRINCIPE :
 * - Stocke les positions des matchs pour chaque q-sequence
 * - Permet de calculer u(pattern + extension) à partir de u(pattern)
 * - Évite de recalculer depuis le début
 *
 * EXEMPLE :
 * Pattern : <{a}, {b}>  → positions = {seq0: [0, 3], seq1: [1, 5]}
 * Extension : <{a}, {b}, {c}> → on cherche seulement 'c' après position 3 (seq0) et 5 (seq1)
 */
public class ProjectedDatabase {

    /**
     * Information de match pour UNE q-sequence
     */
    public static class MatchInfo {
        /** Index de la q-sequence dans le dataset */
        public final int seqIndex;

        /** Positions des itemsets matchés (une par itemset du pattern) */
        public final int[] positions;

        /** Utilité déjà calculée pour ce match */
        public long utility;

        /** Position du dernier item matché (pour I-concat vs S-concat) */
        public int lastItemPosition;

        public MatchInfo(int seqIndex, int[] positions) {
            this.seqIndex = seqIndex;
            this.positions = positions;
            this.utility = 0;
            this.lastItemPosition = -1;
        }

        /**
         * Clone pour créer une projection étendue
         */
        public MatchInfo extend(int newPosition) {
            int[] newPositions = Arrays.copyOf(positions, positions.length + 1);
            newPositions[positions.length] = newPosition;

            MatchInfo extended = new MatchInfo(seqIndex, newPositions);
            extended.utility = this.utility; // Hérité du préfixe
            extended.lastItemPosition = newPosition;

            return extended;
        }

        @Override
        public String toString() {
            return String.format("MatchInfo{seq=%d, pos=%s, util=%d}",
                    seqIndex, Arrays.toString(positions), utility);
        }
    }

    // ========== DONNÉES DE LA PROJECTION ==========

    /** Pattern associé à cette projection */
    private final Sequence pattern;

    /** Signature du pattern (pour cache) */
    private final String signature;

    /** Liste des matchs dans le dataset */
    private final List<MatchInfo> matches;

    /** Utilité totale de ce pattern (somme des matches) */
    private long totalUtility;

    /** Timestamp de création (pour invalidation du cache) */
    private final long timestamp;

    // ========== CONSTRUCTEURS ==========

    /**
     * Constructeur pour une nouvelle projection
     */
    public ProjectedDatabase(Sequence pattern) {
        this.pattern = pattern;
        this.signature = pattern.getSignature();
        this.matches = new ArrayList<>();
        this.totalUtility = 0;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructeur pour cloner avec matchs
     */
    private ProjectedDatabase(Sequence pattern, List<MatchInfo> matches, long totalUtility) {
        this.pattern = pattern;
        this.signature = pattern.getSignature();
        this.matches = new ArrayList<>(matches);
        this.totalUtility = totalUtility;
        this.timestamp = System.currentTimeMillis();
    }

    // ========== MÉTHODES PRINCIPALES ==========

    /**
     * Ajoute un match trouvé
     */
    public void addMatch(MatchInfo match) {
        matches.add(match);
        totalUtility += match.utility;
    }

    /**
     * Crée une projection étendue pour pattern + nouvel itemset
     *
     * ALGORITHME :
     * 1. Pour chaque match existant
     * 2. Chercher l'extension dans la q-sequence correspondante
     * 3. Si trouvée, ajouter à la nouvelle projection
     *
     * @param newItemset l'itemset à ajouter (I-concat ou S-concat)
     * @param isSConcat true si S-concatenation, false si I-concatenation
     * @param compactIndex l'index compact pour accéder aux q-sequences
     * @return nouvelle projection étendue (ou null si aucun match)
     */
    public ProjectedDatabase extend(Itemset newItemset, boolean isSConcat,
                                    CompactSequenceIndex compactIndex) {

        // Créer le pattern étendu
        Sequence extendedPattern = new Sequence();
        for (Itemset is : pattern.getItemsets()) {
            Itemset copy = new Itemset();
            for (Item item : is.getItems()) {
                copy.addItem(new Item(item.getId()));
            }
            extendedPattern.addItemset(copy);
        }
        extendedPattern.addItemset(newItemset);

        // Créer la nouvelle projection
        ProjectedDatabase extended = new ProjectedDatabase(extendedPattern);

        // ⚡ CALCUL INCRÉMENTAL : étendre chaque match existant
        for (MatchInfo match : matches) {
            MatchInfo extendedMatch = compactIndex.extendMatch(
                    match, newItemset, isSConcat
            );

            if (extendedMatch != null) {
                extended.addMatch(extendedMatch);
            }
        }

        // Retourner null si aucun match trouvé
        return extended.matches.isEmpty() ? null : extended;
    }

    // ========== GETTERS ==========

    public String getSignature() {
        return signature;
    }

    public long getTotalUtility() {
        return totalUtility;
    }

    public List<MatchInfo> getMatches() {
        return Collections.unmodifiableList(matches);
    }

    public int getMatchCount() {
        return matches.size();
    }

    public Sequence getPattern() {
        return pattern;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Statistiques pour debug
     */
    public String getStats() {
        return String.format("ProjectedDB{pattern=%s, matches=%d, utility=%d}",
                pattern.toCompactString(), matches.size(), totalUtility);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ProjectedDatabase{\n");
        sb.append("  pattern: ").append(pattern.toCompactString()).append("\n");
        sb.append("  utility: ").append(totalUtility).append("\n");
        sb.append("  matches: ").append(matches.size()).append("\n");

        // Afficher les 3 premiers matchs
        int displayCount = Math.min(3, matches.size());
        for (int i = 0; i < displayCount; i++) {
            sb.append("    - ").append(matches.get(i)).append("\n");
        }
        if (matches.size() > 3) {
            sb.append("    ... (").append(matches.size() - 3).append(" more)\n");
        }

        sb.append("}");
        return sb.toString();
    }
}