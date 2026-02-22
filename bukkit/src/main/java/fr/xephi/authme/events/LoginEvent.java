package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player has successfully logged in or registered.
 */
public class LoginEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public LoginEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Deprecated
    public boolean isLogin() {
        return true;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
