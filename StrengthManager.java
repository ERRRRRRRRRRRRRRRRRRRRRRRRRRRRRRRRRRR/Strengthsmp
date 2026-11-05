package dev.strength;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StrengthManager {
    private final StrengthPlugin plugin;
    private final Map<UUID, Integer> strength = new ConcurrentHashMap<>();
    private final Map<UUID, WeaponClass> classes = new ConcurrentHashMap<>();
    /** expiry in epoch millis, 0 = until revived */
    private final Map<UUID, Long> deathBans = new ConcurrentHashMap<>();
    private final File file;

    public StrengthManager(StrengthPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public int min() { return plugin.getConfig().getInt("strength.min", -3); }
    public int max() { return plugin.getConfig().getInt("strength.max", 5); }
    public int start() { return plugin.getConfig().getInt("strength.start", 0); }

    public int get(UUID id) { return strength.getOrDefault(id, start()); }

    public int set(UUID id, int value) {
        int clamped = Math.max(min(), Math.min(max(), value));
        strength.put(id, clamped);
        return clamped;
    }

    public int add(UUID id, int delta) { return set(id, get(id) + delta); }

    public WeaponClass weaponClass(UUID id) { return classes.get(id); }
    public void setWeaponClass(UUID id, WeaponClass wc) { classes.put(id, wc); }

    // ---- death bans ----

    public void deathBan(UUID id, long hours) {
        deathBans.put(id, hours <= 0 ? 0L : System.currentTimeMillis() + hours * 3_600_000L);
    }

    public boolean isDeathBanned(UUID id) {
        Long expiry = deathBans.get(id);
        if (expiry == null) return false;
        if (expiry == 0L) return true;
        if (System.currentTimeMillis() >= expiry) {
            deathBans.remove(id);
            return false;
        }
        return true;
    }

    /** @return expiry millis, 0 if permanent, null if not banned */
    public Long deathBanExpiry(UUID id) { return isDeathBanned(id) ? deathBans.get(id) : null; }

    public void revive(UUID id, int newStrength) {
        deathBans.remove(id);
        set(id, newStrength);
    }

    public String banMessage(UUID id) {
        Long expiry = deathBanExpiry(id);
        if (expiry == null) return "";
        if (expiry == 0L) return "You are death-banned until another player revives you.";
        long mins = Math.max(1, (expiry - System.currentTimeMillis()) / 60_000L);
        return "You are death-banned for another " + (mins / 60) + "h " + (mins % 60) + "m, or until a player revives you.";
    }

    // ---- leaderboard ----

    public List<Map.Entry<UUID, Integer>> top(int n) {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(strength.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(n, list.size()));
    }

    // ---- persistence ----

    public void load() {
        strength.clear();
        classes.clear();
        deathBans.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (s.contains(key + ".strength")) strength.put(id, s.getInt(key + ".strength"));
            WeaponClass wc = WeaponClass.parse(s.getString(key + ".class"));
            if (wc != null) classes.put(id, wc);
            if (s.contains(key + ".deathban")) deathBans.put(id, s.getLong(key + ".deathban"));
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        Set<UUID> ids = new HashSet<>(strength.keySet());
        ids.addAll(classes.keySet());
        ids.addAll(deathBans.keySet());
        for (UUID id : ids) {
            String base = "players." + id;
            if (strength.containsKey(id)) y.set(base + ".strength", strength.get(id));
            WeaponClass wc = classes.get(id);
            if (wc != null) y.set(base + ".class", wc.name());
            Long ban = deathBans.get(id);
            if (ban != null) y.set(base + ".deathban", ban);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save players.yml: " + ex.getMessage());
        }
    }
}
