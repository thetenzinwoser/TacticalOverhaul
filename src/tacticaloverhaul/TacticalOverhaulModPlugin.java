package tacticaloverhaul;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;

public class TacticalOverhaulModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() throws Exception {
        Global.getLogger(this.getClass()).info("Tactical Overhaul loaded!");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        // Nothing needed here for combat-only mod
    }
}
