package net.leaderos.auth.shared.security;

import java.util.UUID;

/**
 * Decides whether a login may replace a player of the same name who is still online.
 *
 * <p>A quick reconnect (a Bedrock pack reconnect, a network hiccup, a proxy that has not closed the
 * old backend connection yet) arrives while the old connection is still listed. Only when it is the
 * same profile (UUID) from the same exact address is the server allowed to drop the old
 * connection; any other duplicate is refused so nobody can kick an online player by joining with
 * that player's name. The new connection still has to authenticate on its own.</p>
 */
public final class DuplicateLoginPolicy {

    private DuplicateLoginPolicy() {
    }

    /**
     * @param onlineId   UUID of the player currently online
     * @param onlineIp   address of the player currently online
     * @param joiningId  UUID of the joining connection
     * @param joiningIp  address of the joining connection
     * @return true when the joining connection may replace the online one
     */
    public static boolean mayReplace(UUID onlineId, String onlineIp, UUID joiningId, String joiningIp) {
        return onlineId != null && onlineId.equals(joiningId)
                && IpAddressNormalizer.sameAddress(onlineIp, joiningIp);
    }
}
