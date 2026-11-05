package dev.strength;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StrengthCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "strengthsmp.admin";
    private final StrengthPlugin plugin;

    public StrengthCommand(StrengthPlugin plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer target(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayer(name);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StrengthManager sm = plugin.manager();

        if (args.length == 0) {
            if (sender instanceof Player p) showPlayer(sender, p);
            else msg(sender, "Usage: /strength <get|top|class|withdraw|revive|set|add|give|reload>", NamedTextColor.GRAY);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "get" -> {
                if (args.length >= 2) showPlayer(sender, target(args[1]));
                else if (sender instanceof Player p) showPlayer(sender, p);
                else msg(sender, "Usage: /strength get <player>", NamedTextColor.GRAY);
            }
            case "top" -> {
                msg(sender, "--- Strength leaderboard ---", NamedTextColor.GOLD);
                int rank = 1;
                for (Map.Entry<UUID, Integer> en : sm.top(10)) {
                    String name = Bukkit.getOfflinePlayer(en.getKey()).getName();
                    msg(sender, "#" + rank++ + " " + (name == null ? en.getKey().toString() : name) + ": " + en.getValue(),
                            NamedTextColor.YELLOW);
                }
            }
            case "class" -> {
                if (!(sender instanceof Player p)) { msg(sender, "Players only.", NamedTextColor.RED); return true; }
                if (args.length < 2) {
                    msg(sender, "Classes: " + String.join(", ", Arrays.stream(WeaponClass.values()).map(w -> w.name().toLowerCase()).toList()),
                            NamedTextColor.GRAY);
                    return true;
                }
                WeaponClass wc = WeaponClass.parse(args[1]);
                if (wc == null) { msg(sender, "Unknown class.", NamedTextColor.RED); return true; }
                boolean has = sm.weaponClass(p.getUniqueId()) != null;
                if (has && !plugin.getConfig().getBoolean("classes.allow-change", true) && !p.hasPermission(ADMIN)) {
                    msg(sender, "Class changes are disabled.", NamedTextColor.RED);
                    return true;
                }
                sm.setWeaponClass(p.getUniqueId(), wc);
                msg(sender, "Class set to " + wc.display + ". Passive: " + wc.passive + ", Ultimate: " + wc.ultimate + ".",
                        NamedTextColor.GREEN);
            }
            case "withdraw" -> {
                if (!(sender instanceof Player p)) { msg(sender, "Players only.", NamedTextColor.RED); return true; }
                if (!plugin.getConfig().getBoolean("withdraw.enabled", true)) {
                    msg(sender, "Withdrawing is disabled.", NamedTextColor.RED);
                    return true;
                }
                int amount = 1;
                if (args.length >= 2) {
                    try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException ex) { amount = -1; }
                }
                if (amount < 1) { msg(sender, "Usage: /strength withdraw [amount]", NamedTextColor.RED); return true; }
                int floor = plugin.getConfig().getInt("withdraw.floor", 0);
                int cur = sm.get(p.getUniqueId());
                if (cur - amount < floor) {
                    msg(sender, "You cannot withdraw below " + floor + " Strength (you have " + cur + ").", NamedTextColor.RED);
                    return true;
                }
                sm.add(p.getUniqueId(), -amount);
                var leftover = p.getInventory().addItem(plugin.items().token(amount));
                leftover.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
                msg(sender, "Withdrew " + amount + " Strength into token(s). Now " + sm.get(p.getUniqueId()) + ".", NamedTextColor.GREEN);
            }
            case "revive" -> {
                if (args.length < 2) { msg(sender, "Usage: /strength revive <player>", NamedTextColor.RED); return true; }
                OfflinePlayer t = target(args[1]);
                if (!sm.isDeathBanned(t.getUniqueId())) {
                    msg(sender, args[1] + " is not death-banned.", NamedTextColor.RED);
                    return true;
                }
                if (!sender.hasPermission(ADMIN)) {
                    if (!(sender instanceof Player p)) { msg(sender, "No permission.", NamedTextColor.RED); return true; }
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (!plugin.items().isReviveHeart(hand)) {
                        msg(sender, "Hold a Revive Heart in your main hand.", NamedTextColor.RED);
                        return true;
                    }
                    hand.setAmount(hand.getAmount() - 1);
                }
                sm.revive(t.getUniqueId(), plugin.getConfig().getInt("death.revive-strength", 0));
                msg(sender, args[1] + " has been revived.", NamedTextColor.GREEN);
            }
            case "set", "add" -> {
                if (!sender.hasPermission(ADMIN)) { msg(sender, "No permission.", NamedTextColor.RED); return true; }
                if (args.length < 3) { msg(sender, "Usage: /strength " + args[0] + " <player> <number>", NamedTextColor.RED); return true; }
                int n;
                try { n = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { msg(sender, "Not a number.", NamedTextColor.RED); return true; }
                OfflinePlayer t = target(args[1]);
                int result = args[0].equalsIgnoreCase("set") ? sm.set(t.getUniqueId(), n) : sm.add(t.getUniqueId(), n);
                msg(sender, args[1] + " now has " + result + " Strength.", NamedTextColor.GREEN);
            }
            case "give" -> {
                if (!sender.hasPermission(ADMIN)) { msg(sender, "No permission.", NamedTextColor.RED); return true; }
                if (args.length < 3) { msg(sender, "Usage: /strength give <player> <token|revive> [amount]", NamedTextColor.RED); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { msg(sender, "Player not online.", NamedTextColor.RED); return true; }
                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) { }
                }
                ItemStack item = args[2].equalsIgnoreCase("revive") ? plugin.items().reviveHeart(amount) : plugin.items().token(amount);
                t.getInventory().addItem(item).values().forEach(i -> t.getWorld().dropItemNaturally(t.getLocation(), i));
                msg(sender, "Gave " + amount + "x " + args[2] + " to " + t.getName() + ".", NamedTextColor.GREEN);
            }
            case "reload" -> {
                if (!sender.hasPermission(ADMIN)) { msg(sender, "No permission.", NamedTextColor.RED); return true; }
                plugin.reloadConfig();
                msg(sender, "StrengthSMP config reloaded.", NamedTextColor.GREEN);
            }
            default -> msg(sender, "Unknown subcommand.", NamedTextColor.RED);
        }
        return true;
    }

    private void showPlayer(CommandSender to, OfflinePlayer who) {
        StrengthManager sm = plugin.manager();
        WeaponClass wc = sm.weaponClass(who.getUniqueId());
        String name = who.getName() == null ? "Player" : who.getName();
        msg(to, name + ": Strength " + sm.get(who.getUniqueId()) + " (" + sm.min() + " to " + sm.max() + ")"
                + (wc != null ? ", class " + wc.display : ""), NamedTextColor.GOLD);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("get", "top", "class", "withdraw", "revive"));
            if (sender.hasPermission(ADMIN)) out.addAll(List.of("set", "add", "give", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "class" -> Arrays.stream(WeaponClass.values()).forEach(w -> out.add(w.name().toLowerCase()));
                case "get", "set", "add", "give", "revive" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("token", "revive"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
