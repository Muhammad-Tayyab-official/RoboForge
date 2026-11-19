package com.roboforge.model;
public class ExplorerRobot extends Robot {
    public ExplorerRobot() { super("Scout X-2"); }
    @Override public String getType() { return "Explorer"; }
    @Override public String scan() { return "Explorer scan complete: 360-degree area sweep finished."; }
}
