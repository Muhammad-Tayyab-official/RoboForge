package com.roboforge.model;
public class HumanoidRobot extends Robot implements ObjectHandler {
    private String carriedObject;
    public HumanoidRobot() { super("Atlas H-1"); }
    @Override public String getType() { return "Humanoid"; }
    @Override public boolean pickObject(String name) { if (carriedObject != null) return false; carriedObject = name; return true; }
    @Override public String dropObject() { String item = carriedObject; carriedObject = null; return item; }
    public String getCarriedObject() { return carriedObject; }
}
