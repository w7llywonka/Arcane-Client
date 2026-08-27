package dev.arcaneclient.network;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public final class PacketObserverGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"arcaneclient");
    private static final Set<String> WARNED_OBSERVERS = ConcurrentHashMap.newKeySet();

    private PacketObserverGuard() {
    }

    public static void run(String observer, Runnable action) {
        block2: {
            try {
                action.run();
            }
            catch (RuntimeException exception) {
                if (!WARNED_OBSERVERS.add(observer)) break block2;
                LOGGER.warn("Packet observer '{}' failed; skipping that signal instead of disconnecting", (Object)observer, (Object)exception);
            }
        }
    }
}
