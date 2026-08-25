package net.leaderos.auth.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IpAddressNormalizerTest {

    @Test
    void normalizesEquivalentIpv4Representations() {
        assertEquals("192.0.2.25", IpAddressNormalizer.normalize("192.0.2.25", 64));
        assertEquals("192.0.2.25", IpAddressNormalizer.normalize("::ffff:192.0.2.25", 64));
    }

    @Test
    void groupsIpv6PrivacyAddressesBySubnet() {
        String first = IpAddressNormalizer.normalize("2001:db8:1234:5678::1", 64);
        String rotated = IpAddressNormalizer.normalize("2001:db8:1234:5678:abcd::99", 64);
        String otherSubnet = IpAddressNormalizer.normalize("2001:db8:1234:5679::1", 64);

        assertEquals(first, rotated);
        assertNotEquals(first, otherSubnet);
        assertEquals("2001:db8:1234:5678:0:0:0:0/64", first);
    }

    @Test
    void clampsUnsafeIpv6PrefixConfiguration() {
        assertEquals(48, IpAddressNormalizer.clampIpv6Prefix(1));
        assertEquals(64, IpAddressNormalizer.clampIpv6Prefix(128));
    }

    @Test
    void rejectsInvalidAddresses() {
        assertThrows(IllegalArgumentException.class,
                () -> IpAddressNormalizer.normalize("not-an-ip", 64));
    }
}
