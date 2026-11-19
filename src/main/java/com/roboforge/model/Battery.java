package com.roboforge.model;

public class Battery {
    private int level = 100;
    public int getLevel() { return level; }
    public boolean consume(int amount) {
        if (level < amount) return false;
        level = Math.max(0, level - amount);
        return true;
    }
    public void recharge() { level = 100; }
}
