package model;

public class Item {
    private final int id;
    private final int utility;

    public Item(int id, int utility) {
        this.id = id;
        this.utility = utility;
    }

    public int getId() {
        return id;
    }

    public int getUtility() {
        return utility;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return id == item.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return id + "[" + utility + "]";
    }
}