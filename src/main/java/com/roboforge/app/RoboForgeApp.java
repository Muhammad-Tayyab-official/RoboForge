package com.roboforge.app;

import com.roboforge.model.*;
import com.roboforge.simulation.*;
import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.input.KeyCode;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.AmbientLight;
import javafx.scene.PointLight;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import javafx.stage.Screen;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

public class RoboForgeApp extends Application {
    // Static city geometry and the moving robot are deliberately isolated as siblings.
    // Robot movement must never translate, rotate, or scale the city.
    private final Group world = new Group();
    private final Group cityGeometry = new Group();
    private final Group robotView = new Group();
    private PerspectiveCamera fixedCamera;
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private Robot robot = new HumanoidRobot();
    private Label positionLabel, batteryLabel, modeLabel, sensorLabel, cargoLabel;
    private ProgressBar batteryBar;
    private TextArea activity;
    private Slider speedSlider;
    private SubScene cityScene;
    private Timeline exploreTimeline;
    private final Set<KeyCode> heldKeys = new HashSet<>();
    private final List<CargoProp> cargoProps = new ArrayList<>();
    private CargoProp carriedProp;
    private Group industrialArm;
    private Rotate industrialArmJoint;
    private Group explorerScanner;
    private final List<Rotate> explorerWheelRotations = new ArrayList<>();
    private TextField destinationXField, destinationZField;
    private List<double[]> activeRoute = new ArrayList<>();
    private int routeTargetIndex = 0;
    private String activeRouteName = "AUTO EXPLORE";
    private Timeline scanTimeline;
    private Timeline armTimeline;
    // The playable road area is deliberately smaller than the visual ground plane.
    // This keeps the robot inside the camera-framed city at all times.
    private static final double CITY_LIMIT_X = 300;
    private static final double CITY_LIMIT_Z = 150;

    private static class CargoProp {
        final String name;
        final Group node;
        final double originalX, originalZ;
        CargoProp(String name, Group node, double x, double z) {
            this.name=name; this.node=node; this.originalX=x; this.originalZ=z;
        }
    }
    private record GridPoint(int x, int z) {}

    @Override public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #070d1a; -fx-font-family: 'Segoe UI';");
        root.setTop(buildHeader());
        root.setLeft(buildSidebar());

