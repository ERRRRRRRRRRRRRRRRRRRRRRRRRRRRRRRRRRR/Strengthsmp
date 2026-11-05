package dev.strength;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ItemListener implements Listener {
    private final StrengthPlugin plugin;

    public ItemListener(StrengthPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null) return;

        Items items = plugin.items();
        Player p = e.getPlayer();
        if (items.isToken(item)) {
            e.setCancelled(true);
            StrengthManager sm = plugin.manager();
            if (sm.get(p.getUniqueId()) >= sm.max()) {
                p.sendActionBar(Component.text("You are already at max Strength.", NamedTextColor.GOLD));
                return;
            }
            item.setAmount(item.getAmount() - 1);
            int now = sm.add(p.getUniqueId(), 1);
            p.sendMessage(Component.text("+1 Strength (now " + now + ").", NamedTextColor.GREEN));
        } else if (items.isReviveHeart(item)) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("Run /strength revive <player> while holding this.", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack ing : e.getInventory().getMatrix()) {
            if (ing != null && (plugin.items().isToken(ing) || plugin.items().isReviveHeart(ing))) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
