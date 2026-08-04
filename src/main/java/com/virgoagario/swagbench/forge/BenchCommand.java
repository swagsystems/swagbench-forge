package com.virgoagario.swagbench.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.virgoagario.swagbench.core.BenchConfig;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class BenchCommand {
    private BenchCommand() {
    }

    public static void register(RegisterCommandsEvent event, ForgeRunController controller, Path outputDir) {
        register(event.getDispatcher(), controller, outputDir);
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, ForgeRunController controller, Path outputDir) {
        dispatcher.register(Commands.literal("swagbench")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("run")
                        .executes(context -> run(context.getSource(), controller, outputDir, defaultConfig()))
                        .then(Commands.argument("scenario", StringArgumentType.word())
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .then(Commands.argument("warmup", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("measure", IntegerArgumentType.integer(1))
                                                        .executes(context -> run(
                                                                context.getSource(),
                                                                controller,
                                                                outputDir,
                                                                new BenchConfig(
                                                                        StringArgumentType.getString(context, "scenario"),
                                                                        LongArgumentType.getLong(context, "seed"),
                                                                        IntegerArgumentType.getInteger(context, "warmup"),
                                                                        IntegerArgumentType.getInteger(context, "measure")))))))))
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(new TextComponent("SwagBench state: " + controller.state()), false);
                    return 1;
                }))
                .then(Commands.literal("abort").executes(context -> {
                    boolean aborted = controller.abort();
                    context.getSource().sendSuccess(new TextComponent(aborted ? "SwagBench run aborted" : "SwagBench is idle"), true);
                    return aborted ? 1 : 0;
                }))
                .then(Commands.literal("config").executes(context -> {
                    BenchConfig config = defaultConfig();
                    context.getSource().sendSuccess(new TextComponent(
                            "SwagBench default config: scenario=" + config.scenario()
                                    + ", seed=" + config.seed()
                                    + ", warmup=" + config.warmupTicks()
                                    + ", measure=" + config.measureTicks()), false);
                    return 1;
                })));
    }

    private static int run(CommandSourceStack source, ForgeRunController controller, Path outputDir, BenchConfig config) {
        try {
            boolean started = controller.requestRun(config, outputDir, source.getServer(), false, false);
            source.sendSuccess(new TextComponent(started ? "SwagBench run started" : "SwagBench run already running"), true);
            return started ? 1 : 0;
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            source.sendFailure(new TextComponent("SwagBench run failed: " + message));
            return 0;
        }
    }

    public static BenchConfig defaultConfig() {
        return BenchConfig.tiny("mixed-v1", 0L, 2_000, 2_000);
    }
}
