package net.leaderos.auth.shared.security;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Produces stable keys for security decisions. IPv6 privacy addresses are
 * grouped by prefix so rotating the host portion cannot reset a limit.
 */
public final class IpAddressNormalizer {

    private IpAddressNormalizer() {
    }

    public static String normalize(String input, int ipv6PrefixLength) {
        return normalize(input, ipv6PrefixLength, true);
    }

    /**
     * Normalizes an address without grouping IPv6 networks, for comparing two connections exactly.
     * IPv4-mapped IPv6 addresses become plain IPv4.
     */
    public static String exact(String input) {
        return normalize(input, 128, false);
    }

    /**
     * True when both values are valid and denote the same address.
     */
    public static boolean sameAddress(String first, String second) {
        try {
            return exact(first).equals(exact(second));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String normalize(String input, int ipv6PrefixLength, boolean groupIpv6) {
        if (input == null) {
            throw new IllegalArgumentException("IP address cannot be null");
        }

        String value = input.trim();
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("IP address cannot be empty");
        }
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9A-Fa-f:.]+")) {
                throw new IllegalArgumentException("Invalid IPv6 address: " + input);
            }
        } else if (!value.matches("[0-9.]+")) {
            // Never perform DNS resolution for a value used as a security identity.
            throw new IllegalArgumentException("Invalid IPv4 address: " + input);
        }

        try {
            InetAddress address = InetAddress.getByName(value);
            byte[] bytes = address.getAddress();
            if (address instanceof Inet6Address) {
                if (isIpv4Mapped(bytes)) {
                    return (bytes[12] & 0xff) + "." + (bytes[13] & 0xff) + "."
                            + (bytes[14] & 0xff) + "." + (bytes[15] & 0xff);
                }

                if (!groupIpv6) {
                    return InetAddress.getByAddress(bytes).getHostAddress().toLowerCase(Locale.ROOT);
                }
                int prefix = clampIpv6Prefix(ipv6PrefixLength);
                mask(bytes, prefix);
                return InetAddress.getByAddress(bytes).getHostAddress().toLowerCase(Locale.ROOT) + "/" + prefix;
            }
            return address.getHostAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Invalid IP address: " + input, exception);
        }
    }

    public static int clampIpv6Prefix(int prefixLength) {
        return Math.max(48, Math.min(64, prefixLength));
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static void mask(byte[] bytes, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        if (remainingBits != 0) {
            bytes[fullBytes] &= (byte) (0xff << (8 - remainingBits));
            fullBytes++;
        }
        for (int i = fullBytes; i < bytes.length; i++) {
            bytes[i] = 0;
        }
    }
}
