package tacticaloverhaul;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.opengl.GL11;

import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Combat plugin that renders an enhanced tactical view.
 * Features:
 * - Dim overlay for better contrast
 * - Ship indicators with facing
 * - Weapon range circles
 * - Objective markers
 */
public class TacticalOverhaulCombatPlugin extends BaseCombatLayeredRenderingPlugin {

    private boolean tacticalModeActive = false;
    private List<ShipAPI> selectedShips = new ArrayList<>();
    private float overlayAlpha = 0f;
    private float selectionPulse = 0f; // For pulsing selection indicator
    private static final float OVERLAY_TARGET_ALPHA = 0.4f;
    private static final float OVERLAY_FADE_SPEED = 4.0f;

    // Command visualization
    private Vector2f commandTarget = null;
    private ShipAPI commandAttackTarget = null;
    private boolean showCommand = false;
    private String displayMessage = null;

    // Cached colors for performance
    private static final Color PLAYER_FLEET_COLOR = new Color(100, 200, 255);
    private static final Color ENEMY_COLOR = new Color(255, 100, 100);
    private static final Color NEUTRAL_COLOR = new Color(200, 200, 200);
    private static final Color SELECTION_COLOR = new Color(0, 255, 200);
    private static final Color WAYPOINT_COLOR = new Color(100, 255, 150);
    private static final Color ATTACK_LINE_COLOR = new Color(255, 100, 100);
    private static final Color FLUX_CRITICAL = new Color(255, 50, 50);
    private static final Color FLUX_WARNING = new Color(255, 200, 50);
    private static final Color FLUX_NORMAL = new Color(100, 255, 100);
    private static final Color MESSAGE_COLOR = new Color(255, 255, 200);

    public void setTacticalModeActive(boolean active) {
        this.tacticalModeActive = active;
    }

    public void setSelectedShips(List<ShipAPI> ships) {
        this.selectedShips = ships != null ? ships : new ArrayList<>();
    }

    public void setCommandTarget(Vector2f target, ShipAPI attackTarget, boolean show) {
        this.commandTarget = target;
        this.commandAttackTarget = attackTarget;
        this.showCommand = show;
    }

    public void setMessage(String message) {
        this.displayMessage = message;
    }

