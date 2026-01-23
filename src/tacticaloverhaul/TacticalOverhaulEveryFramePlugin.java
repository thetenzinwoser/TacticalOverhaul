package tacticaloverhaul;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tactical Overhaul - Camera and input controller
 *
 * Controls:
 * - Backtick (`) to toggle tactical view
 * - Scroll wheel to zoom in/out
 * - Left-click on friendly ship to select it (not your own ship)
 * - Right-click on empty space to order move
 * - Right-click on enemy ship to order attack
 * - Right-click + drag to pan camera
 * - Arrow keys to pan
 * - Home or C to re-center on player ship
 * - Escape to deselect
 */
public class TacticalOverhaulEveryFramePlugin extends BaseEveryFrameCombatPlugin {

    private static final int TOGGLE_KEY = Keyboard.KEY_GRAVE;

    private boolean initialized = false;
    private boolean tacticalModeActive = false;
    private boolean toggleKeyWasPressed = false;

    // Camera control
    private float currentZoom = 1.0f;
    private float targetZoom = 1.0f;
    private static final float ZOOMED_IN = 1.0f;
    private static final float ZOOMED_OUT = 0.35f;
    private static final float ZOOM_SPEED = 3.0f;
    private static final float MIN_ZOOM = 0.15f;
    private static final float MAX_ZOOM = 1.5f;
    private static final float SCROLL_ZOOM_FACTOR = 0.1f;

    // Panning
    private Vector2f cameraOffset = new Vector2f(0, 0);
    private static final float PAN_SPEED = 1500f;
    private static final float EDGE_PAN_ZONE = 50f;
    private static final float DRAG_PAN_SENSITIVITY = 2.5f;

    // Mouse drag state
    private boolean rightMouseDragging = false;
    private boolean rightMouseWasDown = false;
    private int rightMouseStartX = 0;
    private int rightMouseStartY = 0;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private static final int CLICK_THRESHOLD = 5;

    // Left click state
    private boolean leftMouseWasDown = false;

    // Escape key state
    private boolean escapeWasPressed = false;

    // Ship selection (supports multi-select with Shift+click)
    private List<ShipAPI> selectedShips = new ArrayList<>();

    // Store original state
    private float originalZoom = 1.0f;

    // Reference to render plugin
    private TacticalOverhaulCombatPlugin renderPlugin;

    // Command visualization
    private Vector2f lastCommandTarget = null;
    private ShipAPI lastCommandAttackTarget = null;
    private float commandDisplayTime = 0f;
    private static final float COMMAND_DISPLAY_DURATION = 2.0f;

    // Message display
    private String displayMessage = null;
    private float messageDisplayTime = 0f;
    private static final float MESSAGE_DISPLAY_DURATION = 3.0f;

    @Override
    public void init(CombatEngineAPI engine) {
        initialized = false;
        tacticalModeActive = false;
        toggleKeyWasPressed = false;
        currentZoom = 1.0f;
        targetZoom = 1.0f;
        cameraOffset.set(0, 0);
        rightMouseDragging = false;
        rightMouseWasDown = false;
        leftMouseWasDown = false;
        escapeWasPressed = false;
        selectedShips = new ArrayList<>();
        lastCommandTarget = null;
        lastCommandAttackTarget = null;
        commandDisplayTime = 0f;
        displayMessage = null;
        messageDisplayTime = 0f;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        if (engine.isSimulation()) return;

        // Register rendering plugin once
        if (!initialized) {
            renderPlugin = new TacticalOverhaulCombatPlugin();
            engine.addLayeredRenderingPlugin(renderPlugin);
            initialized = true;
        }

        // Toggle tactical mode with backtick key
        boolean toggleKeyPressed = Keyboard.isKeyDown(TOGGLE_KEY);
        if (toggleKeyPressed && !toggleKeyWasPressed) {
            tacticalModeActive = !tacticalModeActive;

            ViewportAPI viewport = engine.getViewport();
            if (tacticalModeActive) {
                originalZoom = viewport.getViewMult();
                targetZoom = ZOOMED_OUT;
                currentZoom = originalZoom;
                cameraOffset.set(0, 0);
                viewport.setExternalControl(true);
            } else {
                targetZoom = originalZoom;
                cameraOffset.set(0, 0);
                selectedShips.clear();
            }
        }
        toggleKeyWasPressed = toggleKeyPressed;

        // Deselect with Escape (with toggle to prevent repeated firing)
        boolean escapePressed = Keyboard.isKeyDown(Keyboard.KEY_ESCAPE);
        if (tacticalModeActive && escapePressed && !escapeWasPressed) {
            selectedShips.clear();
        }
        escapeWasPressed = escapePressed;

        // Validate selected ships still exist and are alive
        Iterator<ShipAPI> iter = selectedShips.iterator();
        while (iter.hasNext()) {
            ShipAPI ship = iter.next();
            if (!ship.isAlive() || ship.isHulk()) {
                iter.remove();
            }
        }

        // Handle camera and input when in tactical mode
        ViewportAPI viewport = engine.getViewport();

        if (tacticalModeActive) {
            handleMouseInput(engine, viewport);
            handleScrollWheel(events);
            handlePanning(amount, viewport);

            // Get player ship position as base
            ShipAPI playerShip = engine.getPlayerShip();
            Vector2f basePos;
            if (playerShip != null && playerShip.isAlive()) {
                basePos = playerShip.getLocation();
            } else {
                basePos = viewport.getCenter();
            }

            // Apply camera offset for panning
            Vector2f newCenter = new Vector2f(basePos.x + cameraOffset.x, basePos.y + cameraOffset.y);
            viewport.setCenter(newCenter);
        }

        // Smoothly interpolate zoom
        if (Math.abs(currentZoom - targetZoom) > 0.001f) {
            float delta = targetZoom - currentZoom;
            currentZoom += delta * Math.min(1.0f, ZOOM_SPEED * amount);
            viewport.setViewMult(currentZoom);
        } else if (!tacticalModeActive && Math.abs(currentZoom - originalZoom) < 0.01f) {
            viewport.setExternalControl(false);
            currentZoom = originalZoom;
        }

        // Update command display timers
        if (commandDisplayTime > 0) {
            commandDisplayTime -= amount;
            if (commandDisplayTime <= 0) {
                lastCommandTarget = null;
                lastCommandAttackTarget = null;
            }
        }
        if (messageDisplayTime > 0) {
            messageDisplayTime -= amount;
            if (messageDisplayTime <= 0) {
                displayMessage = null;
            }
        }

        // Update render plugin
        if (renderPlugin != null) {
            renderPlugin.setTacticalModeActive(tacticalModeActive);
            renderPlugin.setSelectedShips(selectedShips);
            renderPlugin.setCommandTarget(lastCommandTarget, lastCommandAttackTarget, commandDisplayTime > 0);
            renderPlugin.setMessage(displayMessage);
        }
    }

    private void handleScrollWheel(List<InputEventAPI> events) {
        // Process scroll wheel events from InputEventAPI
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isMouseScrollEvent()) {
                int dWheel = event.getEventValue();
                if (dWheel != 0) {
                    // Scroll up (positive) = zoom in, scroll down (negative) = zoom out
                    float zoomChange = (dWheel > 0 ? 1 : -1) * SCROLL_ZOOM_FACTOR;
                    targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetZoom + zoomChange));
                }
                // Consume to prevent game's default zoom handling
                event.consume();
            }
        }

        // Fallback: also check +/- keys for zoom (in case scroll doesn't work)
        if (Keyboard.isKeyDown(Keyboard.KEY_EQUALS) || Keyboard.isKeyDown(Keyboard.KEY_ADD)) {
            targetZoom = Math.min(MAX_ZOOM, targetZoom + SCROLL_ZOOM_FACTOR * 0.05f);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_MINUS) || Keyboard.isKeyDown(Keyboard.KEY_SUBTRACT)) {
            targetZoom = Math.max(MIN_ZOOM, targetZoom - SCROLL_ZOOM_FACTOR * 0.05f);
        }
    }

    private void handleMouseInput(CombatEngineAPI engine, ViewportAPI viewport) {
        int mouseX = Mouse.getX();
        int mouseY = Mouse.getY();

        // Handle left click for ship selection (with Shift for multi-select)
        boolean leftMouseDown = Mouse.isButtonDown(0);
        boolean shiftHeld = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        if (leftMouseDown && !leftMouseWasDown) {
            Vector2f worldPos = screenToWorld(mouseX, mouseY, viewport);
            ShipAPI clickedShip = getShipAtLocation(engine, worldPos);

            if (clickedShip != null && clickedShip.getOwner() == 0) {
                // Clicked on friendly ship
                if (shiftHeld) {
                    // Shift+click: toggle selection
                    if (selectedShips.contains(clickedShip)) {
                        selectedShips.remove(clickedShip);
                    } else {
                        selectedShips.add(clickedShip);
                    }
                } else {
                    // Regular click: replace selection
                    selectedShips.clear();
                    selectedShips.add(clickedShip);
                }
            } else if (clickedShip == null && !shiftHeld) {
                // Clicked on empty space without Shift - deselect all
                selectedShips.clear();
            }
        }
        leftMouseWasDown = leftMouseDown;

        // Handle right click for commands (with drag detection)
        boolean rightMouseDown = Mouse.isButtonDown(1);

        if (rightMouseDown && !rightMouseWasDown) {
            rightMouseStartX = mouseX;
            rightMouseStartY = mouseY;
            rightMouseDragging = false;
        }

        if (rightMouseDown) {
            int dx = Math.abs(mouseX - rightMouseStartX);
            int dy = Math.abs(mouseY - rightMouseStartY);
            if (dx > CLICK_THRESHOLD || dy > CLICK_THRESHOLD) {
                rightMouseDragging = true;
            }
        }

        if (!rightMouseDown && rightMouseWasDown) {
            if (!rightMouseDragging && !selectedShips.isEmpty()) {
                Vector2f worldPos = screenToWorld(mouseX, mouseY, viewport);
                issueCommandToAll(engine, worldPos);
            }
            rightMouseDragging = false;
        }

        rightMouseWasDown = rightMouseDown;
    }

    private void issueCommandToAll(CombatEngineAPI engine, Vector2f targetPos) {
        if (selectedShips.isEmpty()) return;

        // Check what we're targeting
        ShipAPI targetShip = getShipAtLocation(engine, targetPos);
        boolean isAttackCommand = (targetShip != null && targetShip.getOwner() == 1 && !targetShip.isFighter());

        // Set visualization (for the group)
        if (isAttackCommand) {
            lastCommandTarget = null;
            lastCommandAttackTarget = targetShip;
        } else {
            lastCommandTarget = new Vector2f(targetPos);
            lastCommandAttackTarget = null;
        }
        commandDisplayTime = COMMAND_DISPLAY_DURATION;

        // Issue command to all selected ships
        for (ShipAPI ship : selectedShips) {
            issueCommandToShip(engine, ship, targetPos, targetShip, isAttackCommand);
        }
    }

    private void issueCommandToShip(CombatEngineAPI engine, ShipAPI selectedShip, Vector2f targetPos,
                                     ShipAPI targetShip, boolean isAttackCommand) {
        ShipAPI playerShip = engine.getPlayerShip();
        boolean isPlayerShip = (selectedShip == playerShip);

        CombatFleetManagerAPI fleetManager = engine.getFleetManager(0);
        if (fleetManager == null) return;

        DeployedFleetMemberAPI deployedMember = fleetManager.getDeployedFleetMember(selectedShip);
        if (deployedMember == null) {
            deployedMember = fleetManager.getDeployedFleetMemberEvenIfDisabled(selectedShip);
        }
        if (deployedMember == null) return;

        CombatTaskManagerAPI taskManager = fleetManager.getTaskManager(false);
        if (taskManager == null) return;

        if (isAttackCommand && targetShip != null) {
            // Issue attack order
            CombatFleetManagerAPI enemyFleetManager = engine.getFleetManager(1);
            if (enemyFleetManager != null) {
                DeployedFleetMemberAPI enemyDeployed = enemyFleetManager.getDeployedFleetMember(targetShip);
                if (enemyDeployed == null) {
                    enemyDeployed = enemyFleetManager.getDeployedFleetMemberEvenIfDisabled(targetShip);
                }
                if (enemyDeployed != null) {
                    CombatFleetManagerAPI.AssignmentInfo assignment = taskManager.createAssignment(
                        CombatAssignmentType.INTERCEPT, enemyDeployed, false);
                    taskManager.giveAssignment(deployedMember, assignment, false);
                }
            }
        } else {
            // Issue move order using DEFEND on a waypoint
            AssignmentTargetAPI waypoint = fleetManager.createWaypoint(targetPos, false);
            CombatFleetManagerAPI.AssignmentInfo assignment = taskManager.createAssignment(
                CombatAssignmentType.DEFEND, waypoint, false);
            taskManager.giveAssignment(deployedMember, assignment, false);
        }

        // If commanding the player's ship, show message about autopilot
        if (isPlayerShip) {
            displayMessage = "Press U to enable autopilot for your ship";
            messageDisplayTime = MESSAGE_DISPLAY_DURATION;

            // Show floating text at ship location
            engine.addFloatingText(
                selectedShip.getLocation(),
                "Press U for autopilot",
                25f,
                new Color(255, 255, 100),
                selectedShip,
                0.5f,
                2.0f
            );
        }
    }

    private ShipAPI getShipAtLocation(CombatEngineAPI engine, Vector2f worldPos) {
        ShipAPI closest = null;
        float closestDist = Float.MAX_VALUE;

        for (ShipAPI ship : engine.getShips()) {
            if (ship.isHulk() || ship.isShuttlePod() || ship.isFighter()) continue;

            float dist = getDistance(ship.getLocation(), worldPos);
            float hitRadius = ship.getCollisionRadius();

            if (dist < hitRadius && dist < closestDist) {
                closest = ship;
                closestDist = dist;
            }
        }

        return closest;
    }

    private float getDistance(Vector2f a, Vector2f b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private Vector2f screenToWorld(int screenX, int screenY, ViewportAPI viewport) {
        float worldX = viewport.convertScreenXToWorldX(screenX);
        float worldY = viewport.convertScreenYToWorldY(screenY);
        return new Vector2f(worldX, worldY);
    }

    private void handlePanning(float amount, ViewportAPI viewport) {
        int mouseX = Mouse.getX();
        int mouseY = Mouse.getY();
        int screenWidth = Display.getWidth();
        int screenHeight = Display.getHeight();

        // Right mouse button drag panning
        boolean rightMouseDown = Mouse.isButtonDown(1);

        if (rightMouseDown && rightMouseDragging) {
            float deltaX = (mouseX - lastMouseX) * DRAG_PAN_SENSITIVITY / currentZoom;
            float deltaY = (mouseY - lastMouseY) * DRAG_PAN_SENSITIVITY / currentZoom;
            cameraOffset.x -= deltaX;
            cameraOffset.y -= deltaY;
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // Edge-of-screen panning (only when not dragging)
        if (!rightMouseDragging) {
            float panX = 0;
            float panY = 0;
            float adjustedPanSpeed = PAN_SPEED / currentZoom;

            if (mouseX < EDGE_PAN_ZONE) {
                panX = -adjustedPanSpeed * amount;
            } else if (mouseX > screenWidth - EDGE_PAN_ZONE) {
                panX = adjustedPanSpeed * amount;
            }

            if (mouseY < EDGE_PAN_ZONE) {
                panY = -adjustedPanSpeed * amount;
            } else if (mouseY > screenHeight - EDGE_PAN_ZONE) {
                panY = adjustedPanSpeed * amount;
            }

            cameraOffset.x += panX;
            cameraOffset.y += panY;
        }

        // Arrow keys for panning
        float keyPanSpeed = PAN_SPEED / currentZoom * amount;

        if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
            cameraOffset.x -= keyPanSpeed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
            cameraOffset.x += keyPanSpeed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
            cameraOffset.y -= keyPanSpeed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
            cameraOffset.y += keyPanSpeed;
        }

        // Re-center on player ship
        if (Keyboard.isKeyDown(Keyboard.KEY_HOME) || Keyboard.isKeyDown(Keyboard.KEY_C)) {
            cameraOffset.set(0, 0);
        }
    }
}
