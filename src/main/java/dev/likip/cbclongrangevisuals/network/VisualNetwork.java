package dev.likip.cbclongrangevisuals.network;

import java.util.function.Consumer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** No client class references here: this class also loads on a dedicated server. */
public final class VisualNetwork {
    private static Consumer<VisualFrame> receiver = frame -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").optional().playToClient(VisualFrame.TYPE, VisualFrame.CODEC,
                (frame, context) -> receiver.accept(frame));
    }

    public static void setClientReceiver(Consumer<VisualFrame> handler) { receiver = handler; }
    private VisualNetwork() {}
}
