package dev.strength;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum WeaponClass {
    SWORD("Sword", "Fleet Edge", "Blade Dance"),
    AXE("Axe", "Rend", "Earthsplitter"),
    TRIDENT("Trident", "Stormcaller", "Riptide Surge"),
    BOW("Bow", "Hunter's Mark", "Arrow Volley"),
    CROSSBOW("Crossbow", "Piercing Bolt", "Siege Bolt"),
    SHIELD("Shield", "Riposte", "Bulwark");

    public final String display;
    public final String passive;
    public final String ultimate;

    WeaponClass(String display, String passive, String ultimate) {
        this.display = display;
        this.passive = passive;
        this.ultimate = ultimate;
    }

    public static WeaponClass parse(String s) {
        if (s == null) return null;
        for (WeaponClass w : values()) {
            if (w.name().equalsIgnoreCase(s)) return w;
        }
        return null;
    }

    public static WeaponClass of(ItemStack item) {
        if (item == null) return null;
        Material m = item.getType();
        String n = m.name();
        if (n.endsWith("_SWORD")) return SWORD;
        if (n.endsWith("_AXE")) return AXE;
        return switch (m) {
            case TRIDENT -> TRIDENT;
            case BOW -> BOW;
            case CROSSBOW -> CROSSBOW;
            case SHIELD -> SHIELD;
            default -> null;
        };
    }
}
