package dev.likip.cbclongrangevisuals.client;

import dev.likip.cbclongrangevisuals.CbcLongRangeVisuals;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import dev.likip.cbclongrangevisuals.network.VisualNetwork;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

/** Client-only entry point for the future distant projectile renderer. */
@Mod(value = CbcLongRangeVisuals.MOD_ID, dist = Dist.CLIENT)
public final class CbcLongRangeVisualsClient {
    public CbcLongRangeVisualsClient(IEventBus modEventBus) {
        VisualNetwork.setClientReceiver(ClientVisuals.INSTANCE::accept);
        modEventBus.addListener(FarVisualRenderer::registerShader);
        modEventBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.addListener(ClientVisuals.INSTANCE::tick);
        NeoForge.EVENT_BUS.addListener(FarVisualRenderer::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientVisuals.INSTANCE.clear());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("cbcvisuals").then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(ClientVisuals.INSTANCE.status()), false);
                    return 1;
                })).then(Commands.literal("preview").then(Commands.argument("distance", IntegerArgumentType.integer(256, 4096))
                        .executes(context -> {
                            ClientVisuals.INSTANCE.preview(IntegerArgumentType.getInteger(context, "distance"));
                            context.getSource().sendSuccess(() -> Component.literal("Local visual preview: 20 seconds; no real entities or damage."), false);
                            return 1;
                        })))));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // The class reference also checks the CBC development dependency during compilation.
        CbcLongRangeVisuals.LOGGER.info("CBC client integration ready; projectile base class: {}",
                AbstractCannonProjectile.class.getName());
    }
}

