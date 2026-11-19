package com.roboforge.model;

public abstract class Robot implements Movable, Scannable {
    private final String name;
    private final Battery battery = new Battery();
    private double x, z, heading;
    private double speed = 1.0;
    private String mode = "IDLE";

    protected Robot(String name) { this.name = name; }
    public String getName() { return name; }
    public Battery getBattery() { return battery; }
    public double getX() { return x; }
    public double getZ() { return z; }
    public double getHeading() { return heading; }
    public void setHeading(double heading) { this.heading = ((heading % 360) + 360) % 360; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = Math.max(0.5, Math.min(3.0, speed)); }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    @Override public void move(double dx, double dz) { x += dx; z += dz; }
    @Override public void rotate(double degrees) { heading = (heading + degrees) % 360; }
    public void setPosition(double x, double z) { this.x = x; this.z = z; }
    public abstract String getType();
    @Override public String scan() { return getType() + " scan complete: environment checked."; }
}
