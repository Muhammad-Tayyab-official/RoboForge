# RoboForge

**RoboForge** is a JavaFX 3D robot-city simulator built as an object-oriented programming project. Pilot different robot types through a simulated city, monitor their telemetry, interact with cargo, scan the environment, and plan collision-aware routes.

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![JavaFX 21](https://img.shields.io/badge/JavaFX-21.0.2-2f74c0)
![Maven](https://img.shields.io/badge/build-Maven-c71a36?logo=apachemaven)

## Overview

RoboForge provides an interactive desktop control center and a fixed-camera 3D city environment. The simulation demonstrates object-oriented modeling, JavaFX scene composition, robot behaviors, collision detection, resource management, animation, and grid-based pathfinding.

## Features

- **Three robot types:** Humanoid, Explorer, and Industrial
- **3D city simulation:** JavaFX primitive geometry with a fixed camera and structured city layout
- **Robot control:** Directional movement, strafing, rotation, stopping, speed adjustment, and position reset
- **Environment awareness:** Building collision detection and nearby obstacle status
- **Explorer automation:** Multi-segment Auto Explore route through the central city
- **Scanning:** Simulated sector and cargo detection with an animated Explorer sensor head
- **Cargo handling:** Industrial and Humanoid robots can pick up and drop visible supply crates
- **Robot animation:** Industrial arm movement during cargo operations
- **Route planning:** A* navigation on a 20-unit grid with collision checks
- **Telemetry:** Position, mode, battery level, sensor status, cargo state, and activity log
- **Battery simulation:** Energy use for movement, rotation, and long-distance patrols, with recharge support

## Requirements

- [JDK 21](https://adoptium.net/)
- [Apache Maven](https://maven.apache.org/) 3.9 or later
- A desktop environment capable of running JavaFX

Verify the Java and Maven installations:

```bash
java -version
mvn -version
```

## Getting Started

1. Clone the repository and enter the project directory:

   ```bash
   git clone https://github.com/Muhammad-Tayyab-official/RoboForge.git
   cd RoboForge
   ```

2. Start the application with Maven:

   ```bash
   mvn clean javafx:run
   ```

Maven downloads the JavaFX dependencies and launches `com.roboforge.app.RoboForgeApp`.

### IntelliJ IDEA

1. Open the directory containing `pom.xml`.
2. Configure the project SDK as **JDK 21**.
3. Reload the Maven project.
4. Run the Maven goal `clean javafx:run`.

## Controls

| Action | Keyboard | Control panel |
| --- | --- | --- |
| Move forward | `W` / `Up` | Forward button |
| Move backward | `S` / `Down` | Backward button |
| Strafe left/right | `A` / `Left`, `D` / `Right` | Left/right buttons |
| Rotate | `Q`, `E` | Turn left/right |
| Stop movement | `Space` | Stop |
| Scan surroundings | — | Scan surroundings |
| Pick up cargo | — | Pick cargo |
| Drop cargo | — | Drop cargo |
| Recharge | — | Recharge battery |
| Explore automatically | — | Auto explore |
| Reset position | — | Reset robot position |
| Navigate to a destination | — | Enter X/Z and select Plan route & navigate |

### Robot-specific actions

- **Auto Explore** requires the **Explorer Robot**.
- **Pick Cargo** and **Drop Cargo** are available to **Humanoid** and **Industrial** robots.
- Destination coordinates are snapped to the 20-unit navigation grid.
- Robot switching resets the selected robot to the city start point and is disabled while cargo is being carried.

## Project Structure

```text
src/
└── main/
    └── java/com/roboforge/
        ├── app/
        │   └── RoboForgeApp.java       # JavaFX application and user interface
        ├── model/
        │   ├── Robot.java              # Shared robot behavior
        │   ├── HumanoidRobot.java
        │   ├── ExplorerRobot.java
        │   ├── IndustrialRobot.java
        │   ├── Battery.java
        │   ├── Movable.java
        │   ├── Scannable.java
        │   └── ObjectHandler.java
        └── simulation/
            ├── CollisionDetector.java
            └── Obstacle.java
```

## Technical Notes

- The city geometry and robot scene node are kept separate so robot movement does not transform the environment.
- The playable area is bounded to the camera-framed city.
- A* navigation uses the central drivable grid from `X -300..300` and `Z -150..150`.
- Sensor readings and cargo interactions operate on simulated objects maintained by the application.

## Limitations

- This is a software simulation, not hardware-control software.
- Visual assets are assembled from JavaFX primitive shapes rather than imported 3D models.
- Sensor readings represent simulated city objects and are not physical sensor data.
- The Industrial robot arm uses simplified joint animation rather than full inverse kinematics.

## Development

Compile the project without launching the application:

```bash
mvn clean compile
```

The Maven configuration defines Java 21 compilation and JavaFX 21.0.2 dependencies.

## License

No license has been specified for this project yet.
