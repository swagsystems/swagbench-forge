package com.virgoagario.swagbench;

import com.mojang.logging.LogUtils;
import com.virgoagario.swagbench.core.StatsEngine;
import com.virgoagario.swagbench.forge.BenchCommand;
import com.virgoagario.swagbench.forge.ForgeRunController;
import java.nio.file.Path;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SwagBenchMod.MODID)
public final class SwagBenchMod {
    public static final String MODID = "swagbench";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ForgeRunController controller = new ForgeRunController(new StatsEngine(0.25, 1_000_000));

    public SwagBenchMod() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("SwagBench loaded");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BenchCommand.register(event, controller, Path.of("swagbench-reports"));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        controller.onServerTick(event);
    }
}
