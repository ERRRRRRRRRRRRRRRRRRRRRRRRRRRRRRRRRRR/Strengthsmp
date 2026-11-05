package dev.strength;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/** Vanilla nerf: Strength potions/effects are blocked, the plugin owns strength. */
public final class PotionBlockListener implements Listener {
    private final StrengthPlugin plugin;

    public PotionBlockListener(StrengthPlugin plugin) { this.plugin = plugin; }

    private boolean enabled() { return plugin.getConfig().getBoolean("potions.block-strength", true); }

    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        if (!enabled()) return;
        if (!(e.getItem().getItemMeta() instanceof PotionMeta pm)) return;
        if (hasStrength(pm)) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Component.text("Strength potions are disabled on this server.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!enabled()) return;
        PotionEffect n = e.getNewEffect();
        if (n == null || !n.getType().equals(PotionEffectType.STRENGTH)) return;
        if (e.getCause() == EntityPotionEffectEvent.Cause.PLUGIN
                || e.getCause() == EntityPotionEffectEvent.Cause.COMMAND) return;
        e.setCancelled(true);
    }

    private boolean hasStrength(PotionMeta pm) {
        PotionType base = pm.getBasePotionType();
        if (base != null) {
            for (PotionEffect ef : base.getPotionEffects()) {
                if (ef.getType().equals(PotionEffectType.STRENGTH)) return true;
            }
        }
        for (PotionEffect ef : pm.getCustomEffects()) {
            if (ef.getType().equals(PotionEffectType.STRENGTH)) return true;
        }
        return false;
    }
}
