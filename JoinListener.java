package dev.strength;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Random;

public final class JoinListener implements Listener {
    private final StrengthPlugin plugin;
    private final Random random = new Random();

    public JoinListener(StrengthPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        StrengthManager sm = plugin.manager();
        if (sm.isDeathBanned(e.getUniqueId())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    Component.text(sm.banMessage(e.getUniqueId()), NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        StrengthManager sm = plugin.manager();
        if (sm.weaponClass(p.getUniqueId()) != null) return;

        String mode = plugin.getConfig().getString("classes.assignment", "RANDOM");
        if (mode.equalsIgnoreCase("RANDOM")) {
            WeaponClass[] all = WeaponClass.values();
            WeaponClass wc = all[random.nextInt(all.length)];
            sm.setWeaponClass(p.getUniqueId(), wc);
            p.sendMessage(Component.text("You were assigned the " + wc.display + " class. ", NamedTextColor.GOLD)
                    .append(Component.text("Passive: " + wc.passive + ", Ultimate: " + wc.ultimate
                            + (plugin.getConfig().getBoolean("classes.allow-change", true)
                            ? ". Change it with /strength class <name>." : "."), NamedTextColor.GRAY)));
        } else {
            p.sendMessage(Component.text("Pick your weapon class with /strength class <sword|axe|trident|bow|crossbow|shield>.",
                    NamedTextColor.GOLD));
        }
    }
}
