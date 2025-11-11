package model;

public class Item {
    private final int id;
    private int utility; // Plus final car calculé après

    public Item(int id) {
        this.id = id;
        this.utility = 0;
    }

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

    public void setUtility(int utility) {
        this.utility = utility;
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
        return utility > 0 ? id + "[" + utility + "]" : String.valueOf(id);
    }
}