package dansplugins.fiefs.testsupport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Test doubles for the handful of Bukkit interfaces the chunk-claim code touches.
 *
 * <p>No mocking library is on the test classpath, and {@code Chunk}, {@code World} and
 * {@code Player} are far too wide to implement by hand, so these are dynamic proxies that
 * answer only the methods actually called by the code under test. Any other call throws
 * {@link UnsupportedOperationException} rather than returning a silent null, so a test that
 * starts exercising a new part of the Bukkit surface fails loudly instead of misreporting.
 */
public final class BukkitTestDoubles {

    private BukkitTestDoubles() {
        // static factory methods only
    }

    /**
     * A chunk at the given coordinates in a world of the given name.
     * Answers {@code getX()}, {@code getZ()} and {@code getWorld()}.
     */
    public static Chunk chunk(String worldName, int x, int z) {
        World world = proxy(World.class, (method, args) -> {
            if (method.getName().equals("getName")) {
                return worldName;
            }
            throw unsupported(method);
        });

        return proxy(Chunk.class, (method, args) -> {
            switch (method.getName()) {
                case "getX":
                    return x;
                case "getZ":
                    return z;
                case "getWorld":
                    return world;
                default:
                    throw unsupported(method);
            }
        });
    }

    /**
     * A player that appends every message sent to it to {@code sentMessages}, so tests can
     * assert on what a command told the player rather than only on its return value.
     */
    public static Player messageCapturingPlayer(List<String> sentMessages) {
        return proxy(Player.class, (method, args) -> {
            if (method.getName().equals("sendMessage") && args != null && args.length == 1) {
                if (args[0] instanceof String message) {
                    sentMessages.add(message);
                    return null;
                }
                if (args[0] instanceof Component message) {
                    sentMessages.add(PlainTextComponentSerializer.plainText().serialize(message));
                    return null;
                }
            }
            throw unsupported(method);
        });
    }

    /**
     * The behaviour a proxy needs beyond the methods a given double answers. {@code equals},
     * {@code hashCode} and {@code toString} are dispatched to the invocation handler like any
     * other method, so they are handled here rather than in each double.
     */
    private static <T> T proxy(Class<T> type, Answer answer) {
        InvocationHandler handler = (proxyInstance, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxyInstance == args[0];
                case "hashCode":
                    return System.identityHashCode(proxyInstance);
                case "toString":
                    return type.getSimpleName() + " test double";
                default:
                    return answer.answer(method, args);
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static UnsupportedOperationException unsupported(Method method) {
        return new UnsupportedOperationException(
                "This test double does not answer " + method.getDeclaringClass().getSimpleName()
                        + "." + method.getName() + "(...)");
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(Method method, Object[] args);
    }
}
