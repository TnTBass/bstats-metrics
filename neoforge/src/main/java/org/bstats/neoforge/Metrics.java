package org.bstats.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.bstats.MetricsBase;
import org.bstats.charts.CustomChart;
import org.bstats.config.MetricsConfig;
import org.bstats.json.JsonObjectBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * bStats Metrics for NeoForge server mods.
 *
 * <p>This class is intended to be created from server-side mod initialization. When it is loaded
 * in a client environment, the constructor returns before creating the bStats config file, starting
 * the scheduler, or preparing telemetry data.
 *
 * <p>The config file is stored in {@code config/bStats/config.txt} below NeoForge's config
 * directory.
 */
public class Metrics {

    private static final Logger LOGGER = Logger.getLogger("bStats");

    private final String modId;

    private final MetricsBase metricsBase;
    private volatile Object minecraftServer;
    private final AtomicBoolean listenerRegistered = new AtomicBoolean();

    /**
     * Creates a new Metrics class for a NeoForge server mod.
     *
     * <p>NeoForge server lifecycle events are used to capture the Minecraft server instance so
     * this class can collect player count and online mode without pushing that work to mod
     * authors.
     *
     * @param modId The NeoForge id of the mod.
     * @param serviceId The id of the bStats service.
     *                  It can be found at <a href="https://bstats.org/what-is-my-plugin-id">What is my plugin id?</a>
     *                  <p>Not to be confused with the NeoForge mod id!
     */
    public Metrics(String modId, int serviceId) {
        this.modId = Objects.requireNonNull(modId, "modId");

        if (FMLEnvironment.getDist() != Dist.DEDICATED_SERVER) {
            metricsBase = null;
            return;
        }

        File configFile = FMLPaths.CONFIGDIR.get().resolve("bStats").resolve("config.txt").toFile();
        MetricsConfig config;
        try {
            config = new MetricsConfig(configFile, true);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create bStats config", e);
            metricsBase = null;
            return;
        }

        metricsBase = new MetricsBase(
                "neoforge",
                config.getServerUUID(),
                serviceId,
                config.isEnabled(),
                this::appendPlatformData,
                this::appendServiceData,
                null,
                () -> true,
                (message, error) -> LOGGER.log(Level.WARNING, message, error),
                LOGGER::info,
                config.isLogErrorsEnabled(),
                config.isLogSentDataEnabled(),
                config.isLogResponseStatusTextEnabled(),
                false
        );
        NeoForge.EVENT_BUS.register(this);
        listenerRegistered.set(true);

        if (!config.didExistBefore()) {
            // Send an info message when the bStats config file gets created for the first time
            LOGGER.info("NeoForge and some of its mods collect metrics and send them to bStats (https://bStats.org).");
            LOGGER.info("bStats collects some basic information for mod authors, like how many people use");
            LOGGER.info("their mod and their total player count. It's recommended to keep bStats enabled, but");
            LOGGER.info("if you're not comfortable with this, you can opt-out by editing the config.txt file in");
            LOGGER.info("the '/config/bStats/' folder and setting enabled to false.");
        }
    }

    /**
     * Shuts down the underlying scheduler service.
     */
    public void shutdown() {
        if (listenerRegistered.compareAndSet(true, false)) {
            NeoForge.EVENT_BUS.unregister(this);
        }
        if (metricsBase != null) {
            metricsBase.shutdown();
        }
    }

    /**
     * Adds a custom chart.
     *
     * @param chart The chart to add.
     */
    public void addCustomChart(CustomChart chart) {
        if (metricsBase != null) {
            metricsBase.addCustomChart(chart);
        }
    }

    private void appendPlatformData(JsonObjectBuilder builder) {
        builder.appendField("playerAmount", getPlayerAmount());
        builder.appendField("onlineMode", isOnlineMode() ? 1 : 0);
        builder.appendField("minecraftVersion", getModVersion("minecraft"));
        builder.appendField("neoforgeVersion", getModVersion("neoforge"));

        builder.appendField("javaVersion", System.getProperty("java.version"));
        builder.appendField("osName", System.getProperty("os.name"));
        builder.appendField("osArch", System.getProperty("os.arch"));
        builder.appendField("osVersion", System.getProperty("os.version"));
        builder.appendField("coreCount", Runtime.getRuntime().availableProcessors());
    }

    private void appendServiceData(JsonObjectBuilder builder) {
        builder.appendField("pluginVersion", getModVersion(modId));
    }

    private String getModVersion(String id) {
        Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById(id);
        if (!modContainer.isPresent()) {
            return "unknown";
        }
        return modContainer.get().getModInfo().getVersion().toString();
    }

    /**
     * Captures the active server once NeoForge finishes server startup.
     *
     * @param event The server started event.
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        minecraftServer = getServer(event);
    }

    /**
     * Clears server state and shuts down bStats when NeoForge stops the server.
     *
     * @param event The server stopped event.
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Object server = getServer(event);
        minecraftServer = null;
        if (!scheduleServerTask(server, this::shutdown)) {
            shutdown();
        }
    }

    private int getPlayerAmount() {
        Object playerAmount = invokeMinecraftServerMethod("getPlayerCount");
        if (playerAmount instanceof Number) {
            return ((Number) playerAmount).intValue();
        }
        return 0;
    }

    private boolean isOnlineMode() {
        Object onlineMode = invokeMinecraftServerMethod("usesAuthentication");
        return onlineMode instanceof Boolean && (Boolean) onlineMode;
    }

    private Object getServer(Object event) {
        return invokeNoArgs(event, "getServer");
    }

    private boolean scheduleServerTask(Object server, Runnable task) {
        if (server == null) {
            return false;
        }

        try {
            Method execute = server.getClass().getMethod("execute", Runnable.class);
            execute.invoke(server, task);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.FINE, "Failed to schedule NeoForge server task for bStats", e);
            return false;
        }
    }

    private Object invokeMinecraftServerMethod(String methodName) {
        Object server = minecraftServer;
        if (server == null) {
            return null;
        }
        return invokeNoArgs(server, methodName);
    }

    private Object invokeNoArgs(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.FINE, "Failed to call " + methodName + " for bStats", e);
            return null;
        }
    }

}
