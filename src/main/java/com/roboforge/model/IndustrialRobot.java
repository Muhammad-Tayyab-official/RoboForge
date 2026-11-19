package com.roboforge.model;
public class IndustrialRobot extends Robot implements ObjectHandler {
    private String cargo;
    public IndustrialRobot() { super("Forge ARM-3"); }
    @Override public String getType() { return "Industrial"; }
    @Override public boolean pickObject(String name) { if (cargo != null) return false; cargo = name; return true; }
    @Override public String dropObject() { String item = cargo; cargo = null; return item; }
    public String getCargo() { return cargo; }
}
