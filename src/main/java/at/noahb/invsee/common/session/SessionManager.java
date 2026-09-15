package at.noahb.invsee.common.session;

import at.noahb.invsee.InvseePlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class SessionManager {
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();

    private final InvseePlugin instance;

    public SessionManager(InvseePlugin instance) {
        this.instance = instance;
    }

    public synchronized void addSubscriberToSession(OfflinePlayer player, UUID subscriber) {
        this.sessions.stream().filter(session -> session.getSubscribers().contains(subscriber))
                .forEach(session -> session.removeSubscriber(subscriber));

        Session session = this.sessions.stream()
                .filter(filterSession -> player.getUniqueId().equals(filterSession.getUniqueIdOfObservedPlayer()))
                .findFirst()
                .orElseGet(() -> {
                    Session created = createSession(player);
                    this.sessions.add(created);
                    return created;
                });

        session.runOnObservedThread(() -> {
            session.updateSubscriberInventory();
            session.addSubscriber(subscriber);
        });
    }

    public void removeSubscriberFromSession(@NotNull HumanEntity subscriber) {
        Optional<? extends Session> first = this.sessions.stream().filter(session -> session.getSubscribers().contains(subscriber.getUniqueId())).findFirst();

        first.ifPresent(session -> {
            session.removeSubscriber(subscriber.getUniqueId());
            if (session.getSubscribers().isEmpty()) {
                this.sessions.remove(session);
            }
            subscriber.getScheduler().run(this.instance, scheduledTask -> subscriber.closeInventory(InventoryCloseEvent.Reason.PLUGIN), null);
        });

    }

    public void updateContent(Player player) {
        Optional<? extends Session> optionalSession = this.sessions.stream()
                .filter(session -> session.getUniqueIdOfObservedPlayer().equals(player.getUniqueId()))
                .findFirst();

        if (optionalSession.isPresent()) {
            Session session = optionalSession.get();
            session.runOnObservedThread(session::updateSubscriberInventory);
            return;
        }

        optionalSession = this.sessions.stream()
                .filter(session -> session.hasSubscriber(player.getUniqueId()))
                .findFirst();

        optionalSession.ifPresent(session -> session.runOnObservedThread(session::updateObservedInventory));
    }

    public Optional<Session> getSessionForSubscriber(UUID subscriber) {
        return sessions.stream()
                .filter(session -> session.hasSubscriber(subscriber))
                .findFirst();
    }

    protected abstract Session createSession(OfflinePlayer offlinePlayer);

    public boolean isSessionInventory(Inventory inventory) {
        return inventory instanceof SessionInventory;
    }

    public boolean isSession(@NotNull UUID whoClicked) {
        return this.sessions.stream().anyMatch(session -> session.isSubscriber(whoClicked) ||
                session.getUniqueIdOfObservedPlayer().equals(whoClicked));
    }
}
