package tacticaloverhaul;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
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

    // Panning offset (for right-click drag)
    private Vector2f cameraOffset = new Vector2f(0, 0);

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

        // In tactical mode, disable player ship control to prevent click-to-switch
        if (tacticalModeActive) {
            CombatUIAPI combatUI = engine.getCombatUI();
            if (combatUI != null) {
                // This prevents the game from processing player inputs like click-to-switch
                combatUI.setDisablePlayerShipControlOneFrame(true);
            }
        }

        // Toggle tactical mode with backtick key
        boolean toggleKeyPressed = Keyboard.isKeyDown(TOGGLE_KEY);
        if (toggleKeyPressed && !toggleKeyWasPressed) {
            tacticalModeActive = !tacticalModeActive;

            if (tacticalModeActive) {
                cameraOffset.set(0, 0);
                // Don't take external control - let game handle zoom normally
            } else {
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
            // Let game handle zoom normally - no custom zoom handling
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

}
