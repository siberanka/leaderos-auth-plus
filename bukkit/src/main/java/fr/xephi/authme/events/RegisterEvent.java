package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player has successfully registered.
 */
public class RegisterEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public RegisterEvent(Player player) {
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
