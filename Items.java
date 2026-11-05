package dev.strength;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class Items {
    private final NamespacedKey tokenKey;
    private final NamespacedKey reviveKey;

    public Items(StrengthPlugin plugin) {
        this.tokenKey = new NamespacedKey(plugin, "strength_token");
        this.reviveKey = new NamespacedKey(plugin, "revive_heart");
    }

    public ItemStack token(int amount) {
        return build(Material.NETHER_STAR, tokenKey, "Strength Token", NamedTextColor.RED,
                "Right-click to gain +1 Strength.", amount);
    }

    public ItemStack reviveHeart(int amount) {
        return build(Material.HEART_OF_THE_SEA, reviveKey, "Revive Heart", NamedTextColor.GREEN,
                "Hold this and run /strength revive <player>.", amount);
    }

    public boolean isToken(ItemStack item) { return has(item, tokenKey); }
    public boolean isReviveHeart(ItemStack item) { return has(item, reviveKey); }

    private boolean has(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private ItemStack build(Material mat, NamespacedKey key, String name, NamedTextColor color, String lore, int amount) {
        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
