package dev.strength;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ultimates: hold your class weapon, Sneak + swap-hands key (F). */
public final class UltimateListener implements Listener {
    private final StrengthPlugin plugin;
    private final NamespacedKey siegeKey;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public UltimateListener(StrengthPlugin plugin) {
        this.plugin = plugin;
        this.siegeKey = new NamespacedKey(plugin, "siege_bolt");
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;

        WeaponClass held = WeaponClass.of(p.getInventory().getItemInMainHand());
        if (held == null) return;
        StrengthManager sm = plugin.manager();
        if (sm.weaponClass(p.getUniqueId()) != held) return;
        if (sm.get(p.getUniqueId()) < plugin.getConfig().getInt("abilities.ultimate-unlock", 4)) return;

        e.setCancelled(true);

        long now = System.currentTimeMillis();
        long cdMs = plugin.getConfig().getLong("abilities.ultimate-cooldown-seconds", 60) * 1000L;
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null && now - last < cdMs) {
            long left = (cdMs - (now - last) + 999) / 1000;
            p.sendActionBar(Component.text(held.ultimate + " ready in " + left + "s", NamedTextColor.GRAY));
            return;
        }
        cooldowns.put(p.getUniqueId(), now);
        p.sendActionBar(Component.text(held.ultimate + "!", NamedTextColor.LIGHT_PURPLE));

        switch (held) {
            case SWORD -> bladeDance(p);
            case AXE -> earthsplitter(p);
            case TRIDENT -> riptideSurge(p);
            case BOW -> arrowVolley(p);
            case CROSSBOW -> siegeBolt(p);
            case SHIELD -> bulwark(p);
        }
    }

    private boolean valid(Player self, Entity en) {
        return en instanceof LivingEntity && !(en instanceof ArmorStand) && !en.equals(self);
    }

    // ---- SWORD ----
    private void bladeDance(Player p) {
        p.setVelocity(p.getLocation().getDirection().setY(0.1).normalize().multiply(1.6));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override
            public void run() {
                if (t++ >= 8 || !p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                Location l = p.getLocation().add(0, 1, 0);
                l.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 2, 0.4, 0.3, 0.4, 0);
                for (Entity en : p.getNearbyEntities(2.5, 1.5, 2.5)) {
                    if (valid(p, en) && hit.add(en.getUniqueId())) ((LivingEntity) en).damage(7.0, p);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- AXE ----
    private void earthsplitter(Player p) {
        Location c = p.getLocation();
        World w = c.getWorld();
        w.spawnParticle(Particle.EXPLOSION, c, 1);
        w.spawnParticle(Particle.CLOUD, c, 40, 2, 0.2, 2, 0.05);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        for (Entity en : p.getNearbyEntities(4, 2, 4)) {
            if (!valid(p, en)) continue;
            Vector away = en.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() > 0) away.normalize().multiply(0.5);
            away.setY(0.9);
            ((LivingEntity) en).damage(6.0, p);
            en.setVelocity(away);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1));
    }

    // ---- TRIDENT ----
    private void riptideSurge(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 0));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override
            public void run() {
                if (t++ >= 25 || !p.isOnline() || p.isDead()) {
                    if (p.isOnline()) p.setFallDistance(0);
                    cancel();
                    return;
                }
                p.setVelocity(p.getLocation().getDirection().normalize().multiply(1.1));
                p.setFallDistance(0);
                Location l = p.getLocation().add(0, 0.5, 0);
                l.getWorld().spawnParticle(Particle.SPLASH, l, 30, 0.6, 0.3, 0.6, 0.1);
                for (Entity en : p.getNearbyEntities(2, 1.5, 2)) {
                    if (valid(p, en) && hit.add(en.getUniqueId())) {
                        ((LivingEntity) en).damage(4.0, p);
                        en.setVelocity(p.getLocation().getDirection().multiply(0.8).setY(0.4));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- BOW ----
    private void arrowVolley(Player p) {
        Vector base = p.getEyeLocation().getDirection();
        for (int i = -3; i <= 3; i++) {
            Vector v = base.clone().rotateAroundY(Math.toRadians(i * 6)).multiply(2.6);
            Arrow a = p.launchProjectile(Arrow.class, v);
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
    }

    // ---- CROSSBOW ----
    private void siegeBolt(Player p) {
        Arrow a = p.launchProjectile(Arrow.class, p.getEyeLocation().getDirection().multiply(4.0));
        a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        a.setCritical(true);
        a.getPersistentDataContainer().set(siegeKey, PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.6f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!pr.getPersistentDataContainer().has(siegeKey, PersistentDataType.BYTE)) return;
        Location l = pr.getLocation();
        World w = l.getWorld();
        w.spawnParticle(Particle.EXPLOSION, l, 1);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        ProjectileSource src = pr.getShooter();
        // manual area damage: no block damage, never hurts the shooter
        for (Entity en : w.getNearbyEntities(l, 3.5, 3.5, 3.5)) {
            if (!(en instanceof LivingEntity le) || en instanceof ArmorStand || en.equals(src)) continue;
            if (src instanceof Player sp) le.damage(8.0, sp);
            else le.damage(8.0);
        }
        pr.remove();
    }

    // ---- SHIELD ----
    private void bulwark(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1));
        Location c = p.getLocation();
        c.getWorld().spawnParticle(Particle.CLOUD, c.add(0, 1, 0), 50, 1.5, 0.5, 1.5, 0.1);
        c.getWorld().playSound(c, Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
        for (Entity en : p.getNearbyEntities(4, 2, 4)) {
            if (!valid(p, en)) continue;
            Vector away = en.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 0) away.normalize().multiply(1.2);
            away.setY(0.4);
            ((LivingEntity) en).damage(2.0, p);
            en.setVelocity(away);
        }
    }
}
