package com.virgoagario.swagbench.forge;

import com.virgoagario.swagbench.core.EnvironmentInfo;
import java.util.Comparator;
import net.minecraftforge.fml.ModList;

public final class EnvironmentCollector {
    private EnvironmentCollector() {
    }

    public static EnvironmentInfo collect() {
        String forgeVersion = ModList.get().getModContainerById("forge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        var mods = ModList.get().getMods().stream()
                .map(info -> info.getModId() + "@" + info.getVersion())
                .sorted(Comparator.naturalOrder())
                .toList();
        return EnvironmentInfo.runtime(forgeVersion, mods);
    }
}
