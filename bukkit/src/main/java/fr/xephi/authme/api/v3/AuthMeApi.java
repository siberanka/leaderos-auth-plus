package fr.xephi.authme.api.v3;

import org.bukkit.entity.Player;

/**
 * Minimal AuthMeApi compatibility layer exposed by LeaderOS-Auth.
 */
public class AuthMeApi {

    private static final AuthMeApi INSTANCE = new AuthMeApi();
    private static volatile Provider provider = new NoOpProvider();

    public static AuthMeApi getInstance() {
        return INSTANCE;
    }

    public static void setProvider(Provider newProvider) {
        provider = newProvider == null ? new NoOpProvider() : newProvider;
    }

    public String getPluginVersion() {
        return provider.getPluginVersion();
    }

    public boolean isAuthenticated(Player player) {
        return provider.isAuthenticated(player);
    }

    public boolean isRegistered(String playerName) {
        return provider.isRegistered(playerName);
    }

    public void forceLogin(Player player) {
        provider.forceLogin(player);
    }

    public void forceLogout(Player player) {
        provider.forceLogout(player);
    }

    public interface Provider {
        String getPluginVersion();
        boolean isAuthenticated(Player player);
        boolean isRegistered(String playerName);
        void forceLogin(Player player);
        void forceLogout(Player player);
    }

    private static class NoOpProvider implements Provider {
        @Override
        public String getPluginVersion() {
            return "compat";
        }

        @Override
        public boolean isAuthenticated(Player player) {
            return false;
        }

        @Override
        public boolean isRegistered(String playerName) {
            return false;
        }

        @Override
        public void forceLogin(Player player) {}

        @Override
        public void forceLogout(Player player) {}
    }
}
