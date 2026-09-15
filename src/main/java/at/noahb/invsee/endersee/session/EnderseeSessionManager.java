package at.noahb.invsee.endersee.session;

import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.session.SessionManager;
import org.bukkit.OfflinePlayer;

public class EnderseeSessionManager extends SessionManager {
    public EnderseeSessionManager(InvseePlugin instance) {
        super(instance);
    }

    @Override
    protected EnderseeSession createSession(OfflinePlayer offlinePlayer) {
        return new EnderseeSession(offlinePlayer);
    }
}
