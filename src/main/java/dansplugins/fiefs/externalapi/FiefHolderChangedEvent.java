package dansplugins.fiefs.externalapi;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Main-thread, noncancellable notification of a completed LIVE fief holding change.
 *
 * <p>The owner has applied its membership, holder, heir and dirty-state changes before publication.
 * Creation uses a null previous holder; removal or reversion uses a null current holder. Vacant
 * creation/removal and unchanged holders do not emit a holding change. Startup loading is not a
 * creation and does not replay these events.
 *
 * <p>This is not a durable-save receipt: Fiefs saves dirty live state separately. Notifications may
 * be delayed and the fief may already have changed again, so consumers must reread current state
 * rather than treating this historical payload as an instruction to grant a title. A listener or
 * scheduler failure never rolls back a completed mutation.
 */
public final class FiefHolderChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID fiefId;
    private final UUID previousHolderId;
    private final UUID currentHolderId;

    public FiefHolderChangedEvent(UUID fiefId, UUID previousHolderId, UUID currentHolderId) {
        this.fiefId = Objects.requireNonNull(fiefId, "fiefId");
        if (Objects.equals(previousHolderId, currentHolderId)) {
            throw new IllegalArgumentException("A holding change requires different holders");
        }
        this.previousHolderId = previousHolderId;
        this.currentHolderId = currentHolderId;
    }

    public UUID getFiefId() { return fiefId; }
    public UUID getPreviousHolderId() { return previousHolderId; }
    public UUID getCurrentHolderId() { return currentHolderId; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
