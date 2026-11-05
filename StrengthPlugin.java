package dev.strength;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class StrengthPlugin extends JavaPlugin {
    private StrengthManager manager;
    private Items items;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new StrengthManager(this);
        manager.load();
        items = new Items(this);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new JoinListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new UltimateListener(this), this);
        pm.registerEvents(new PotionBlockListener(this), this);
        pm.registerEvents(new ItemListener(this), this);

        StrengthCommand cmd = new StrengthCommand(this);
        getCommand("strength").setExecutor(cmd);
        getCommand("strength").setTabCompleter(cmd);

        // autosave every 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, manager::save, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    public StrengthManager manager() { return manager; }
    public Items items() { return items; }
}
