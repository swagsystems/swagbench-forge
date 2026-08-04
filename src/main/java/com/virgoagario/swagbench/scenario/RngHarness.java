package com.virgoagario.swagbench.scenario;

import com.virgoagario.swagbench.core.RngInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public final class RngHarness {
    public RngInfo seedAll(long seed, ServerLevel level) {
        level.getRandom().setSeed(seed);
        var rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
        // TODO(Section 12): enumerate and pin reachable RNG sources once mixed-v1's
        // exact composition is approved. Some mod-added RNGs may be intentionally
        // unpinnable; report them instead of hiding variance.
        return new RngInfo(seed, level.dimension().location().toString(), false);
    }
}
