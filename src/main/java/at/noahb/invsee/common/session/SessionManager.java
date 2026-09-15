package at.noahb.invsee.common.session;

import at.noahb.invsee.InvseePlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class SessionManager {
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private final InvseePlugin instance;

    public SessionManager(InvseePlugin instance) {
        this.instance = instance;
    }

    public void addSubscriberToSession(OfflinePlayer player, UUID subscriber) {
        for (Session session : this.sessions.values()) {
            if (session.hasSubscriber(subscriber)) {
                session.removeSubscriber(subscriber);
            }
        }

        Session session = this.sessions.computeIfAbsent(player.getUniqueId(), uuid -> createSession(player));

        session.runOnObservedThread(() -> {
            session.updateSubscriberInventory();
            session.addSubscriber(subscriber);
        });
    }

    public void removeSubscriberFromSession(@NotNull HumanEntity subscriber) {
        UUID subscriberId = subscriber.getUniqueId();
        for (Session session : this.sessions.values()) {
            if (session.hasSubscriber(subscriberId)) {
                session.removeSubscriber(subscriberId);
                if (session.getSubscribers().isEmpty()) {
                    this.sessions.remove(session.getUniqueIdOfObservedPlayer(), session);
                    if (session.isOffline()) {
                        session.save();
                    }
                }
            }
        }
        subscriber.getScheduler().run(this.instance, scheduledTask -> subscriber.closeInventory(InventoryCloseEvent.Reason.PLUGIN), null);
    }

    public void updateContent(Player player) {
        UUID uuid = player.getUniqueId();
        Session session = this.sessions.get(uuid);
        if (session != null) {
            session.runOnObservedThread(session::updateSubscriberInventory);
            return;
        }

        for (Session activeSession : this.sessions.values()) {
            if (activeSession.hasSubscriber(uuid)) {
                activeSession.runOnObservedThread(activeSession::updateObservedInventory);
                break;
            }
        }
    }

    public Optional<Session> getSessionForSubscriber(UUID subscriber) {
        return this.sessions.values().stream()
                .filter(session -> session.hasSubscriber(subscriber))
                .findFirst();
    }

    protected abstract Session createSession(OfflinePlayer offlinePlayer);

    public boolean isSessionInventory(Inventory inventory) {
        return inventory instanceof SessionInventory;
    }

    public boolean hasActiveSessions() {
        return !this.sessions.isEmpty();
    }

    public boolean isSession(@NotNull UUID whoClicked) {
        if (this.sessions.containsKey(whoClicked)) {
            return true;
        }
        for (Session session : this.sessions.values()) {
            if (session.hasSubscriber(whoClicked)) {
                return true;
            }
        }
        return false;
    }
}
