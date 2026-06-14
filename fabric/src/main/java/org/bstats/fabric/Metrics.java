package org.bstats.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import org.bstats.MetricsBase;
import org.bstats.charts.CustomChart;
import org.bstats.config.MetricsConfig;
import org.bstats.json.JsonObjectBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * bStats Metrics for Fabric server mods.
 *
 * <p>This class is intended to be created from server-side mod initialization. When it is loaded
 * in a client environment, the constructor returns before creating the bStats config file, starting
 * the scheduler, or preparing telemetry data.
 *
 * <p>The config file is stored in {@code config/bStats/config.txt} below Fabric's config
 * directory.
 */
public class Metrics {

    private static final Logger LOGGER = Logger.getLogger("bStats");
    private static final String SERVER_LIFECYCLE_EVENTS =
            "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents";
    private static final String SERVER_STARTED =
            "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStarted";
    private static final String SERVER_STOPPED =
            "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents$ServerStopped";
    private static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";

    private final FabricLoader loader;
    private final String modId;

    private final MetricsBase metricsBase;
    private volatile Object minecraftServer;

    /**
     * Creates a new Metrics class for a Fabric server mod.
     *
     * <p>Fabric API's server lifecycle events are used to capture the Minecraft server instance
     * so this class can collect player count and online mode without pushing that work to mod
     * authors.
     *
     * @param modId The Fabric id of the mod.
     * @param serviceId The id of the bStats service.
     *                  It can be found at <a href="https://bstats.org/what-is-my-plugin-id">What is my plugin id?</a>
     *                  <p>Not to be confused with the Fabric mod id!
     */
    public Metrics(String modId, int serviceId) {
        this.loader = FabricLoader.getInstance();
        this.modId = Objects.requireNonNull(modId, "modId");

        if (loader.getEnvironmentType() != EnvType.SERVER) {
            metricsBase = null;
            return;
        }

        if (!registerServerLifecycleEvents()) {
            metricsBase = null;
            return;
        }

        File configFile = loader.getConfigDir().resolve("bStats").resolve("config.txt").toFile();
        MetricsConfig config;
        try {
            config = new MetricsConfig(configFile, true);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create bStats config", e);
            metricsBase = null;
            return;
        }

        metricsBase = new MetricsBase(
                "fabric",
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

        if (!config.didExistBefore()) {
            // Send an info message when the bStats config file gets created for the first time
            LOGGER.info("Fabric and some of its mods collect metrics and send them to bStats (https://bStats.org).");
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
        builder.appendField("fabricVersion", getModVersion("fabricloader"));

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
        Optional<ModContainer> modContainer = loader.getModContainer(id);
        if (!modContainer.isPresent()) {
            return "unknown";
        }
        return modContainer.get().getMetadata().getVersion().getFriendlyString();
    }

    private boolean registerServerLifecycleEvents() {
        try {
            Class<?> eventsClass = Class.forName(SERVER_LIFECYCLE_EVENTS);
            registerServerLifecycleListener(eventsClass, "SERVER_STARTED", SERVER_STARTED, server -> minecraftServer = server);
            registerServerLifecycleListener(eventsClass, "SERVER_STOPPED", SERVER_STOPPED, server -> minecraftServer = null);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.WARNING, "Failed to register Fabric server lifecycle events for bStats", e);
            return false;
        }
    }

    private void registerServerLifecycleListener(
            Class<?> eventsClass,
            String eventField,
            String listenerClassName,
            Consumer<Object> serverConsumer) throws ReflectiveOperationException {
        Object event = eventsClass.getField(eventField).get(null);
        Class<?> listenerClass = Class.forName(listenerClassName);
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                }
                if ("toString".equals(method.getName())) {
                    return "bStats Fabric lifecycle listener";
                }
            }
            if (args != null && args.length == 1) {
                serverConsumer.accept(args[0]);
            }
            return null;
        };
        Object listener = Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass },
                handler
        );
        Method register = event.getClass().getMethod("register", Object.class);
        register.setAccessible(true);
        register.invoke(event, listener);
    }

    private int getPlayerAmount() {
        Object playerAmount = invokeMinecraftServerMethod("getCurrentPlayerCount", "method_3788", "()I");
        if (playerAmount instanceof Number) {
            return ((Number) playerAmount).intValue();
        }
        return 0;
    }

    private boolean isOnlineMode() {
        Object onlineMode = invokeMinecraftServerMethod("isOnlineMode", "method_3828", "()Z");
        return onlineMode instanceof Boolean && (Boolean) onlineMode;
    }

    private Object invokeMinecraftServerMethod(String namedMethod, String intermediaryMethod, String descriptor) {
        Object server = minecraftServer;
        if (server == null) {
            return null;
        }

        String mappedMethod = mapMinecraftServerMethod(namedMethod, descriptor);
        Object result = invokeNoArgs(server, mappedMethod);
        if (result != null || mappedMethod.equals(intermediaryMethod)) {
            return result;
        }

        result = invokeNoArgs(server, intermediaryMethod);
        if (result != null || mappedMethod.equals(namedMethod)) {
            return result;
        }

        return invokeNoArgs(server, namedMethod);
    }

    private String mapMinecraftServerMethod(String namedMethod, String descriptor) {
        try {
            MappingResolver mappingResolver = loader.getMappingResolver();
            return mappingResolver.mapMethodName("named", MINECRAFT_SERVER, namedMethod, descriptor);
        } catch (RuntimeException e) {
            return namedMethod;
        }
    }

    private Object invokeNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.FINE, "Failed to call Minecraft server method " + methodName + " for bStats", e);
            return null;
        }
    }

}
