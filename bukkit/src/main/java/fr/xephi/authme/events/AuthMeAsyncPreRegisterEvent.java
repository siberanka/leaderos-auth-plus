package fr.xephi.authme.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a player uses register command before actual registration.
 */
public class AuthMeAsyncPreRegisterEvent extends CustomEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private boolean canRegister = true;

    public AuthMeAsyncPreRegisterEvent(Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean canRegister() {
        return canRegister;
    }

    public void setCanRegister(boolean canRegister) {
        this.canRegister = canRegister;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
