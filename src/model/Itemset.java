package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Itemset {
    private final List<Item> items;

    public Itemset() {
        this.items = new ArrayList<>();
    }

    public Itemset(List<Item> items) {
        this.items = new ArrayList<>(items);
    }

    public void addItem(Item item) {
        items.add(item);
    }

    public List<Item> getItems() {
        return  Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int getUtility() {
        return items.stream().mapToInt(Item::getUtility).sum();
    }

    @Override
    public String toString() {
        return items.stream()
                .map(Item::toString)
                .collect(Collectors.joining(" ")) + " -1";
    }

    public String toCompactString() {
        return items.stream()
                .map(item -> String.valueOf(item.getId()))
                .collect(Collectors.joining(",", "{", "}"));
    }
}