    @Override
    public void advance(float amount) {
        // Smoothly fade the overlay in/out
        float targetAlpha = tacticalModeActive ? OVERLAY_TARGET_ALPHA : 0f;
        if (Math.abs(overlayAlpha - targetAlpha) > 0.001f) {
            float delta = targetAlpha - overlayAlpha;
            overlayAlpha += delta * Math.min(1.0f, OVERLAY_FADE_SPEED * amount);
        }

        // Pulse animation for selection
        selectionPulse += amount * 3f;
        if (selectionPulse > Math.PI * 2) {
            selectionPulse -= Math.PI * 2;
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        // Don't render if not in tactical mode and fully faded out
        if (!tacticalModeActive && overlayAlpha < 0.001f) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        // Set up OpenGL
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Draw dimming overlay across the entire visible area
        if (overlayAlpha > 0.001f) {
            drawDimOverlay(viewport);
        }

        // Only draw tactical elements when overlay is visible enough
        if (overlayAlpha > 0.1f) {
            float elementAlpha = Math.min(1f, overlayAlpha / OVERLAY_TARGET_ALPHA);
            drawTacticalElements(engine, elementAlpha);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    private void drawDimOverlay(ViewportAPI viewport) {
        float llx = viewport.getLLX();
        float lly = viewport.getLLY();
        float w = viewport.getVisibleWidth();
        float h = viewport.getVisibleHeight();

        GL11.glColor4f(0f, 0f, 0f, overlayAlpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(llx, lly);
        GL11.glVertex2f(llx + w, lly);
        GL11.glVertex2f(llx + w, lly + h);
        GL11.glVertex2f(llx, lly + h);
        GL11.glEnd();
    }

    private void drawTacticalElements(CombatEngineAPI engine, float alpha) {
        List<ShipAPI> ships = engine.getShips();

        for (ShipAPI ship : ships) {
            if (ship.isHulk() || ship.isShuttlePod()) continue;

            float x = ship.getLocation().x;
            float y = ship.getLocation().y;
            float radius = ship.getCollisionRadius();

            // Choose color based on owner (using cached colors)
            Color baseColor;
            if (ship.getOwner() == 0) {
                baseColor = PLAYER_FLEET_COLOR;
            } else if (ship.getOwner() == 1) {
                baseColor = ENEMY_COLOR;
            } else {
                baseColor = NEUTRAL_COLOR;
            }

            // Apply alpha
            Color color = new Color(baseColor.getRed(), baseColor.getGreen(),
                                    baseColor.getBlue(), (int)(180 * alpha));

            // Draw circle around ship
            drawCircle(x, y, radius * 1.5f, color);

            // Draw facing indicator
            float facing = ship.getFacing();
            float lineLength = radius * 2f;
            float endX = x + (float) Math.cos(Math.toRadians(facing)) * lineLength;
            float endY = y + (float) Math.sin(Math.toRadians(facing)) * lineLength;
            drawLine(x, y, endX, endY, color);

            // Draw velocity vector (where ship is heading)
            if (ship.getVelocity().length() > 10f) {
                float velScale = 0.5f; // Scale down velocity for display
                float velEndX = x + ship.getVelocity().x * velScale;
                float velEndY = y + ship.getVelocity().y * velScale;
                Color velColor = new Color(baseColor.getRed(), baseColor.getGreen(),
                                           baseColor.getBlue(), (int)(100 * alpha));
                drawLine(x, y, velEndX, velEndY, velColor);
                // Draw arrowhead
                drawArrowHead(velEndX, velEndY, ship.getVelocity(), velColor);
            }

            // Draw weapon range for larger ships
            if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP ||
                ship.getHullSize() == ShipAPI.HullSize.CRUISER) {
                float maxRange = getMaxWeaponRange(ship);
                if (maxRange > 0) {
                    Color rangeColor = new Color(baseColor.getRed(), baseColor.getGreen(),
                                                  baseColor.getBlue(), (int)(40 * alpha));
                    drawCircle(x, y, maxRange, rangeColor);
                }
            }

            // Draw flux indicator (arc around ship)
            float fluxLevel = ship.getFluxLevel();
            if (fluxLevel > 0.01f) {
                Color baseFluxColor;
                int fluxAlpha;
                if (fluxLevel > 0.8f) {
                    baseFluxColor = FLUX_CRITICAL;
                    fluxAlpha = (int)(200 * alpha);
                } else if (fluxLevel > 0.5f) {
                    baseFluxColor = FLUX_WARNING;
                    fluxAlpha = (int)(150 * alpha);
                } else {
                    baseFluxColor = FLUX_NORMAL;
                    fluxAlpha = (int)(100 * alpha);
                }
                Color fluxColor = new Color(baseFluxColor.getRed(), baseFluxColor.getGreen(),
                                            baseFluxColor.getBlue(), fluxAlpha);
                drawArc(x, y, radius * 1.8f, 90f, 90f + fluxLevel * 360f, fluxColor);
            }

            // Draw ship class label for non-fighters
            if (!ship.isFighter()) {
                String label = getHullSizeLabel(ship.getHullSize());
                // Note: Text rendering would require LWJGL font rendering which is complex
                // For now we use symbols: small dot for frigate, larger for destroyer, etc.
                drawShipSizeIndicator(x, y - radius * 1.7f, ship.getHullSize(), color, alpha);
            }
        }

        // Draw objectives
        for (BattleObjectiveAPI objective : engine.getObjectives()) {
            float x = objective.getLocation().x;
            float y = objective.getLocation().y;

            // Color based on who controls it
            Color objColor;
            int owner = objective.getOwner();
            if (owner == 0) {
                objColor = new Color(100, 200, 255, (int)(150 * alpha));
            } else if (owner == 1) {
                objColor = new Color(255, 100, 100, (int)(150 * alpha));
            } else {
                objColor = new Color(255, 255, 100, (int)(150 * alpha)); // Neutral/contested
            }

            drawCircle(x, y, 80f, objColor);
            drawCircle(x, y, 120f, new Color(objColor.getRed(), objColor.getGreen(),
                                              objColor.getBlue(), (int)(80 * alpha)));
        }

        // Draw selection indicators for all selected ships
        for (ShipAPI selectedShip : selectedShips) {
            if (selectedShip != null && selectedShip.isAlive()) {
                drawSelectionIndicator(selectedShip, alpha);

                // Draw command visualization from each selected ship
                if (showCommand) {
                    float shipX = selectedShip.getLocation().x;
                    float shipY = selectedShip.getLocation().y;

                    if (commandTarget != null) {
                        // Draw line from ship to waypoint
                        Color lineColor = new Color(WAYPOINT_COLOR.getRed(), WAYPOINT_COLOR.getGreen(),
                                                    WAYPOINT_COLOR.getBlue(), (int)(150 * alpha));
                        drawDashedLine(shipX, shipY, commandTarget.x, commandTarget.y, lineColor);
                    }

                    if (commandAttackTarget != null && commandAttackTarget.isAlive()) {
                        // Draw attack line from ship to target
                        float targetX = commandAttackTarget.getLocation().x;
                        float targetY = commandAttackTarget.getLocation().y;
                        Color lineColor = new Color(ATTACK_LINE_COLOR.getRed(), ATTACK_LINE_COLOR.getGreen(),
                                                    ATTACK_LINE_COLOR.getBlue(), (int)(200 * alpha));
                        drawDashedLine(shipX, shipY, targetX, targetY, lineColor);
                    }
                }
            }
        }

        // Draw command target markers once (not per ship)
        if (showCommand && !selectedShips.isEmpty()) {
            if (commandTarget != null) {
                drawWaypoint(commandTarget.x, commandTarget.y, alpha);
            }
            if (commandAttackTarget != null && commandAttackTarget.isAlive()) {
                float targetX = commandAttackTarget.getLocation().x;
                float targetY = commandAttackTarget.getLocation().y;
                drawAttackMarker(targetX, targetY, alpha);
            }
        }
    }

    private void drawSelectionIndicator(ShipAPI ship, float alpha) {
        float x = ship.getLocation().x;
        float y = ship.getLocation().y;
        float radius = ship.getCollisionRadius();

        // Pulsing effect
        float pulse = 0.7f + 0.3f * (float) Math.sin(selectionPulse);
        int pulseAlpha = (int) (255 * alpha * pulse);

        // Selection color - bright green/cyan
        Color selectColor = new Color(0, 255, 200, pulseAlpha);

        // Draw multiple rings for emphasis
        GL11.glLineWidth(3f);
        drawCircle(x, y, radius * 2.0f, selectColor);
        GL11.glLineWidth(2f);
        drawCircle(x, y, radius * 2.3f, new Color(0, 255, 200, pulseAlpha / 2));
        GL11.glLineWidth(1f);

        // Draw corner brackets for extra visibility
        float bracketSize = radius * 0.8f;
        float bracketOffset = radius * 1.8f;
        Color bracketColor = new Color(0, 255, 200, (int)(200 * alpha));

        // Top-left bracket
        drawLine(x - bracketOffset, y + bracketOffset - bracketSize, x - bracketOffset, y + bracketOffset, bracketColor);
        drawLine(x - bracketOffset, y + bracketOffset, x - bracketOffset + bracketSize, y + bracketOffset, bracketColor);

        // Top-right bracket
        drawLine(x + bracketOffset, y + bracketOffset - bracketSize, x + bracketOffset, y + bracketOffset, bracketColor);
        drawLine(x + bracketOffset, y + bracketOffset, x + bracketOffset - bracketSize, y + bracketOffset, bracketColor);

        // Bottom-left bracket
        drawLine(x - bracketOffset, y - bracketOffset + bracketSize, x - bracketOffset, y - bracketOffset, bracketColor);
        drawLine(x - bracketOffset, y - bracketOffset, x - bracketOffset + bracketSize, y - bracketOffset, bracketColor);

        // Bottom-right bracket
        drawLine(x + bracketOffset, y - bracketOffset + bracketSize, x + bracketOffset, y - bracketOffset, bracketColor);
        drawLine(x + bracketOffset, y - bracketOffset, x + bracketOffset - bracketSize, y - bracketOffset, bracketColor);
    }

    private float getMaxWeaponRange(ShipAPI ship) {
        float maxRange = 0;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getRange() > maxRange && !weapon.isDecorative()) {
                maxRange = weapon.getRange();
            }
        }
        return maxRange;
    }

    private void drawCircle(float cx, float cy, float radius, Color color) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                       color.getBlue() / 255f, color.getAlpha() / 255f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            GL11.glVertex2f(x, y);
        }
        GL11.glEnd();
    }

    private void drawArc(float cx, float cy, float radius, float startAngle, float endAngle, Color color) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                       color.getBlue() / 255f, color.getAlpha() / 255f);
        GL11.glLineWidth(3f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        int segments = (int) Math.max(8, Math.abs(endAngle - startAngle) / 5);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(startAngle + (endAngle - startAngle) * i / segments);
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            GL11.glVertex2f(x, y);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
    }

    private void drawLine(float x1, float y1, float x2, float y2, Color color) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                       color.getBlue() / 255f, color.getAlpha() / 255f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }

    private void drawArrowHead(float tipX, float tipY, org.lwjgl.util.vector.Vector2f direction, Color color) {
        if (direction.length() < 1f) return;

        float angle = (float) Math.atan2(direction.y, direction.x);
        float arrowSize = 15f;
        float arrowAngle = (float) Math.toRadians(150);

        float x1 = tipX + (float) Math.cos(angle + arrowAngle) * arrowSize;
        float y1 = tipY + (float) Math.sin(angle + arrowAngle) * arrowSize;
        float x2 = tipX + (float) Math.cos(angle - arrowAngle) * arrowSize;
        float y2 = tipY + (float) Math.sin(angle - arrowAngle) * arrowSize;

        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                       color.getBlue() / 255f, color.getAlpha() / 255f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(tipX, tipY);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(tipX, tipY);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }

    private String getHullSizeLabel(ShipAPI.HullSize size) {
        switch (size) {
            case CAPITAL_SHIP: return "CAP";
            case CRUISER: return "CRU";
            case DESTROYER: return "DES";
            case FRIGATE: return "FRG";
            default: return "";
        }
    }

    private void drawShipSizeIndicator(float x, float y, ShipAPI.HullSize size, Color color, float alpha) {
        // Draw size-based symbol below ship
        float indicatorSize;
        switch (size) {
            case CAPITAL_SHIP: indicatorSize = 12f; break;
            case CRUISER: indicatorSize = 9f; break;
            case DESTROYER: indicatorSize = 6f; break;
            case FRIGATE: indicatorSize = 4f; break;
            default: return;
        }

        Color indicatorColor = new Color(color.getRed(), color.getGreen(),
                                          color.getBlue(), (int)(200 * alpha));

        // Draw a diamond shape
        GL11.glColor4f(indicatorColor.getRed() / 255f, indicatorColor.getGreen() / 255f,
                       indicatorColor.getBlue() / 255f, indicatorColor.getAlpha() / 255f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y + indicatorSize);
        GL11.glVertex2f(x + indicatorSize, y);
        GL11.glVertex2f(x, y - indicatorSize);
        GL11.glVertex2f(x - indicatorSize, y);
        GL11.glEnd();

        // Fill for capitals and cruisers
        if (size == ShipAPI.HullSize.CAPITAL_SHIP || size == ShipAPI.HullSize.CRUISER) {
            GL11.glColor4f(indicatorColor.getRed() / 255f, indicatorColor.getGreen() / 255f,
                           indicatorColor.getBlue() / 255f, indicatorColor.getAlpha() / 255f * 0.5f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y + indicatorSize);
            GL11.glVertex2f(x + indicatorSize, y);
            GL11.glVertex2f(x, y - indicatorSize);
            GL11.glVertex2f(x - indicatorSize, y);
            GL11.glEnd();
        }
    }

    private void drawWaypoint(float x, float y, float alpha) {
        Color color = new Color(WAYPOINT_COLOR.getRed(), WAYPOINT_COLOR.getGreen(),
                                WAYPOINT_COLOR.getBlue(), (int)(200 * alpha));

        // Draw pulsing concentric circles
        float pulse = 0.7f + 0.3f * (float) Math.sin(selectionPulse * 2);

        GL11.glLineWidth(2f);
        drawCircle(x, y, 30f * pulse, color);
        drawCircle(x, y, 50f * pulse, new Color(color.getRed(), color.getGreen(),
                                                  color.getBlue(), (int)(100 * alpha)));
        GL11.glLineWidth(1f);

        // Draw crosshair
        float crossSize = 20f;
        drawLine(x - crossSize, y, x + crossSize, y, color);
        drawLine(x, y - crossSize, x, y + crossSize, color);
    }

    private void drawAttackMarker(float x, float y, float alpha) {
        Color color = new Color(ATTACK_LINE_COLOR.getRed(), ATTACK_LINE_COLOR.getGreen(),
                                ATTACK_LINE_COLOR.getBlue(), (int)(220 * alpha));

        // Draw X marker
        float markerSize = 25f;
        GL11.glLineWidth(3f);
        drawLine(x - markerSize, y - markerSize, x + markerSize, y + markerSize, color);
        drawLine(x - markerSize, y + markerSize, x + markerSize, y - markerSize, color);
        GL11.glLineWidth(1f);

        // Draw pulsing circle around
        float pulse = 0.8f + 0.2f * (float) Math.sin(selectionPulse * 3);
        drawCircle(x, y, 40f * pulse, new Color(color.getRed(), color.getGreen(),
                                                  color.getBlue(), (int)(150 * alpha)));
    }

    private void drawDashedLine(float x1, float y1, float x2, float y2, Color color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1f) return;

        float dashLength = 30f;
        float gapLength = 20f;
        float segmentLength = dashLength + gapLength;
        int segments = (int) (length / segmentLength);

        float nx = dx / length;
        float ny = dy / length;

        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                       color.getBlue() / 255f, color.getAlpha() / 255f);
        GL11.glLineWidth(2f);
        GL11.glBegin(GL11.GL_LINES);

        for (int i = 0; i <= segments; i++) {
            float startDist = i * segmentLength;
            float endDist = Math.min(startDist + dashLength, length);

            float sx = x1 + nx * startDist;
            float sy = y1 + ny * startDist;
            float ex = x1 + nx * endDist;
            float ey = y1 + ny * endDist;

            GL11.glVertex2f(sx, sy);
            GL11.glVertex2f(ex, ey);
        }

        GL11.glEnd();
        GL11.glLineWidth(1f);
    }
}
