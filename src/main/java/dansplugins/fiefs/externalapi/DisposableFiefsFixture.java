package dansplugins.fiefs.externalapi;

import dansplugins.fiefs.data.PersistentData;
import dansplugins.fiefs.objects.Fief;
import dansplugins.fiefs.services.StorageService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Exact, unclaimed fief fixture. Commands perform creation; this owner performs compensation. */
public final class DisposableFiefsFixture {
    private final String tag;
    private final Set<UUID> actors;
    private final PersistentData data;
    private final StorageService storage;

    public DisposableFiefsFixture(String tag, Set<UUID> actors,
                                   PersistentData data, StorageService storage) {
        if (tag == null || !tag.matches("PT[0-9a-f]{10}") || actors == null
                || actors.isEmpty() || actors.size() > 16
                || actors.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Invalid disposable fief identity");
        }
        this.tag = tag;
        this.actors = Set.copyOf(actors);
        this.data = data;
        this.storage = java.util.Objects.requireNonNull(storage, "storage");
        storage.verifyPersistence();
        if (data.getFiefs().stream().anyMatch(this::touchesFixture)) {
            throw new IllegalStateException("Disposable fief fixture is not initially empty");
        }
    }

    private boolean touchesFixture(Fief fief) {
        return fief.getName().startsWith(tag)
                || fief.getMembers().stream().anyMatch(actors::contains)
                || (fief.getHeirUUID() != null && actors.contains(fief.getHeirUUID()))
                || (fief.getOwnerUUID() != null && actors.contains(fief.getOwnerUUID()));
    }

    /** Persist through the owner and prove the complete current rows match. */
    public void flushAndVerify() {
        storage.saveIfDirty();
        storage.verifyPersistence();
    }

    /** Rejects foreign members and all claims before removing any fief; safe to retry. */
    public void cleanup() {
        List<Fief> selected = data.getFiefs().stream().filter(this::touchesFixture).toList();
        for (Fief fief : selected) {
            if (!fief.getName().matches(java.util.regex.Pattern.quote(tag) + "[A-Za-z0-9_-]{0,20}")
                    || fief.getOwnerUUID() == null || !actors.contains(fief.getOwnerUUID())
                    || !actors.containsAll(fief.getMembers())
                    || (fief.getHeirUUID() != null && !actors.contains(fief.getHeirUUID()))
                    || data.getNumChunksClaimedByFief(fief) != 0) {
                throw new IllegalStateException("Disposable fief acquired foreign state or land");
            }
        }
        selected.forEach(data::removeFief);
        flushAndVerify();
        if (data.getFiefs().stream().anyMatch(this::touchesFixture)) {
            throw new IllegalStateException("Disposable fief owner state remains");
        }
    }
}
