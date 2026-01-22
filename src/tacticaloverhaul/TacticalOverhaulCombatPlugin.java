package tacticaloverhaul;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
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
    private float overlayAlpha = 0f;
    private static final float OVERLAY_TARGET_ALPHA = 0.4f;
    private static final float OVERLAY_FADE_SPEED = 4.0f;

    public void setTacticalModeActive(boolean active) {
        this.tacticalModeActive = active;
    }

    @Override
    public void advance(float amount) {
        // Smoothly fade the overlay in/out
        float targetAlpha = tacticalModeActive ? OVERLAY_TARGET_ALPHA : 0f;
        if (Math.abs(overlayAlpha - targetAlpha) > 0.001f) {
            float delta = targetAlpha - overlayAlpha;
            overlayAlpha += delta * Math.min(1.0f, OVERLAY_FADE_SPEED * amount);
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

            // Choose color based on owner
            Color baseColor;
            if (ship.getOwner() == 0) {
                baseColor = new Color(100, 200, 255); // Player fleet - blue
            } else if (ship.getOwner() == 1) {
                baseColor = new Color(255, 100, 100); // Enemy - red
            } else {
                baseColor = new Color(200, 200, 200); // Neutral - gray
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
                Color fluxColor;
                if (fluxLevel > 0.8f) {
                    fluxColor = new Color(255, 50, 50, (int)(200 * alpha)); // Critical - red
                } else if (fluxLevel > 0.5f) {
                    fluxColor = new Color(255, 200, 50, (int)(150 * alpha)); // Warning - yellow
                } else {
                    fluxColor = new Color(100, 255, 100, (int)(100 * alpha)); // Normal - green
                }
                drawArc(x, y, radius * 1.8f, 90f, 90f + fluxLevel * 360f, fluxColor);
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
}
