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
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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
    private static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";
    private static final ServerMethod PLAYER_COUNT =
            new ServerMethod("getCurrentPlayerCount", "method_3788", "()I");
    private static final ServerMethod ONLINE_MODE =
            new ServerMethod("isOnlineMode", "method_3828", "()Z");

    private final FabricLoader loader;
    private final String modId;

    private final MetricsBase metricsBase;

    /**
     * Creates a new Metrics class for a Fabric server mod.
     *
     * <p>Fabric Loader's game instance is used to collect player count and online mode without
     * pushing that work to mod authors.
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
        appendFabricVersions(builder, this::getOptionalModVersion);

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
        return getOptionalModVersion(id).orElse("unknown");
    }

    private Optional<String> getOptionalModVersion(String id) {
        Optional<ModContainer> modContainer = loader.getModContainer(id);
        return modContainer.map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    static void appendFabricVersions(JsonObjectBuilder builder, Function<String, Optional<String>> modVersionProvider) {
        builder.appendField("fabricLoaderVersion", modVersionProvider.apply("fabricloader").orElse("unknown"));
        modVersionProvider.apply("fabric-api").ifPresent(version -> builder.appendField("fabricApiVersion", version));
    }

    private int getPlayerAmount() {
        Object playerAmount = PLAYER_COUNT.invoke(getGameInstance(), loader);
        if (playerAmount instanceof Number) {
            return ((Number) playerAmount).intValue();
        }
        return 0;
    }

    private boolean isOnlineMode() {
        Object onlineMode = ONLINE_MODE.invoke(getGameInstance(), loader);
        return onlineMode instanceof Boolean && (Boolean) onlineMode;
    }

    @SuppressWarnings("deprecation")
    private Object getGameInstance() {
        return loader.getGameInstance();
    }

    static final class ServerMethod {

        private final String namedMethod;
        private final String intermediaryMethod;
        private final String descriptor;
        private volatile Method method;

        ServerMethod(String namedMethod, String intermediaryMethod, String descriptor) {
            this.namedMethod = namedMethod;
            this.intermediaryMethod = intermediaryMethod;
            this.descriptor = descriptor;
        }

        Object invoke(Object server, FabricLoader loader) {
            if (server == null) {
                return null;
            }
            try {
                return resolve(server, loader).invoke(server);
            } catch (ReflectiveOperationException e) {
                LOGGER.log(Level.FINE, "Failed to call Minecraft server method " + namedMethod + " for bStats", e);
                return null;
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.FINE, "Failed to call cached Minecraft server method " + namedMethod + " for bStats", e);
                method = null;
                return null;
            }
        }

        private Method resolve(Object server, FabricLoader loader) throws NoSuchMethodException {
            Method cachedMethod = method;
            if (cachedMethod != null && cachedMethod.getDeclaringClass().isInstance(server)) {
                return cachedMethod;
            }

            Method resolvedMethod = findNoArgs(server, mappedMethod(loader));
            if (resolvedMethod == null) {
                resolvedMethod = findNoArgs(server, intermediaryMethod);
            }
            if (resolvedMethod == null) {
                resolvedMethod = findNoArgs(server, namedMethod);
            }
            if (resolvedMethod == null) {
                throw new NoSuchMethodException(namedMethod);
            }

            method = resolvedMethod;
            return resolvedMethod;
        }

        private String mappedMethod(FabricLoader loader) {
            if (loader == null) {
                return namedMethod;
            }
            try {
                MappingResolver mappingResolver = loader.getMappingResolver();
                return mappingResolver.mapMethodName("named", MINECRAFT_SERVER, namedMethod, descriptor);
            } catch (RuntimeException e) {
                return namedMethod;
            }
        }

        private Method findNoArgs(Object server, String methodName) {
            try {
                return server.getClass().getMethod(methodName);
            } catch (ReflectiveOperationException e) {
                LOGGER.log(Level.FINE, "Failed to resolve Minecraft server method " + methodName + " for bStats", e);
                return null;
            }
        }

    }

}
