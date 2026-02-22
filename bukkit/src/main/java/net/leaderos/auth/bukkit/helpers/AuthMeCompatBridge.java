package net.leaderos.auth.bukkit.helpers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.AuthMeAsyncPreLoginEvent;
import fr.xephi.authme.events.AuthMeAsyncPreRegisterEvent;
import fr.xephi.authme.events.FailedLoginEvent;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.LogoutEvent;
import fr.xephi.authme.events.RegisterEvent;
import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import org.bukkit.entity.Player;

import java.util.Locale;

public class AuthMeCompatBridge {

    private final Bukkit plugin;

    public AuthMeCompatBridge(Bukkit plugin) {
        this.plugin = plugin;
    }

    public void init() {
        AuthMeApi.setProvider(new AuthMeApi.Provider() {
            @Override
            public String getPluginVersion() {
                return plugin.getDescription().getVersion();
            }

            @Override
            public boolean isAuthenticated(Player player) {
                return player != null && plugin.isAuthenticated(player);
            }

            @Override
            public boolean isRegistered(String playerName) {
                if (playerName == null || playerName.isEmpty()) {
                    return false;
                }
                GameSessionResponse session = plugin.getSessions().get(playerName);
                if (session == null) {
                    return false;
                }
                return session.getState() != SessionState.REGISTER_REQUIRED;
            }

            @Override
            public void forceLogin(Player player) {
                if (player == null) {
                    return;
                }
                plugin.forceAuthenticate(player);
            }

            @Override
            public void forceLogout(Player player) {
                if (player == null) {
                    return;
                }
                plugin.forceUnauthenticate(player);
            }
        });
    }

    public void shutdown() {
        AuthMeApi.setProvider(null);
    }

    public boolean callPreLogin(Player player) {
        AuthMeAsyncPreLoginEvent event = new AuthMeAsyncPreLoginEvent(player, false);
        plugin.getServer().getPluginManager().callEvent(event);
        return event.canLogin();
    }

    public boolean callPreRegister(Player player) {
        AuthMeAsyncPreRegisterEvent event = new AuthMeAsyncPreRegisterEvent(player, false);
        plugin.getServer().getPluginManager().callEvent(event);
        return event.canRegister();
    }

    public void callLogin(Player player) {
        plugin.getServer().getPluginManager().callEvent(new LoginEvent(player));
        sendAuthMeBungeeMessage(player, "login");
    }

    public void callRegister(Player player) {
        plugin.getServer().getPluginManager().callEvent(new RegisterEvent(player));
    }

    public void callFailedLogin(Player player) {
        plugin.getServer().getPluginManager().callEvent(new FailedLoginEvent(player, false));
    }

    public void callLogout(Player player) {
        plugin.getServer().getPluginManager().callEvent(new LogoutEvent(player));
        sendAuthMeBungeeMessage(player, "logout");
    }

    public void broadcastUnauthenticated(Player player) {
        sendAuthMeBungeeMessage(player, "logout");
    }

    private void sendAuthMeBungeeMessage(Player player, String typeId) {
        if (player == null || !player.isOnline()) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ONLINE");
        out.writeUTF("AuthMe.v2.Broadcast");

        ByteArrayDataOutput dataOut = ByteStreams.newDataOutput();
        dataOut.writeUTF(typeId);
        dataOut.writeUTF(player.getName().toLowerCase(Locale.ROOT));

        byte[] dataBytes = dataOut.toByteArray();
        out.writeShort(dataBytes.length);
        out.write(dataBytes);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

}
