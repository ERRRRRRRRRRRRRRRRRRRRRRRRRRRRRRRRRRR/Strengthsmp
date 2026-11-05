package dev.strength;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Strength damage scaling + passive abilities. */
public final class CombatListener implements Listener {
    private final StrengthPlugin plugin;
    private final Random random = new Random();
    /** attackers currently dealing plugin-caused damage, so we do not recurse */
    private final Set<UUID> reentry = new HashSet<>();
    private final Map<UUID, Long> passiveCooldown = new ConcurrentHashMap<>();

    public CombatListener(StrengthPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (reentry.contains(damager.getUniqueId())) return;

        if (damager instanceof Player p
                && (e.getCause() == DamageCause.ENTITY_ATTACK || e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK)) {
            scale(p, e);
            meleePassive(p, e);
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            projectilePassive(p, proj, e);
        }

        if (e.getEntity() instanceof Player victim) shieldPassive(victim, e);
    }

    // ---- strength scaling ----

    private void scale(Player p, EntityDamageByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("strength.apply-to-mobs", true) && !(e.getEntity() instanceof Player)) return;
        double perPoint = plugin.getConfig().getDouble("strength.damage-per-point", 0.10);
        int s = plugin.manager().get(p.getUniqueId());
        double mult = Math.max(0.0, 1.0 + perPoint * s);
        e.setDamage(e.getDamage() * mult);
    }

    // ---- passives ----

    private boolean ready(Player p, WeaponClass wc) {
        StrengthManager sm = plugin.manager();
        if (sm.weaponClass(p.getUniqueId()) != wc) return false;
        return sm.get(p.getUniqueId()) >= plugin.getConfig().getInt("abilities.passive-unlock", 2);
    }

    private boolean proc(Player p) {
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("abilities.passive-internal-cooldown-seconds", 3) * 1000L;
        Long last = passiveCooldown.get(p.getUniqueId());
        if (last != null && now - last < cd) return false;
        if (random.nextDouble() >= plugin.getConfig().getDouble("abilities.passive-chance", 0.25)) return false;
        passiveCooldown.put(p.getUniqueId(), now);
        return true;
    }

    private void meleePassive(Player p, EntityDamageByEntityEvent e) {
        WeaponClass held = WeaponClass.of(p.getInventory().getItemInMainHand());
        if (held == null || !ready(p, held)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        switch (held) {
            case SWORD -> {
                if (proc(p)) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
            }
            case AXE -> {
                if (proc(p)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1));
                    hit(p, victim, 2.0);
                }
            }
            case TRIDENT -> {
                if (proc(p)) lightning(p, victim);
            }
            default -> { }
        }
    }

    private void projectilePassive(Player p, Projectile proj, EntityDamageByEntityEvent e) {
        WeaponClass wc;
        if (proj instanceof Trident) {
            wc = WeaponClass.TRIDENT;
        } else if (proj instanceof AbstractArrow arrow) {
            ItemStack weapon = arrow.getWeapon();
            wc = weapon != null ? WeaponClass.of(weapon) : null;
            if (wc == null) wc = WeaponClass.of(p.getInventory().getItemInMainHand());
        } else {
            return;
        }
        if (wc == null || !ready(p, wc)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        switch (wc) {
            case BOW -> {
                if (proc(p)) victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            }
            case CROSSBOW -> e.setDamage(e.getDamage() * 1.3);
            case TRIDENT -> {
                if (proc(p)) lightning(p, victim);
            }
            default -> { }
        }
    }

    private void shieldPassive(Player victim, EntityDamageByEntityEvent e) {
        if (!victim.isBlocking() || !ready(victim, WeaponClass.SHIELD)) return;
        if (!(e.getDamager() instanceof LivingEntity attacker)) return;
        if (!proc(victim)) return;

        Vector push = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (push.lengthSquared() > 0) push.normalize().multiply(0.8);
        push.setY(0.3);
        attacker.setVelocity(push);
        hit(victim, attacker, 3.0);
    }

    private void lightning(Player p, LivingEntity victim) {
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        hit(p, victim, 5.0);
    }

    /** Deal plugin damage without re-triggering our own listener. */
    private void hit(Player source, LivingEntity target, double amount) {
        reentry.add(source.getUniqueId());
        try {
            target.damage(amount, source);
        } finally {
            reentry.remove(source.getUniqueId());
        }
    }
}
