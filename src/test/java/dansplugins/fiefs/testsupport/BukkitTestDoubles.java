package dansplugins.fiefs.testsupport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Test doubles for the handful of Bukkit interfaces the chunk-claim code touches.
 *
 * <p>These pure command tests do not need a running mock server. {@code Chunk}, {@code World} and
 * {@code Player} are far too wide to implement by hand, so these are dynamic proxies that
 * answer only the methods actually called by the code under test. Any other call throws
 * {@link UnsupportedOperationException} rather than returning a silent null, so a test that
 * starts exercising a new part of the Bukkit surface fails loudly instead of misreporting.
 *
 */
public final class BukkitTestDoubles {

    private BukkitTestDoubles() {
        // static factory methods only
    }

    /**
     * A world of the given name. Answers {@code getName()} only, which is all the code under
     * test asks of a world it did not create itself — fief claims are matched by world name.
     */
    public static World world(String worldName) {
        return proxy(World.class, (method, args) -> {
            if (method.getName().equals("getName")) {
                return worldName;
            }
            throw unsupported(method);
        });
    }

    /**
     * A chunk at the given coordinates in a world of the given name.
     * Answers {@code getX()}, {@code getZ()} and {@code getWorld()}.
     */
    public static Chunk chunk(String worldName, int x, int z) {
        World world = world(worldName);

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
     * A player standing in the given chunk, appending every message sent to it to
     * {@code sentMessages}. Answers {@code sendMessage(String)} and {@code getLocation()},
     * the latter returning a location whose {@code getChunk()} resolves to {@code chunk}.
     */
    public static Player playerInChunk(Chunk chunk, List<String> sentMessages) {
        World world = proxy(World.class, (method, args) -> {
            if (method.getName().equals("getChunkAt") && args != null && args.length == 1
                    && args[0] instanceof Location) {
                return chunk;
            }
            throw unsupported(method);
        });

        Location location = new Location(world, 0, 64, 0);

        return proxy(Player.class, (method, args) -> {
            if (method.getName().equals("sendMessage") && captureMessage(args, sentMessages)) {
                return null;
            }
            if (method.getName().equals("getLocation") && (args == null || args.length == 0)) {
                return location;
            }
            // Answering getWorld() is also what keeps the world above reachable: a Location
            // holds its world through a weak reference, and this handler is the only strong
            // reference to it once the factory returns. Without it a collected world turns
            // getLocation().getChunk() into an intermittent "World unloaded" failure.
            if (method.getName().equals("getWorld") && (args == null || args.length == 0)) {
                return world;
            }
            throw unsupported(method);
        });
    }

    /**
     * A non-player command sender — the console, for the purposes of the commands that refuse
     * to run for one — appending every message sent to it to {@code sentMessages}.
     */
    public static CommandSender messageCapturingConsole(List<String> sentMessages) {
        return proxy(CommandSender.class, (method, args) -> {
            if (method.getName().equals("sendMessage") && captureMessage(args, sentMessages)) {
                return null;
            }
            if (method.getName().equals("hasPermission")) {
                return true;
            }
            throw unsupported(method);
        });
    }

    private static boolean captureMessage(Object[] args, List<String> messages) {
        if (args == null || args.length != 1) return false;
        if (args[0] instanceof String message) {
            messages.add(message);
            return true;
        }
        if (args[0] instanceof Component message) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
            return true;
        }
        return false;
    }

    /**
     * The behaviour a proxy needs beyond the methods a given double answers. {@code equals},
     * {@code hashCode} and {@code toString} are dispatched to the invocation handler like any
     * other method, so they are handled here rather than in each double.
     */
    static <T> T proxy(Class<T> type, Answer answer) {
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

    static UnsupportedOperationException unsupported(Method method) {
        return new UnsupportedOperationException(
                "This test double does not answer " + method.getDeclaringClass().getSimpleName()
                        + "." + method.getName() + "(...)");
    }

    @FunctionalInterface
    interface Answer {
        Object answer(Method method, Object[] args);
    }
}
