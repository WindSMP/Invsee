package at.noahb.invsee.common.command;

import at.noahb.invsee.Constants;
import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.session.SessionManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public abstract class AbstractPluginCommand extends Command {

    private final InvseePlugin instance;

    public AbstractPluginCommand(InvseePlugin instance, String name, String description, String usage, String permission, List<String> aliases) {
        super(name, description, usage, aliases);
        this.instance = instance;

        setPermission(permission);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (args.length != 1) {
            sender.sendMessage(text(getUsage()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(text("Command can only be executed by a player.", RED));
            return true;
        }

        if (!player.hasPermission(Objects.requireNonNull(getPermission(), this::getCommandPermission))) {
            sender.sendMessage(text("You don't have permissions to use that command", RED));
            return true;
        }

        // Fast path: target player is already online
        Player onlineTarget = this.instance.getServer().getPlayerExact(args[0]);
        if (onlineTarget != null) {
            if (player.getUniqueId().equals(onlineTarget.getUniqueId())) {
                player.sendMessage(text("You cannot view your own inventory.", RED));
                return true;
            }
            getSessionManager().addSubscriberToSession(onlineTarget, player.getUniqueId());
            return true;
        }

        // Slow path: offload player lookup & disk checks to Folia's AsyncScheduler
        this.instance.getServer().getAsyncScheduler().runNow(this.instance, task -> {
            OfflinePlayer offlineTarget = this.instance.getServer().getOfflinePlayer(args[0]);
            UUID targetUuid = offlineTarget.getUniqueId();

            if (player.getUniqueId().equals(targetUuid)) {
                player.getScheduler().run(this.instance, t -> player.sendMessage(text("You cannot view your own inventory.", RED)), null);
                return;
            }

            if (!offlineTarget.isOnline() && !offlineTarget.hasPlayedBefore()) {
                if (!InvseePlugin.getInstance().getConfig().getBoolean(Constants.LOOKUP_UNSEEN_CONFIG)) {
                    String name = Objects.requireNonNullElse(offlineTarget.getName(), targetUuid.toString());
                    player.getScheduler().run(this.instance, t -> player.sendMessage(text("Player ", RED)
                            .append(text(name))
                            .append(text(" has never played on this server."))), null);
                    return;
                }

                if (!player.hasPermission(Constants.LOOKUP_UNSEEN_PERMISSION)) {
                    String name = Objects.requireNonNullElse(offlineTarget.getName(), targetUuid.toString());
                    player.getScheduler().run(this.instance, t -> player.sendMessage(text("Player ", RED)
                            .append(text(name))
                            .append(text(" has never played on this server."))), null);
                    return;
                }
            }

            // Pre-fetch location in this async thread so regionScheduler does not block on disk reading later
            offlineTarget.getLocation();

            player.getScheduler().run(this.instance, t -> {
                if (!player.isOnline()) {
                    return;
                }
                getSessionManager().addSubscriberToSession(offlineTarget, player.getUniqueId());
            }, null);
        });

        return true;
    }

    protected InvseePlugin getInstance() {
        return this.instance;
    }

    protected abstract String getCommandPermission();

    protected abstract SessionManager getSessionManager();
}
