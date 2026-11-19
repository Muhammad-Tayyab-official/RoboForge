# RoboForge – 3D Robot City Simulator

Java 21 + JavaFX 3D + Maven OOP semester project.

## Run in IntelliJ IDEA
1. Open the `RoboForge` folder (the folder containing `pom.xml`).
2. Set Project SDK to JDK 21.
3. 
4. Run `clean javafx:run`.

## Implemented in this build
- Static 3D city geometry and locked camera, separated from the robot scene node.
- Three selectable robot types: Humanoid, Explorer and Industrial.
- Manual directional movement, rotation, stop, speed setting and reset.
- City bounds and building collision checks.
- Explorer-only Auto Explore follows a multi-segment road loop through the central city instead of stopping after four steps.
- Explorer scanning counts nearby building sectors and cargo props; its sensor head performs a sweep animation.
- Industrial and Humanoid robots can pick up actual visible cargo props when close enough and drop them at their current location.
- Industrial robot arm animates during pick/drop.
- Destination navigation uses A* on a 20-unit grid with building collision checks.
- Battery consumption is charged per group of route nodes during long patrols so a full-charge Explorer can complete the central loop; manual moves and rotations consume energy. Recharge, live telemetry and activity log.
- Robot switching resets the new robot to the city start point and is blocked while cargo is being carried.

## Controls
- W / Up: forward step
- S / Down: backward step
- A / Left: left step
- D / Right: right step
- Q / E: rotate
- Space: stop active route
- Control panel buttons: same movement and robot actions
- Auto Explore: select Explorer Robot, then press Auto Explore
- Pick Cargo: move within 65 world units of a visible supply crate
- Drop Cargo: drop the carried physical crate at the robot's current location
- Plan Route: enter destination X/Z (coordinates are snapped to the 20-unit grid)

## Important limitations
- This is a software simulation, not real hardware control.
- 3D assets are built from JavaFX primitive geometry; this is not a photorealistic imported robot model.
- Sensor readings are simulated from the city objects stored in the application.
- A* operates on the central drivable grid (X -300..300, Z -150..150).
- Industrial arm animation is a simplified joint movement, not a full inverse-kinematics model.
- JavaFX runtime and Maven build must be verified on the target Windows/IntelliJ machine.