        // Keep the 3D viewport and controls in one explicit horizontal layout.
        // This prevents BorderPane sizing from pushing the control panel off-screen.
        VBox cityPanel = buildCityPanel();
        ScrollPane controlsScroll = new ScrollPane(buildControlPanel());
        controlsScroll.setFitToWidth(true);
        controlsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        controlsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        controlsScroll.setPrefViewportWidth(300);
        controlsScroll.setMinWidth(285);
        controlsScroll.setMaxWidth(330);
        controlsScroll.setStyle("-fx-background:#0a1324;-fx-background-color:#0a1324;-fx-border-color:#1a2c49;-fx-border-width:0 0 0 1;");
        HBox mainContent = new HBox(0, cityPanel, controlsScroll);
        HBox.setHgrow(cityPanel, Priority.ALWAYS);
        cityPanel.setMinWidth(480);
        root.setCenter(mainContent);
        root.setBottom(buildFooter());
        var screenBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.min(1450, screenBounds.getWidth() - 40);
        double initialHeight = Math.min(900, screenBounds.getHeight() - 60);
        Scene scene = new Scene(root, initialWidth, initialHeight, true);
        stage.setTitle("RoboForge | 3D Robot City Simulator");
        stage.setMinWidth(1180);
        stage.setMinHeight(680);
        stage.setWidth(initialWidth);
        stage.setHeight(initialHeight);
        stage.setScene(scene);
        stage.setOnShown(e -> root.requestFocus());
        stage.show();
        // Ignore OS key-repeat events while a key is held. One press = one controlled step.
        scene.setOnKeyPressed(e -> {
            if (e.getTarget() instanceof TextInputControl) return;
            if (!heldKeys.add(e.getCode())) return;
            switch (e.getCode()) {
                case W, UP -> moveRelative(1);
                case S, DOWN -> moveRelative(-1);
                case A, LEFT -> strafe(-1);
                case D, RIGHT -> strafe(1);
                case Q -> rotateRobot(-15);
                case E -> rotateRobot(15);
                case SPACE -> stopRobot();
                default -> { }
            }
        });
        scene.setOnKeyReleased(e -> heldKeys.remove(e.getCode()));
        refreshStatus();
        log("RoboForge simulation initialized.");
        log("City environment loaded. Select a robot and start exploring.");
    }

    private HBox buildHeader() {
        Label brand = new Label("◉  ROBOFORGE"); brand.setStyle("-fx-text-fill:#55c8ff;-fx-font-size:20px;-fx-font-weight:bold;");
        Label sub = new Label("3D ROBOT CITY SIMULATOR   /   JAVA OOP PROJECT"); sub.setStyle("-fx-text-fill:#8294b4;-fx-font-size:11px;");
        VBox title = new VBox(3, brand, sub);
        Label live = new Label("●  SIMULATION ONLINE"); live.setStyle("-fx-text-fill:#55e6a5;-fx-background-color:#10352e;-fx-padding:9 14;-fx-background-radius:16;");
        HBox bar = new HBox(title, new Region(), live); HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT); bar.setPadding(new Insets(16, 22, 16, 22));
        bar.setStyle("-fx-background-color:#0b1426;-fx-border-color:#1a2c49;-fx-border-width:0 0 1 0;"); return bar;
    }

    private VBox buildSidebar() {
        Label logo = new Label("🤖"); logo.setStyle("-fx-font-size:42px;");
        Label name = new Label("RoboForge"); name.setStyle("-fx-text-fill:#eaf5ff;-fx-font-size:21px;-fx-font-weight:bold;");
        Label tagline = new Label("Explore. Build. Control."); tagline.setStyle("-fx-text-fill:#7790b7;-fx-font-size:11px;");
        VBox top = new VBox(5, logo, name, tagline); top.setAlignment(Pos.CENTER); top.setPadding(new Insets(24, 10, 28, 10));
        VBox nav = new VBox(9);
        String[] items = {"▦   Dashboard", "⌘   Robot Control", "◎   City Simulation", "⌖   Navigation", "⚙   Robot Workshop"};
        for (String item : items) {
            Button button = new Button(item);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setPadding(new Insets(12, 13, 12, 13));
            button.setStyle(navStyle(item.contains("Dashboard")));
            button.setOnAction(e -> {
                log(item.trim() + " selected.");
                if (item.contains("Robot Control")) {
                    // The always-visible right panel contains the live movement/action controls.
                    rootFocusControls();
                } else if (item.contains("City Simulation")) {
                    if (cityScene != null) cityScene.requestFocus();
                } else if (item.contains("Navigation")) {
                    log("Use the Auto Explore action in the control panel for the current scripted navigation demo.");
                } else if (item.contains("Robot Workshop")) {
                    log("Choose Humanoid, Explorer or Industrial Robot from the Active Robot selector.");
                }
            });
            nav.getChildren().add(button);
        }
        Label note = new Label("JAVA OBJECT-ORIENTED\nSIMULATION PLATFORM"); note.setStyle("-fx-text-fill:#526989;-fx-font-size:10px;-fx-alignment:center;");
        VBox side = new VBox(top, nav, new Region(), note); VBox.setVgrow(side.getChildren().get(2), Priority.ALWAYS);
        side.setPadding(new Insets(0, 10, 18, 10)); side.setPrefWidth(175); side.setMinWidth(160); side.setStyle("-fx-background-color:#0a1324;-fx-border-color:#1a2c49;-fx-border-width:0 1 0 0;"); return side;
    }
    private void rootFocusControls() {
        // Focus is returned to the main window; controls remain visible in the right-hand panel.
        if (cityScene != null && cityScene.getScene() != null) cityScene.getScene().getRoot().requestFocus();
    }

    private String navStyle(boolean selected) { return selected ? "-fx-text-fill:#eaf7ff;-fx-font-size:13px;-fx-background-color:#123b68;-fx-background-radius:9;-fx-border-color:#1b9be8;-fx-border-radius:9;" : "-fx-text-fill:#9cafcc;-fx-font-size:13px;-fx-background-color:transparent;"; }

    private VBox buildCityPanel() {
        Label heading = new Label("LIVE CITY ENVIRONMENT"); heading.setStyle("-fx-text-fill:#e7f2ff;-fx-font-size:16px;-fx-font-weight:bold;");
        Label hint = new Label("3D simulation  •  Use the control panel to move your robot"); hint.setStyle("-fx-text-fill:#7f94b5;-fx-font-size:11px;");
        fixedCamera = new PerspectiveCamera(true); fixedCamera.setNearClip(0.1); fixedCamera.setFarClip(4000); fixedCamera.setFieldOfView(35);
        lockCamera();
        buildCity();
        cityScene = new SubScene(world, 800, 600, true, SceneAntialiasing.BALANCED);
        cityScene.setFill(Color.web("#101e34"));
        cityScene.setCamera(fixedCamera);
        // The SubScene is unmanaged inside a plain Pane: the Pane decides the size, the SubScene
        // only follows it. This stops the viewport from resizing/shifting the rendered city.
        Pane sceneHolder = new Pane(cityScene);
        cityScene.setManaged(false);
        cityScene.widthProperty().bind(sceneHolder.widthProperty());
        cityScene.heightProperty().bind(sceneHolder.heightProperty());
        sceneHolder.setMinSize(0, 0);
        StackPane viewport = new StackPane(sceneHolder);
        viewport.setMinSize(0, 430);
        javafx.scene.shape.Rectangle viewportClip = new javafx.scene.shape.Rectangle();
        viewportClip.widthProperty().bind(viewport.widthProperty());
        viewportClip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(viewportClip);
        viewport.setStyle("-fx-border-color:#23456c;-fx-border-radius:12;-fx-background-radius:12;");
        VBox.setVgrow(viewport, Priority.ALWAYS);
        Label overlay = new Label("CITY SECTOR 01   /   SIMULATION VIEW"); overlay.setStyle("-fx-text-fill:#78cfff;-fx-background-color:#071326cc;-fx-padding:8 12;-fx-background-radius:5;");
        StackPane.setAlignment(overlay, Pos.TOP_LEFT); StackPane.setMargin(overlay, new Insets(14)); viewport.getChildren().add(overlay);
        VBox panel = new VBox(12, heading, hint, viewport); panel.setPadding(new Insets(16)); return panel;
    }

    /**
     * Camera lock: called once at scene creation and re-applied after robot commands.
     * There are intentionally no camera-follow, pan, zoom, or orbit animations.
     */
    private void lockCamera() {
        if (fixedCamera == null) return;
        // Fixed look-at rig: tilt down 28 degrees, then pull back 1350 units along the view axis.
        // Because the tilt happens around the origin, the camera always points at the city centre
        // (0,0,0) and is independent of window size, robot position or any other node.
        fixedCamera.getTransforms().setAll(
                new Rotate(-28, Rotate.X_AXIS),
                new Translate(0, 0, -1350));
    }

    private void buildCity() {
        cityGeometry.getChildren().clear();
        world.getChildren().clear();
        obstacles.clear();
        cargoProps.clear(); carriedProp=null;
        Box ground = new Box(1100, 8, 850); ground.setMaterial(mat("#18263a")); ground.setTranslateY(45); cityGeometry.getChildren().add(ground);
        // Roads and lane markings
        for (int i=-3;i<=3;i++) {
            Box road = new Box(1100, 2, 72); road.setMaterial(mat("#26364b")); road.setTranslateY(39); road.setTranslateZ(i*180); cityGeometry.getChildren().add(road);
            for (int x=-480;x<500;x+=75) { Box dash = new Box(34, 1, 3); dash.setMaterial(mat("#8ba0b8")); dash.setTranslateX(x); dash.setTranslateY(37); dash.setTranslateZ(i*180); cityGeometry.getChildren().add(dash); }
        }
        for (int i=-5;i<=5;i++) { Box line = new Box(3,1,850); line.setMaterial(mat("#30435a")); line.setTranslateX(i*95); line.setTranslateY(38); cityGeometry.getChildren().add(line); }
        // Buildings around the playable central roads
        addBuilding(-390,-280,115,190,150,"#263c59"); addBuilding(-160,-300,125,240,190,"#1c3452");
        addBuilding(150,-300,150,210,170,"#243c5b"); addBuilding(405,-280,120,180,140,"#1c3551");
        addBuilding(-400,280,135,200,175,"#203954"); addBuilding(-130,300,110,190,145,"#2b405d");
        addBuilding(170,300,140,220,180,"#1b3450"); addBuilding(420,280,120,170,130,"#263d59");
        // Street lights
        for (int x=-450;x<=450;x+=150) for (int z : new int[]{-100,100}) {
            Cylinder pole = new Cylinder(2,55); pole.setMaterial(mat("#7189a8")); pole.setTranslateX(x); pole.setTranslateY(10); pole.setTranslateZ(z); cityGeometry.getChildren().add(pole);
            Sphere lamp = new Sphere(7); lamp.setMaterial(mat("#38caff")); lamp.setTranslateX(x); lamp.setTranslateY(-20); lamp.setTranslateZ(z); cityGeometry.getChildren().add(lamp);
        }
        // Interactive cargo crates (visual props)
        addCrate(80,90,"#e8a33b"); addCrate(-40,-90,"#37aeea"); addCrate(300,100,"#a879ed");
        robotView.getChildren().clear();
        robotView.getChildren().add(createRobotModel(robot));
        world.getChildren().setAll(cityGeometry, robotView, fixedCamera);

        // JavaFX Phong materials need scene lights; without these the 3D city appears almost black.
        AmbientLight ambient = new AmbientLight(Color.color(0.78, 0.82, 0.92));
        PointLight keyLight = new PointLight(Color.WHITE);
        keyLight.setTranslateX(-260);
        keyLight.setTranslateY(-420);
        keyLight.setTranslateZ(-260);
        PointLight fillLight = new PointLight(Color.web("#70cfff"));
        fillLight.setTranslateX(360);
        fillLight.setTranslateY(-220);
        fillLight.setTranslateZ(260);
        cityGeometry.getChildren().addAll(ambient, keyLight, fillLight);
        updateRobotView();
    }
    private void addBuilding(double x,double z,double w,double d,double h,String color) {
        Box b = new Box(w,h,d); b.setMaterial(mat(color)); b.setTranslateX(x); b.setTranslateY(45-h/2); b.setTranslateZ(z); cityGeometry.getChildren().add(b);
        Box roof = new Box(w+8,5,d+8); roof.setMaterial(mat("#477092")); roof.setTranslateX(x); roof.setTranslateY(42-h); roof.setTranslateZ(z); cityGeometry.getChildren().add(roof);
        for (double wx=x-w/2+18; wx<x+w/2-10; wx+=34) for (double wy=48-h+28; wy<35; wy+=35) {
            Box window = new Box(12,9,2); window.setMaterial(mat("#35b8e8")); window.setTranslateX(wx); window.setTranslateY(wy); window.setTranslateZ(z-d/2-1); cityGeometry.getChildren().add(window);
        }
        obstacles.add(new Obstacle(x,z,w,d,"Building"));
    }
    private void addCrate(double x,double z,String color) {
        Group crate = new Group();
        Box body = new Box(27,27,27); body.setMaterial(mat(color));
        Box band = new Box(29,4,29); band.setMaterial(mat("#f4d28a")); band.setTranslateY(-2);
        crate.getChildren().addAll(body, band);
        crate.setTranslateX(x); crate.setTranslateY(23); crate.setTranslateZ(z);
        cityGeometry.getChildren().add(crate);
        cargoProps.add(new CargoProp("Supply crate", crate, x, z));
    }
    private PhongMaterial mat(String hex) { return new PhongMaterial(Color.web(hex)); }

    private Group createRobotModel(Robot r) {
        industrialArm = null;
        industrialArmJoint = null;
        explorerScanner = null;
        explorerWheelRotations.clear();
        Group g = new Group();
        PhongMaterial white=mat("#dceaff"), blue=mat("#159be8"), dark=mat("#172b46"), glow=mat("#42d9ff");
        if (r instanceof ExplorerRobot) {
            Cylinder base=new Cylinder(27,18); base.setMaterial(dark); base.setTranslateY(16); g.getChildren().add(base);
            Sphere dome=new Sphere(23); dome.setMaterial(blue); dome.setTranslateY(-12); g.getChildren().add(dome);
            explorerScanner = new Group();
            Box sensor=new Box(45,5,8); sensor.setMaterial(glow); sensor.setTranslateY(-14);
            Sphere sensorEye=new Sphere(5); sensorEye.setMaterial(white); sensorEye.setTranslateY(-14); sensorEye.setTranslateZ(-7);
            explorerScanner.getChildren().addAll(sensor,sensorEye); g.getChildren().add(explorerScanner);
            explorerWheelRotations.clear();
            for(int i=0;i<4;i++){
                Cylinder wheel=new Cylinder(8,12);wheel.setMaterial(white);wheel.setRotate(90);
                wheel.setTranslateX(i%2==0?-24:24);wheel.setTranslateY(22);wheel.setTranslateZ(i<2?-12:12);
                Rotate spin=new Rotate(0,Rotate.Z_AXIS); wheel.getTransforms().add(spin); explorerWheelRotations.add(spin); g.getChildren().add(wheel);
            }
        } else if (r instanceof IndustrialRobot) {
            Box base=new Box(58,22,48);base.setMaterial(dark);base.setTranslateY(20);g.getChildren().add(base);
            Cylinder turntable=new Cylinder(19,12);turntable.setMaterial(blue);turntable.setTranslateY(5);g.getChildren().add(turntable);
            Cylinder shoulder=new Cylinder(13,24);shoulder.setMaterial(glow);shoulder.setTranslateY(-10);g.getChildren().add(shoulder);
            industrialArm = new Group();
            industrialArmJoint = new Rotate(-28, Rotate.Z_AXIS);
            industrialArm.getTransforms().add(industrialArmJoint);
            Box upper=new Box(15,45,15);upper.setMaterial(white);upper.setTranslateY(-28);
            Cylinder elbow=new Cylinder(10,18);elbow.setMaterial(blue);elbow.setTranslateY(-51);
            Box forearm=new Box(12,28,12);forearm.setMaterial(white);forearm.setTranslateY(-68);
            Box palm=new Box(27,10,17);palm.setMaterial(glow);palm.setTranslateY(-86);
            Box fingerL=new Box(5,18,7);fingerL.setMaterial(white);fingerL.setTranslateX(-10);fingerL.setTranslateY(-98);
            Box fingerR=new Box(5,18,7);fingerR.setMaterial(white);fingerR.setTranslateX(10);fingerR.setTranslateY(-98);
            industrialArm.getChildren().addAll(upper,elbow,forearm,palm,fingerL,fingerR);
            g.getChildren().add(industrialArm);
        } else {
            Sphere head=new Sphere(22);head.setMaterial(white);head.setTranslateY(-73);g.getChildren().add(head);
            Box face=new Box(30,12,4);face.setMaterial(dark);face.setTranslateY(-73);face.setTranslateZ(-18);g.getChildren().add(face);
            for(int x:new int[]{-8,8}){Sphere eye=new Sphere(4);eye.setMaterial(glow);eye.setTranslateX(x);eye.setTranslateY(-74);eye.setTranslateZ(-21);g.getChildren().add(eye);}
            Box torso=new Box(43,53,27);torso.setMaterial(blue);torso.setTranslateY(-22);g.getChildren().add(torso);
            Box chest=new Box(24,19,4);chest.setMaterial(white);chest.setTranslateY(-26);chest.setTranslateZ(-15);g.getChildren().add(chest);
            for(int x:new int[]{-29,29}){Cylinder arm=new Cylinder(8,47);arm.setMaterial(white);arm.setTranslateX(x);arm.setTranslateY(-24);arm.setRotate(90);g.getChildren().add(arm);}
            for(int x:new int[]{-12,12}){Cylinder leg=new Cylinder(9,45);leg.setMaterial(dark);leg.setTranslateX(x);leg.setTranslateY(25);g.getChildren().add(leg);Box foot=new Box(20,10,28);foot.setMaterial(white);foot.setTranslateX(x);foot.setTranslateY(48);g.getChildren().add(foot);}
        }
        return g;
    }

    private VBox buildControlPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(18, 14, 18, 14));
        panel.setPrefWidth(285);
        panel.setMinWidth(250);
        panel.setMaxWidth(300);
        Label title = sectionTitle("ROBOT CONTROL");
        ComboBox<String> selector = new ComboBox<>(); selector.getItems().addAll("Humanoid Robot", "Explorer Robot", "Industrial Robot"); selector.setValue("Humanoid Robot"); selector.setMaxWidth(Double.MAX_VALUE); selector.setStyle(controlStyle());
        selector.setOnAction(e -> {
            if (carriedProp != null) {
                log("Drop the carried cargo before switching robot types.");
                selector.setValue(robot instanceof ExplorerRobot ? "Explorer Robot" :
                        robot instanceof IndustrialRobot ? "Industrial Robot" : "Humanoid Robot");
                return;
            }
            if (exploreTimeline != null) exploreTimeline.stop();
            String v=selector.getValue();
            robot = v.startsWith("Explorer")?new ExplorerRobot():v.startsWith("Industrial")?new IndustrialRobot():new HumanoidRobot();
            robot.setPosition(0,0);
            robotView.getChildren().setAll(createRobotModel(robot));
            updateRobotView();
            log("Robot switched to " + robot.getName() + ". Each type has its own capabilities.");
            refreshStatus();
        });
        VBox robotCard = card(new Label("ACTIVE ROBOT"), selector);
        Label movementTitle=sectionTitle("MOVEMENT");
        GridPane pad=new GridPane(); pad.setHgap(7);pad.setVgap(7);pad.setAlignment(Pos.CENTER);
        Button forward=controlButton("▲"); Button left=controlButton("◀"); Button stop=controlButton("■"); Button right=controlButton("▶"); Button back=controlButton("▼");
        pad.add(forward,1,0);pad.add(left,0,1);pad.add(stop,1,1);pad.add(right,2,1);pad.add(back,1,2);
        Button turnLeft=actionButton("↶  Turn left"); Button turnRight=actionButton("↷  Turn right");
        HBox turns=new HBox(8,turnLeft,turnRight); turns.setAlignment(Pos.CENTER);
        forward.setOnAction(e->moveRelative(1));back.setOnAction(e->moveRelative(-1));left.setOnAction(e->strafe(-1));right.setOnAction(e->strafe(1));
        turnLeft.setOnAction(e->rotateRobot(-15));turnRight.setOnAction(e->rotateRobot(15));
        stop.setOnAction(e -> stopRobot());
        speedSlider=new Slider(0.5,3,1); speedSlider.setShowTickLabels(true); speedSlider.setMajorTickUnit(1); speedSlider.valueProperty().addListener((o,a,b)->robot.setSpeed(b.doubleValue()));
        VBox movement=card(movementTitle,pad,turns,new Label("Movement speed"),speedSlider);
        Label actionsTitle=sectionTitle("ACTIONS");
        Button scan=actionButton("◎   Scan surroundings"); Button pick=actionButton("▣   Pick cargo"); Button drop=actionButton("□   Drop cargo"); Button recharge=actionButton("⚡   Recharge battery"); Button explore=actionButton("➤   Auto explore"); Button reset=actionButton("↺   Reset robot position");
        scan.setOnAction(e -> scanEnvironment());
        pick.setOnAction(e -> pickNearestCargo());
        drop.setOnAction(e -> dropCarriedCargo());
        recharge.setOnAction(e->{robot.getBattery().recharge();robot.setMode("IDLE");log("Battery fully recharged.");refreshStatus();});
        explore.setOnAction(e -> startAutoExplore());
        reset.setOnAction(e -> resetRobotPosition());
        VBox actions=card(actionsTitle,scan,pick,drop,recharge,explore,reset);
        Label statusTitle=sectionTitle("LIVE TELEMETRY");
        positionLabel=infoLabel("Position: X 0.0  /  Z 0.0");batteryLabel=infoLabel("Battery: 100%");modeLabel=infoLabel("Mode: IDLE");sensorLabel=infoLabel("Obstacle: Clear");cargoLabel=infoLabel("Cargo: Empty");
        batteryBar=new ProgressBar(1);batteryBar.setMaxWidth(Double.MAX_VALUE);batteryBar.setStyle("-fx-accent:#35d68b;");
        VBox status=card(statusTitle,positionLabel,batteryLabel,batteryBar,modeLabel,sensorLabel,cargoLabel);
        Label logTitle=sectionTitle("ACTIVITY LOG");activity=new TextArea();activity.setEditable(false);activity.setWrapText(true);activity.setPrefRowCount(5);activity.setStyle("-fx-control-inner-background:#081326;-fx-text-fill:#a9c2df;-fx-font-size:11px;");
        VBox logCard=card(logTitle,activity);
        Label navTitle=sectionTitle("DESTINATION NAVIGATION");
        destinationXField=new TextField("280"); destinationZField=new TextField("120");
        destinationXField.setPromptText("X coordinate"); destinationZField.setPromptText("Z coordinate");
        destinationXField.setStyle(controlStyle()); destinationZField.setStyle(controlStyle());
        Button navigate=actionButton("⌖  Plan route & navigate");
        navigate.setOnAction(e -> navigateToDestination());
        Button stopRoute=actionButton("■  Stop navigation");
        stopRoute.setOnAction(e -> stopRobot());
        VBox navigation=card(navTitle,new Label("Destination X"),destinationXField,new Label("Destination Z"),destinationZField,navigate,stopRoute);
        panel.getChildren().addAll(title,robotCard,movement,actions,navigation,status,logCard);return panel;
    }
    private VBox card(javafx.scene.Node... nodes) { VBox b=new VBox(9,nodes);b.setPadding(new Insets(13));b.setStyle("-fx-background-color:#0d1a2d;-fx-border-color:#203b5d;-fx-border-radius:10;-fx-background-radius:10;");return b; }
    private Label sectionTitle(String text){Label l=new Label(text);l.setStyle("-fx-text-fill:#64caff;-fx-font-size:12px;-fx-font-weight:bold;");return l;}
    private Label infoLabel(String text){Label l=new Label(text);l.setStyle("-fx-text-fill:#c6d7ed;-fx-font-size:11px;");return l;}
    private String controlStyle(){return "-fx-background-color:#142944;-fx-text-fill:#e8f4ff;";}
    private Button controlButton(String text){Button b=new Button(text);b.setPrefSize(54,42);b.setStyle("-fx-background-color:#123b68;-fx-text-fill:#65d2ff;-fx-font-size:18px;-fx-border-color:#247cb8;-fx-border-radius:7;-fx-background-radius:7;");return b;}
    private Button actionButton(String text){Button b=new Button(text);b.setMaxWidth(Double.MAX_VALUE);b.setAlignment(Pos.CENTER_LEFT);b.setPadding(new Insets(9));b.setStyle("-fx-background-color:#142b49;-fx-text-fill:#d8eaff;-fx-border-color:#25466c;-fx-border-radius:6;-fx-background-radius:6;");return b;}

    private void stopRobot() {
        if (exploreTimeline != null) exploreTimeline.stop();
        if (scanTimeline != null) scanTimeline.stop();
        robot.setMode("IDLE");
        log("Robot stopped by user.");
        refreshStatus();
    }

    // Explorer patrol: follows a long rectangular road loop and stops only when stopped,
    // blocked, out of battery, or the complete route has been visited.
    private void startAutoExplore() {
        if (!(robot instanceof ExplorerRobot)) {
            log("Auto Explore is an Explorer Robot capability. Select Explorer Robot first.");
            return;
        }
        List<double[]> route = new ArrayList<>();
        addSegment(route, 0, 0, 300, 0, 20);
        addSegment(route, 300, 0, 300, 150, 20);
        addSegment(route, 300, 150, -300, 150, 20);
        addSegment(route, -300, 150, -300, -150, 20);
        addSegment(route, -300, -150, 300, -150, 20);
        addSegment(route, 300, -150, 300, 0, 20);
        addSegment(route, 300, 0, 0, 0, 20);
        startRoute(route, "AUTO EXPLORE");
        log("Explorer patrol started: 7 road segments, full central-city loop.");
    }

    private void addSegment(List<double[]> points, double x1, double z1, double x2, double z2, double spacing) {
        int count=(int)Math.ceil(Math.max(Math.abs(x2-x1),Math.abs(z2-z1))/spacing);
        for(int i=1;i<=count;i++){
            double t=(double)i/count;
            points.add(new double[]{x1+(x2-x1)*t,z1+(z2-z1)*t});
        }
    }

    private void startRoute(List<double[]> route, String mode) {
        if (exploreTimeline != null) exploreTimeline.stop();
        if (route.isEmpty()) { log("No route available."); return; }
        activeRoute = route; routeTargetIndex=0; activeRouteName=mode;
        robot.setMode(mode);
        exploreTimeline = new Timeline(new KeyFrame(Duration.millis(170), event -> {
            if (routeTargetIndex >= activeRoute.size()) {
                exploreTimeline.stop(); robot.setMode("IDLE");
                log(activeRouteName + " completed successfully.");
                refreshStatus(); return;
            }
            double[] target=activeRoute.get(routeTargetIndex);
            double dx=target[0]-robot.getX(), dz=target[1]-robot.getZ();
            double distance=Math.hypot(dx,dz);
            if(distance < 2.5) { routeTargetIndex++; return; }
            double step=Math.min(10.0*robot.getSpeed(),distance);
            if(!advanceBy(dx/distance*step,dz/distance*step,true)){
                exploreTimeline.stop(); robot.setMode("ROUTE BLOCKED");
                log(activeRouteName + " stopped: route blocked or battery low.");
                refreshStatus(); return;
            }
        }));
        exploreTimeline.setCycleCount(Timeline.INDEFINITE);
        exploreTimeline.play();
    }

    private boolean advanceBy(double dx, double dz, boolean charge) {
        double nx=robot.getX()+dx, nz=robot.getZ()+dz;
        if(Math.abs(nx)>CITY_LIMIT_X || Math.abs(nz)>CITY_LIMIT_Z) return false;
        Obstacle hit=new CollisionDetector(obstacles).firstCollision(nx,nz);
        if(hit!=null) return false;
        if(charge && routeTargetIndex % 10 == 0 && !robot.getBattery().consume(1)) return false;
        robot.setHeading(Math.toDegrees(Math.atan2(dx,-dz))); robot.move(dx,dz); robot.setMode(activeRouteName);
        updateRobotView(); refreshStatus();
        return true;
    }

    private void navigateToDestination() {
        try {
            int tx=Integer.parseInt(destinationXField.getText().trim());
            int tz=Integer.parseInt(destinationZField.getText().trim());
            tx=Math.max(-300,Math.min(300,Math.round(tx/20f)*20));
            tz=Math.max(-150,Math.min(150,Math.round(tz/20f)*20));
            List<double[]> path=findGridPath((int)Math.round(robot.getX()/20)*20,
                    (int)Math.round(robot.getZ()/20)*20,tx,tz);
            if(path.isEmpty()){log("No safe route found. Choose another destination.");return;}
            startRoute(path,"NAVIGATION");
            log("A* grid route planned to X="+tx+", Z="+tz+" ("+path.size()+" nodes).");
        } catch(NumberFormatException ex) {
            log("Enter numeric X and Z destination coordinates.");
        }
    }

    // A* pathfinding on a 20-unit city grid, with building collision checks.
    private List<double[]> findGridPath(int sx,int sz,int tx,int tz) {
        GridPoint start=new GridPoint(sx,sz), goal=new GridPoint(tx,tz);
        ArrayList<GridPoint> open=new ArrayList<>(); open.add(start);
        Map<GridPoint,GridPoint> cameFrom=new HashMap<>();
        Map<GridPoint,Integer> gScore=new HashMap<>(); gScore.put(start,0);
        Set<GridPoint> closed=new HashSet<>();
        while(!open.isEmpty()){
            open.sort(Comparator.comparingInt(p->gScore.getOrDefault(p,Integer.MAX_VALUE)+Math.abs(p.x()-tx)+Math.abs(p.z()-tz)));
            GridPoint current=open.remove(0);
            if(current.equals(goal)){
                ArrayList<double[]> path=new ArrayList<>();
                GridPoint p=goal;
                while(!p.equals(start)){path.add(0,new double[]{p.x(),p.z()});p=cameFrom.get(p);if(p==null)return List.of();}
                return path;
            }
            if(!closed.add(current))continue;
            for(int[] d:new int[][]{{20,0},{-20,0},{0,20},{0,-20}}){
                GridPoint next=new GridPoint(current.x()+d[0],current.z()+d[1]);
                if(next.x() < -300 || next.x()>300 || next.z() < -150 || next.z()>150 || closed.contains(next))continue;
                if(new CollisionDetector(obstacles).firstCollision(next.x(),next.z())!=null)continue;
                int tentative=gScore.get(current)+20;
                if(tentative<gScore.getOrDefault(next,Integer.MAX_VALUE)){
                    cameFrom.put(next,current);gScore.put(next,tentative);if(!open.contains(next))open.add(next);
                }
            }
        }
        return List.of();
    }

    private void pickNearestCargo() {
        if (!(robot instanceof ObjectHandler handler)) {
            log("This robot has no cargo-handling capability."); return;
        }
        if (carriedProp != null) { log("Manipulator already carries " + carriedProp.name + "."); return; }
        CargoProp nearest=cargoProps.stream().min(Comparator.comparingDouble(c ->
                Math.hypot(c.node.getTranslateX()-robot.getX(),c.node.getTranslateZ()-robot.getZ()))).orElse(null);
        if(nearest==null || Math.hypot(nearest.node.getTranslateX()-robot.getX(),nearest.node.getTranslateZ()-robot.getZ())>65){
            log("No cargo in reach. Move close to a visible supply crate first."); return;
        }
        if(!robot.getBattery().consume(5)){log("Battery too low to operate manipulator.");refreshStatus();return;}
        if(!handler.pickObject(nearest.name)){log("Cargo handler is busy.");return;}
        carriedProp=nearest; cargoProps.remove(nearest); cityGeometry.getChildren().remove(nearest.node);
        nearest.node.setTranslateX(0); nearest.node.setTranslateY(-105); nearest.node.setTranslateZ(-28);
        robotView.getChildren().add(nearest.node);
        animateArm(true);
        log(robot.getType()+" picked a physical city cargo object: "+nearest.name+".");
        refreshStatus();
    }

    private void dropCarriedCargo() {
        if (!(robot instanceof ObjectHandler handler)) { log("This robot has no cargo-handling capability."); return; }
        if(carriedProp==null){log("No physical cargo is being carried.");return;}
        String name=handler.dropObject();
        CargoProp prop=carriedProp; carriedProp=null;
        robotView.getChildren().remove(prop.node);
        prop.node.setTranslateX(robot.getX()+28); prop.node.setTranslateY(23); prop.node.setTranslateZ(robot.getZ()+20);
        cityGeometry.getChildren().add(prop.node);
        cargoProps.add(prop);
        animateArm(false);
        log(name+" dropped into the city at the robot's current position.");
        refreshStatus();
    }

    private void animateArm(boolean picking) {
        if(industrialArmJoint==null)return;
        if(armTimeline!=null)armTimeline.stop();
        double from=industrialArmJoint.getAngle();
        double to=picking?35:-28;
        armTimeline=new Timeline(new KeyFrame(Duration.ZERO,new javafx.animation.KeyValue(industrialArmJoint.angleProperty(),from)),
                new KeyFrame(Duration.millis(260),new javafx.animation.KeyValue(industrialArmJoint.angleProperty(),to)),
                new KeyFrame(Duration.millis(520),new javafx.animation.KeyValue(industrialArmJoint.angleProperty(),-28)));
        armTimeline.play();
    }

    private void scanEnvironment() {
        if(!robot.getBattery().consume(3)){log("Battery too low to scan.");refreshStatus();return;}
        long nearbyCargo=cargoProps.stream().filter(c->Math.hypot(c.node.getTranslateX()-robot.getX(),c.node.getTranslateZ()-robot.getZ())<=220).count();
        long nearbyBuildings=obstacles.stream().filter(o->Math.hypot(o.x()-robot.getX(),o.z()-robot.getZ())<=260).count();
        if(robot instanceof ExplorerRobot && explorerScanner!=null){
            if(scanTimeline!=null)scanTimeline.stop();
            Rotate sweep=new Rotate(0,Rotate.Y_AXIS);explorerScanner.getTransforms().setAll(sweep);
            scanTimeline=new Timeline(new KeyFrame(Duration.ZERO,new javafx.animation.KeyValue(sweep.angleProperty(),0)),
                    new KeyFrame(Duration.millis(900),new javafx.animation.KeyValue(sweep.angleProperty(),360)));
            scanTimeline.play();
        }
        robot.setMode("SCANNING");
        log(robot.getType()+" scan: "+nearbyBuildings+" building sectors and "+nearbyCargo+" cargo objects detected within sensor range.");
        refreshStatus();
    }

    private boolean tryMove(double dx,double dz,boolean charge) {
        // Small, discrete steps keep manual control predictable and prevent key-repeat runaway.
        double step = 12.0 * robot.getSpeed();
        double nx = robot.getX() + dx * step;
        double nz = robot.getZ() + dz * step;
        if (Math.abs(nx) > CITY_LIMIT_X || Math.abs(nz) > CITY_LIMIT_Z) {
            log("Playable city-road boundary reached. Robot held inside the environment.");
            robot.setMode("BOUNDARY STOP");
            refreshStatus();
            return false;
        }
        Obstacle hit = new CollisionDetector(obstacles).firstCollision(nx, nz);
        if (hit != null) {
            log("Obstacle detected: " + hit.label() + ". Movement blocked.");
            robot.setMode("OBSTACLE ALERT");
            refreshStatus();
            return false;
        }
        if (charge && !robot.getBattery().consume(1)) {
            log("Battery depleted. Recharge to continue.");
            robot.setMode("LOW BATTERY");
            refreshStatus();
            return false;
        }
        robot.move(dx * step, dz * step);
        robot.setMode("MOVING");
        updateRobotView();
        refreshStatus();
        return true;
    }
    private void moveRelative(double direction) {
        double radians=Math.toRadians(robot.getHeading());
        double dx=Math.sin(radians)*direction;
        double dz=-Math.cos(radians)*direction;
        move(dx,dz);
    }

    private void strafe(double direction) {
        double radians=Math.toRadians(robot.getHeading());
        double dx=Math.cos(radians)*direction;
        double dz=Math.sin(radians)*direction;
        move(dx,dz);
    }

    private void move(double dx,double dz){
        if (tryMove(dx,dz,true)) log("Movement step completed.");
    }
    private void resetRobotPosition() {
        stopRobot();
        robot.setPosition(0, 0);
        robot.setMode("IDLE");
        updateRobotView();
        refreshStatus();
        log("Robot returned to the city starting point. City and camera remain fixed.");
    }
    private void rotateRobot(double degrees){if(!robot.getBattery().consume(1)){log("Battery depleted. Recharge to continue.");refreshStatus();return;}robot.rotate(degrees);robot.setMode("TURNING");updateRobotView();refreshStatus();log("Robot rotated by "+degrees+" degrees.");}
    private void updateRobotView() {
        // Enforce immovable city/camera invariants on every robot update.
        world.setTranslateX(0); world.setTranslateY(0); world.setTranslateZ(0);
        world.setRotate(0); world.setScaleX(1); world.setScaleY(1); world.setScaleZ(1);
        cityGeometry.setTranslateX(0); cityGeometry.setTranslateY(0); cityGeometry.setTranslateZ(0);
        cityGeometry.setRotate(0); cityGeometry.setScaleX(1); cityGeometry.setScaleY(1); cityGeometry.setScaleZ(1);

        // Only this node is allowed to move.
        robotView.setTranslateX(robot.getX());
        robotView.setTranslateZ(robot.getZ());
        robotView.setTranslateY(0);
        robotView.setRotationAxis(Rotate.Y_AXIS);
        robotView.setRotate(robot.getHeading());
        for (Rotate wheelSpin : explorerWheelRotations) wheelSpin.setAngle(wheelSpin.getAngle() + 24);
    }
    private void refreshStatus(){if(positionLabel==null)return;positionLabel.setText(String.format("Position: X %.0f  /  Z %.0f",robot.getX(),robot.getZ()));batteryLabel.setText("Battery: "+robot.getBattery().getLevel()+"%");batteryBar.setProgress(robot.getBattery().getLevel()/100.0);modeLabel.setText("Mode: "+robot.getMode());Obstacle near=new CollisionDetector(obstacles).firstCollision(robot.getX()+22,robot.getZ());sensorLabel.setText("Obstacle: "+(near==null?"Clear":"Detected nearby"));String cargo=carriedProp==null?"Empty":carriedProp.name;cargoLabel.setText("Cargo: "+cargo);}
    private void log(String message){String line="["+LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"] "+message;logs.add(line);if(activity!=null){activity.appendText(line+"\n");activity.setScrollTop(Double.MAX_VALUE);}}
    private HBox buildFooter(){Label footer=new Label("ROBOFORGE  •  JAVA OOP SEMESTER PROJECT  •  SIMULATED ROBOTICS — NOT REAL HARDWARE");footer.setStyle("-fx-text-fill:#526b8b;-fx-font-size:10px;");HBox b=new HBox(footer);b.setPadding(new Insets(10,20,12,20));b.setStyle("-fx-background-color:#091326;-fx-border-color:#1a2c49;-fx-border-width:1 0 0 0;");return b;}
    public static void main(String[] args){launch(args);}
}
