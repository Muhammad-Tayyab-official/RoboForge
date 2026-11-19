package com.roboforge.simulation;

public record Obstacle(double x, double z, double width, double depth, String label) {
    // Include the robot body's radius so it cannot visually clip into buildings.
    private static final double ROBOT_CLEARANCE = 18.0;

    public boolean blocks(double px, double pz) {
        return Math.abs(px - x) < width / 2.0 + ROBOT_CLEARANCE
                && Math.abs(pz - z) < depth / 2.0 + ROBOT_CLEARANCE;
    }
}
