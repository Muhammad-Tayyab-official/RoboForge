package com.roboforge.simulation;
import java.util.List;
public class CollisionDetector {
    private final List<Obstacle> obstacles;
    public CollisionDetector(List<Obstacle> obstacles) { this.obstacles = obstacles; }
    public Obstacle firstCollision(double x, double z) {
        return obstacles.stream().filter(o -> o.blocks(x, z)).findFirst().orElse(null);
    }
}
