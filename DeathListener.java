package dev.strength;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathListener implements Listener {
    private final StrengthPlugin plugin;
    /** "killer:victim" -> last time killer gained strength from that victim */
    private final Map<String, Long> recentKills = new ConcurrentHashMap<>();

    public DeathListener(StrengthPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        FileConfiguration cfg = plugin.getConfig();
        StrengthManager sm = plugin.manager();
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        boolean pvp = killer != null && !killer.equals(victim);

        if (!pvp && cfg.getBoolean("strength.pvp-deaths-only", true)) return;

        // victim side
        UUID vid = victim.getUniqueId();
        int before = sm.get(vid);
        boolean banNow = cfg.getBoolean("death.deathban-at-min", true) && before <= sm.min();
        int after = sm.add(vid, -cfg.getInt("strength.death-loss", 1));
        int lost = before - after;

        if (lost > 0) {
            victim.sendMessage(Component.text("You lost " + lost + " Strength (now " + after + ").", NamedTextColor.RED));
            if (cfg.getBoolean("death.drop-token", true)) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), plugin.items().token(lost));
            }
        }

        // killer side
        if (pvp) {
            long farmMs = cfg.getLong("strength.anti-farm-seconds", 300) * 1000L;
            String key = killer.getUniqueId() + ":" + vid;
            long now = System.currentTimeMillis();
            Long last = recentKills.get(key);
            if (last != null && now - last < farmMs) {
                killer.sendMessage(Component.text("No Strength gained: you killed this player recently.", NamedTextColor.GRAY));
            } else {
                recentKills.put(key, now);
                int kb = sm.get(killer.getUniqueId());
                int ka = sm.add(killer.getUniqueId(), cfg.getInt("strength.kill-gain", 1));
                if (ka > kb) {
                    killer.sendMessage(Component.text("+" + (ka - kb) + " Strength (now " + ka + ").", NamedTextColor.GREEN));
                    announceUnlocks(killer, kb, ka);
                } else {
                    killer.sendMessage(Component.text("You are at max Strength (" + ka + ").", NamedTextColor.GOLD));
                }
            }
        }

        if (banNow) {
            sm.deathBan(vid, cfg.getLong("death.deathban-hours", 24));
            String msg = sm.banMessage(vid);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) victim.kick(Component.text(msg, NamedTextColor.RED));
            }, 1L);
        }
    }

    private void announceUnlocks(Player p, int before, int after) {
        int passive = plugin.getConfig().getInt("abilities.passive-unlock", 2);
        int ult = plugin.getConfig().getInt("abilities.ultimate-unlock", 4);
        WeaponClass wc = plugin.manager().weaponClass(p.getUniqueId());
        if (wc == null) return;
        if (before < passive && after >= passive) {
            p.sendMessage(Component.text("Passive unlocked: " + wc.passive, NamedTextColor.AQUA));
        }
        if (before < ult && after >= ult) {
            p.sendMessage(Component.text("Ultimate unlocked: " + wc.ultimate + " (hold " + wc.display
                    + ", Sneak + swap-hands key)", NamedTextColor.LIGHT_PURPLE));
        }
    }
}
