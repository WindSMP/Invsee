package at.noahb.invsee.common.session;

import at.noahb.invsee.InvseePlugin;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public interface Session extends SessionInventory {

    default void addSubscriber(UUID subscriber) {
        if (subscriber == null) return;
        if (hasSubscriber(subscriber)) return;
        Player player = InvseePlugin.getInstance().getServer().getPlayer(subscriber);
        if (player == null) return;

        getSubscribers().add(subscriber);
        player.getScheduler().run(InvseePlugin.getInstance(), scheduledTask -> player.openInventory(getInventory()), null);
    }

    /**
     * Runs code which touches the observed player's data on the region that owns
     * that player. Offline players are represented by a temporary ServerPlayer,
     * so their last saved location is the owning region as well.
     */
    default void runOnObservedThread(Runnable runnable) {
        InvseePlugin plugin = InvseePlugin.getInstance();
        Player onlinePlayer = plugin.getServer().getPlayer(getUniqueIdOfObservedPlayer());

        if (onlinePlayer != null) {
            onlinePlayer.getScheduler().run(plugin, task -> runnable.run(),
                    () -> runOnObservedThread(runnable));
            return;
        }

        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(getUniqueIdOfObservedPlayer());
        Location location = offlinePlayer.getLocation();
        if (location == null) {
            location = plugin.getServer().getWorlds().get(0).getSpawnLocation();
        }

        plugin.getServer().getRegionScheduler().run(plugin, location, task -> runnable.run());
    }


    default void save() {
        Player cachedPlayer = getCachedPlayer();
        if (cachedPlayer != null) {
            MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
            server.getPlayerList().playerIo.save(((CraftPlayer) cachedPlayer).getHandle());
        }
    }

    default void update(Runnable runnable) {
        try {
            getLock().lock();
            runnable.run();
            if (isOffline()) {
                save();
            }
        } finally {
            if (getLock().isHeldByCurrentThread()) getLock().unlock();
        }
    }

    default boolean isOffline() {
        return !InvseePlugin.getInstance().getServer().getOfflinePlayer(getUniqueIdOfObservedPlayer()).isOnline();
    }

    default Optional<Player> getPlayerOffline(OfflinePlayer offlinePlayer) {
        Player onlinePlayer = InvseePlugin.getInstance().getServer().getPlayer(offlinePlayer.getUniqueId());
        if (onlinePlayer != null) {
            return Optional.of(onlinePlayer);
        }

        Player cached = getCachedPlayer();
        if (cached != null) {
            return Optional.of(cached);
        }

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        Location location = offlinePlayer.getLocation();
        ServerLevel world;

        if (location == null) {
            world = server.overworld();
        } else {
            world = ((CraftWorld) location.getWorld()).getHandle();
        }

        GameProfile profile = new GameProfile(offlinePlayer.getUniqueId(),
                offlinePlayer.getName() != null ? offlinePlayer.getName() : offlinePlayer.getUniqueId().toString());

        ServerPlayer serverPlayer = new ServerPlayer(server, world, profile, ClientInformation.createDefault());
        if (location != null) {
            // CraftPlayer#loadData performs Folia's entity-thread check before it
            // loads the saved position. Put the surrogate in its owning region first.
            serverPlayer.setPos(location.getX(), location.getY(), location.getZ());
        }
        // Do not call CraftPlayer#loadData here. Canvas adds an entity ownership
        // check to that API, but this temporary player is deliberately not added
        // to a world and therefore can never own a Folia region.
        server.getPlayerList().playerIo.load(serverPlayer.nameAndId())
                .map(tag -> TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag))
                .ifPresent(serverPlayer::load);
        Player target = serverPlayer.getBukkitEntity();
        cache(target);
        return Optional.of(target);
    }

    UUID getUniqueIdOfObservedPlayer();

    void updateObservedInventory();

    void updateSubscriberInventory();

    Set<UUID> getSubscribers();

    void removeSubscriber(UUID subscriber);

    boolean hasSubscriber(UUID subscriber);

    ReentrantLock getLock();

    void cache(Player player);

    Player getCachedPlayer();

    boolean isSubscriber(@NotNull UUID whoClicked);
}
