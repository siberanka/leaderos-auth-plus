package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player enters a wrong password.
 */
public class FailedLoginEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public FailedLoginEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
