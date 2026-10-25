package dev.likip.cbclongrangevisuals;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import dev.likip.cbclongrangevisuals.network.VisualNetwork;
import dev.likip.cbclongrangevisuals.server.ServerVisuals;
import org.slf4j.Logger;

/** Common entry point; keep client rendering classes out of dedicated-server loading. */
@Mod(CbcLongRangeVisuals.MOD_ID)
public final class CbcLongRangeVisuals {
    public static final String MOD_ID = "cbc_long_range_visuals";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CbcLongRangeVisuals(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, VisualConfig.SERVER);
        container.registerConfig(ModConfig.Type.CLIENT, VisualConfig.CLIENT);
        modBus.addListener(VisualNetwork::register);
        ServerVisuals.register(NeoForge.EVENT_BUS);
        LOGGER.info("CBC Long Range Visuals: readable-distance profile initialized");
    }
}

