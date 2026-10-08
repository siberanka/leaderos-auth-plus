package net.leaderos.auth.velocity.handler;

import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.leaderos.auth.velocity.Velocity;

/**
 * Lets a player with a valid session (or a trusted Xbox binding) straight through the limbo.
 */
public class ValidSessionHandler implements LimboSessionHandler {

    private final Velocity plugin;
    private final Player player;

    public ValidSessionHandler(Velocity plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public void onSpawn(Limbo server, LimboPlayer limboPlayer) {
        // Mark the connection authenticated before leaving the limbo: the first server connection
        // follows immediately and would otherwise be refused.
        plugin.setAuthenticated(player, true);
        limboPlayer.disconnect();
    }

}
