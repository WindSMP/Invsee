package at.noahb.invsee.common.listener;

import at.noahb.invsee.InvseePlugin;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Objects;

public class LuckPermsListener {

    private final InvseePlugin instance;

    public LuckPermsListener(InvseePlugin instance, LuckPerms luckPerms) {
        this.instance = instance;

        EventBus eventBus = luckPerms.getEventBus();
        eventBus.subscribe(this.instance, NodeRemoveEvent.class, this::removeNode);
    }

    private void removeNode(NodeRemoveEvent nodeRemoveEvent) {
        if (!nodeRemoveEvent.isUser()) {
            return;
        }
        OfflinePlayer offlinePlayer = this.instance.getServer().getOfflinePlayer(((User) nodeRemoveEvent.getTarget()).getUniqueId());
        if (!(offlinePlayer instanceof Player player)) {
            return;
        }

        String permission = nodeRemoveEvent.getNode().getKey();

        if (Objects.equals(this.instance.getInvseeCommand().getPermission(), permission)) {
            this.instance.getInvseeSessionManager().removeSubscriberFromSession(player, true);
        } else if (Objects.equals(this.instance.getEnderseeCommand().getPermission(), permission)) {
            this.instance.getEnderseeSessionManager().removeSubscriberFromSession(player, true);
        }
    }
}
