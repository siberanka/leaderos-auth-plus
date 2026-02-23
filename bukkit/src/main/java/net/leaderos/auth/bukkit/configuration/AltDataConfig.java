package net.leaderos.auth.bukkit.configuration;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration file to store Alt Account and Registration tracking data
 */
@Getter
@Setter
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class AltDataConfig extends OkaeriConfig {

    @Comment("Tracks which names are associated with an IP address")
    private Map<String, List<String>> ipToNames = new ConcurrentHashMap<>();

    @Comment("Tracks how many successful registrations occurred from an IP address")
    private Map<String, Integer> ipRegistrations = new ConcurrentHashMap<>();

    public synchronized void addRegistration(String ip) {
        ipRegistrations.put(ip, ipRegistrations.getOrDefault(ip, 0) + 1);
    }

    public synchronized boolean hasReachedRegistrationLimit(String ip, int max) {
        return ipRegistrations.getOrDefault(ip, 0) >= max;
    }

    public synchronized boolean addAccountToIp(String ip, String accountName) {
        List<String> names = ipToNames.computeIfAbsent(ip, k -> new ArrayList<>());
        if (!names.contains(accountName.toLowerCase())) {
            names.add(accountName.toLowerCase());
            return true;
        }
        return false;
    }

    public List<String> getAccountsByIp(String ip) {
        return ipToNames.getOrDefault(ip, new ArrayList<>());
    }
}
