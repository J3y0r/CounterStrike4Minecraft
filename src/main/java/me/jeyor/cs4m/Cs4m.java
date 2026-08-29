package me.jeyor.cs4m;

import me.jeyor.cs4m.command.Cs4mCommands;
import me.jeyor.cs4m.event.PlayerLifecycle;
import me.jeyor.cs4m.runtime.Cs4mAccess;
import me.jeyor.cs4m.runtime.Cs4mServer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cs4m implements ModInitializer {
    public static final String MOD_ID = "cs4m";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private Cs4mServer runtime;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (runtime != null) {
                runtime.close();
            }
            runtime = Cs4mServer.start(server, LOGGER);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (runtime != null) {
                runtime.close();
                runtime = null;
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (runtime != null && runtime.server() == server) {
                runtime.tick();
            }
        });

        PlayerLifecycle.register(() -> runtime);
        Cs4mAccess.bind(() -> runtime);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                new Cs4mCommands(() -> runtime).register(dispatcher));

        LOGGER.info("CS4M server initializer registered");
    }
}
