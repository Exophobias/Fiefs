package dansplugins.fiefs.services;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Durable MF unclaim notifications whose worlds cannot currently be resolved by Bukkit. */
public final class DeferredUnclaimStore {
    private static final long MAGIC = 0x46494546554e4331L; // FIEFUNC1
    private static final int VERSION = 1;
    private static final int MAX_ROWS = 100_000;
    private static final long MAX_BYTES = 16L + MAX_ROWS * 24L;

    public record Position(UUID worldId, int x, int z) {
        public Position {
            if (worldId == null) throw new IllegalArgumentException("World id is required");
        }
    }

    private final Path file;
    private final Set<Position> pending = new LinkedHashSet<>();
    private boolean healthy = true;

    public DeferredUnclaimStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    /** An unreadable or corrupt queue is a startup error; never silently discard it. */
    public synchronized void load() throws IOException {
        Set<Position> loaded = new LinkedHashSet<>();
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            attributes = null;
        }
        if (attributes != null) {
            if (!attributes.isRegularFile() || attributes.size() > MAX_BYTES) {
                throw new IOException("Deferred Fiefs unclaims are not a bounded regular file");
            }
            try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
                if (input.readLong() != MAGIC || input.readInt() != VERSION) {
                    throw new IOException("Unsupported deferred Fiefs unclaim format");
                }
                int count = input.readInt();
                if (count < 0 || count > MAX_ROWS) throw new IOException("Invalid deferred Fiefs unclaim count");
                for (int i = 0; i < count; i++) {
                    Position position = new Position(new UUID(input.readLong(), input.readLong()),
                            input.readInt(), input.readInt());
                    if (!loaded.add(position)) throw new IOException("Duplicate deferred Fiefs unclaim");
                }
                if (input.read() != -1) throw new IOException("Trailing deferred Fiefs unclaim bytes");
            }
        }
        pending.clear();
        pending.addAll(loaded);
        healthy = true;
    }

    /** Commit before attempting to remove the corresponding in-memory fief claim. */
    public synchronized void record(Position position) throws IOException {
        requireHealthy();
        if (pending.contains(position)) return;
        Set<Position> next = new LinkedHashSet<>(pending);
        if (!next.add(position) || next.size() > MAX_ROWS) {
            throw new IOException("Deferred Fiefs unclaim limit reached");
        }
        replace(next);
        pending.clear();
        pending.addAll(next);
    }

    public synchronized List<Position> forWorld(UUID worldId) {
        return pending.stream().filter(position -> position.worldId().equals(worldId)).toList();
    }

    /** Call only after Fiefs' claimedChunks.json has saved the matching removals. */
    public synchronized void acknowledge(Collection<Position> resolved) throws IOException {
        requireHealthy();
        Set<Position> next = new LinkedHashSet<>(pending);
        if (!next.removeAll(resolved)) return;
        replace(next);
        pending.clear();
        pending.addAll(next);
    }

    private void requireHealthy() throws IOException {
        if (!healthy) throw new IOException("Deferred Fiefs unclaim commit is uncertain; restart required");
    }

    private void replace(Set<Position> next) throws IOException {
        Path parent = file.getParent();
        if (parent == null) throw new IOException("Deferred Fiefs unclaim file needs a parent directory");
        Files.createDirectories(parent);
        Path candidate = Files.createTempFile(parent, ".pending-unclaims-", ".new");
        boolean moveAttempted = false;
        try {
            try (FileChannel channel = FileChannel.open(candidate, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel))) {
                output.writeLong(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(next.size());
                for (Position position : next) {
                    output.writeLong(position.worldId().getMostSignificantBits());
                    output.writeLong(position.worldId().getLeastSignificantBits());
                    output.writeInt(position.x());
                    output.writeInt(position.z());
                }
                output.flush();
                channel.force(true);
            }
            moveAttempted = true;
            try {
                Files.move(candidate, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                moveAttempted = false;
                throw new IOException("Deferred Fiefs unclaims require atomic replacement", unsupported);
            }
            syncDirectory(parent);
        } catch (IOException | RuntimeException failure) {
            if (moveAttempted) healthy = false;
            throw failure;
        } finally {
            try {
                Files.deleteIfExists(candidate);
            } catch (IOException cleanup) {
                if (moveAttempted) healthy = false;
                throw cleanup;
            }
        }
    }

    private static void syncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException unsupported) {
            // Filesystems that cannot force directories still have the forced file above.
        } catch (AccessDeniedException unsupportedWindowsDirectory) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw unsupportedWindowsDirectory;
            }
        }
    }
}
