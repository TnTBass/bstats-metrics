package org.bstats.fabric;

import org.bstats.json.JsonObjectBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MetricsTest {

    @Test
    void appendFabricVersionsUsesLoaderAndApiFieldNames() {
        JsonObjectBuilder builder = new JsonObjectBuilder();

        Metrics.appendFabricVersions(builder, id -> {
            if ("fabricloader".equals(id)) {
                return Optional.of("0.19.3");
            }
            if ("fabric-api".equals(id)) {
                return Optional.of("0.128.2+1.21.8");
            }
            return Optional.empty();
        });

        assertEquals(
                "{\"fabricLoaderVersion\":\"0.19.3\",\"fabricApiVersion\":\"0.128.2+1.21.8\"}",
                builder.build().toString()
        );
    }

    @Test
    void appendFabricVersionsOmitsUnknownFabricApiVersion() {
        JsonObjectBuilder builder = new JsonObjectBuilder();

        Metrics.appendFabricVersions(builder, id -> "fabricloader".equals(id) ? Optional.of("0.19.3") : Optional.empty());

        assertEquals("{\"fabricLoaderVersion\":\"0.19.3\"}", builder.build().toString());
    }

    @Test
    void appendFabricVersionsIncludesLiteralUnknownFabricApiVersionWhenPresent() {
        JsonObjectBuilder builder = new JsonObjectBuilder();

        Metrics.appendFabricVersions(builder, id -> Optional.of("unknown"));

        assertEquals(
                "{\"fabricLoaderVersion\":\"unknown\",\"fabricApiVersion\":\"unknown\"}",
                builder.build().toString()
        );
    }

    @Test
    void serverMethodCachesResolvedMethodAndReturnsBoxedPrimitiveValue() throws Exception {
        Metrics.ServerMethod serverMethod = new Metrics.ServerMethod("getCurrentPlayerCount", "method_3788", "()I");
        Server server = new Server();

        Object result = serverMethod.invoke(server, null);

        assertEquals(7, result);
        Field cachedMethod = Metrics.ServerMethod.class.getDeclaredField("method");
        cachedMethod.setAccessible(true);
        Object firstMethod = cachedMethod.get(serverMethod);

        assertNotNull(firstMethod);
        assertEquals(8, serverMethod.invoke(server, null));
        assertSame(firstMethod, cachedMethod.get(serverMethod));
    }

    @Test
    void serverMethodReResolvesForDifferentServerClasses() {
        Metrics.ServerMethod serverMethod = new Metrics.ServerMethod("getCurrentPlayerCount", "method_3788", "()I");

        assertEquals(7, serverMethod.invoke(new Server(), null));
        assertEquals(12, serverMethod.invoke(new OtherServer(), null));
    }

    private static class Server {
        private int playerCount = 6;

        public int getCurrentPlayerCount() {
            playerCount++;
            return playerCount;
        }
    }

    private static class OtherServer {
        public int getCurrentPlayerCount() {
            return 12;
        }
    }
}
