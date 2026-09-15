package at.noahb.invsee.invsee.session;

import at.noahb.invsee.InvseePlugin;
import at.noahb.invsee.common.session.SessionManager;
import org.bukkit.OfflinePlayer;

public class InvseeSessionManager extends SessionManager {

    public InvseeSessionManager(InvseePlugin instance) {
        super(instance);
    }

    @Override
    public InvseeSession createSession(OfflinePlayer offlinePlayer) {
        return new InvseeSession(offlinePlayer);
    }

}
