package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player uses login command before actual verification.
 */
public class AuthMeAsyncPreLoginEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private boolean canLogin = true;

    public AuthMeAsyncPreLoginEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean canLogin() {
        return canLogin;
    }

    public void setCanLogin(boolean canLogin) {
        this.canLogin = canLogin;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
