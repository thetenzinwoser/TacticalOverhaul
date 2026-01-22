package tacticaloverhaul;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * Tactical Overhaul - Camera and input controller
 *
 * Controls:
 * - Backtick (`) to toggle tactical view
 * - Mouse to screen edges to pan
 * - Middle mouse drag to pan
 * - Arrow keys to pan
 * - Home or C to re-center on player ship
 */
public class TacticalOverhaulEveryFramePlugin extends BaseEveryFrameCombatPlugin {

    // Use backtick/grave key (`) for toggle - less intrusive than Tab
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

    // Panning
    private Vector2f cameraOffset = new Vector2f(0, 0);
    private static final float PAN_SPEED = 1500f;
    private static final float EDGE_PAN_ZONE = 50f;
    private static final float DRAG_PAN_SENSITIVITY = 2.5f;

    // Middle mouse drag state
    private boolean middleMouseDragging = false;
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    // Store original state
    private float originalZoom = 1.0f;

    // Reference to render plugin
    private TacticalOverhaulCombatPlugin renderPlugin;

    @Override
    public void init(CombatEngineAPI engine) {
        initialized = false;
        tacticalModeActive = false;
        toggleKeyWasPressed = false;
        currentZoom = 1.0f;
        targetZoom = 1.0f;
        cameraOffset.set(0, 0);
        middleMouseDragging = false;
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
                cameraOffset.set(0, 0);
                viewport.setExternalControl(true);
            } else {
                targetZoom = originalZoom;
                cameraOffset.set(0, 0);
            }
        }
        toggleKeyWasPressed = toggleKeyPressed;

        // Handle camera when in tactical mode
        ViewportAPI viewport = engine.getViewport();

        if (tacticalModeActive) {
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

        // Update render plugin
        if (renderPlugin != null) {
            renderPlugin.setTacticalModeActive(tacticalModeActive);
        }
    }

    private void handlePanning(float amount, ViewportAPI viewport) {
        int mouseX = Mouse.getX();
        int mouseY = Mouse.getY();
        int screenWidth = Display.getWidth();
        int screenHeight = Display.getHeight();

        // Right mouse button drag panning
        boolean rightMouseDown = Mouse.isButtonDown(1); // 0=left, 1=right, 2=middle

        if (rightMouseDown) {
            if (middleMouseDragging) {
                float deltaX = (mouseX - lastMouseX) * DRAG_PAN_SENSITIVITY / currentZoom;
                float deltaY = (mouseY - lastMouseY) * DRAG_PAN_SENSITIVITY / currentZoom;
                cameraOffset.x -= deltaX;
                cameraOffset.y -= deltaY;
            }
            middleMouseDragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else {
            middleMouseDragging = false;
        }

        // Edge-of-screen panning (only when not dragging)
        if (!middleMouseDragging) {
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
