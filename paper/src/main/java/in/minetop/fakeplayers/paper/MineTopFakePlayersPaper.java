package in.minetop.fakeplayers.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class MineTopFakePlayersPaper extends JavaPlugin {
    private NamespacedKey npcKey;
    private NamespacedKey npcNameKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        npcKey = new NamespacedKey(this, "fake_player");
        npcNameKey = new NamespacedKey(this, "fake_player_name");
        Objects.requireNonNull(getCommand("fakeplayer")).setTabCompleter((sender, command, alias, args) -> tabComplete(args));
        getLogger().info("MineTopFakePlayers Paper enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minetop.fakeplayers.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "tp" -> teleportToSender(sender, args);
            case "move" -> move(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            default -> { help(sender); yield true; }
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Run this command in-game.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /fp create <name>", NamedTextColor.YELLOW));
            return true;
        }
        String name = sanitizeName(args[1]);
        if (name == null) {
            sender.sendMessage(Component.text("Name must be 1-16 characters: letters, numbers or _", NamedTextColor.RED));
            return true;
        }
        if (findNpc(name) != null) {
            sender.sendMessage(Component.text("That fake player already exists.", NamedTextColor.RED));
            return true;
        }

        ArmorStand npc = spawnNpc(name, player.getLocation());
        saveNpc(npc, name);
        sender.sendMessage(Component.text("Created fake player " + name + ".", NamedTextColor.GREEN));
        return true;
    }

    private ArmorStand spawnNpc(String name, Location location) {
        return location.getWorld().spawn(location, ArmorStand.class, npc -> {
            npc.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
            npc.getPersistentDataContainer().set(npcNameKey, PersistentDataType.STRING, name);
            npc.customName(Component.text(name, NamedTextColor.WHITE));
            npc.setCustomNameVisible(getConfig().getBoolean("npc.name-visible", true));
            npc.setArms(true);
            npc.setBasePlate(false);
            npc.setSmall(false);
            npc.setVisible(true);
            npc.setInvulnerable(getConfig().getBoolean("npc.invulnerable", true));
            npc.setGravity(getConfig().getBoolean("npc.gravity", false));
            npc.setCollidable(getConfig().getBoolean("npc.collidable", false));
            npc.setGlowing(getConfig().getBoolean("npc.glowing", false));
            equipNpc(npc, name);
        });
    }

    private void equipNpc(ArmorStand npc, String name) {
        EntityEquipment equipment = npc.getEquipment();
        if (equipment == null) return;

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
            head.setItemMeta(meta);
        }
        equipment.setHelmet(head);
        equipment.setChestplate(materialItem("npc.equipment.chestplate"));
        equipment.setLeggings(materialItem("npc.equipment.leggings"));
        equipment.setBoots(materialItem("npc.equipment.boots"));
        equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
    }

    private ItemStack materialItem(String path) {
        Material material = Material.matchMaterial(getConfig().getString(path, "AIR"));
        return new ItemStack(material == null ? Material.AIR : material);
    }

    private boolean teleportToSender(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Run this command in-game.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /fp tp <name>", NamedTextColor.YELLOW));
            return true;
        }
        ArmorStand npc = findNpc(args[1]);
        if (npc == null) {
            sender.sendMessage(Component.text("Fake player not found.", NamedTextColor.RED));
            return true;
        }
        npc.teleport(player.getLocation());
        saveNpc(npc, npcName(npc));
        sender.sendMessage(Component.text("Teleported " + npcName(npc) + " to you.", NamedTextColor.GREEN));
        return true;
    }

    private boolean move(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(Component.text("Usage: /fp move <name> <world> <x> <y> <z> [yaw] [pitch]", NamedTextColor.YELLOW));
            return true;
        }
        ArmorStand npc = findNpc(args[1]);
        if (npc == null) {
            sender.sendMessage(Component.text("Fake player not found.", NamedTextColor.RED));
            return true;
        }
        World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            sender.sendMessage(Component.text("World not found.", NamedTextColor.RED));
            return true;
        }
        try {
            double x = Double.parseDouble(args[3]);
            double y = Double.parseDouble(args[4]);
            double z = Double.parseDouble(args[5]);
            float yaw = args.length > 6 ? Float.parseFloat(args[6]) : npc.getLocation().getYaw();
            float pitch = args.length > 7 ? Float.parseFloat(args[7]) : npc.getLocation().getPitch();
            npc.teleport(new Location(world, x, y, z, yaw, pitch));
            saveNpc(npc, npcName(npc));
            sender.sendMessage(Component.text("Moved " + npcName(npc) + ".", NamedTextColor.GREEN));
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Coordinates/yaw/pitch must be numbers.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /fp remove <name>", NamedTextColor.YELLOW));
            return true;
        }
        ArmorStand npc = findNpc(args[1]);
        if (npc == null) {
            sender.sendMessage(Component.text("Fake player not found.", NamedTextColor.RED));
            return true;
        }
        String name = npcName(npc);
        npc.remove();
        getConfig().set("players." + name, null);
        saveConfig();
        sender.sendMessage(Component.text("Removed " + name + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean list(CommandSender sender) {
        List<String> names = allNpcs().stream().map(this::npcName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        sender.sendMessage(Component.text("Fake players (" + names.size() + "): ", NamedTextColor.AQUA)
                .append(Component.text(names.isEmpty() ? "none" : String.join(", ", names), NamedTextColor.WHITE)));
        return true;
    }

    private boolean reload(CommandSender sender) {
        reloadConfig();
        sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN));
        return true;
    }

    private void saveNpc(ArmorStand npc, String name) {
        Location loc = npc.getLocation();
        String base = "players." + name;
        getConfig().set(base + ".uuid", npc.getUniqueId().toString());
        getConfig().set(base + ".world", loc.getWorld().getName());
        getConfig().set(base + ".x", loc.getX());
        getConfig().set(base + ".y", loc.getY());
        getConfig().set(base + ".z", loc.getZ());
        getConfig().set(base + ".yaw", loc.getYaw());
        getConfig().set(base + ".pitch", loc.getPitch());
        saveConfig();
    }

    private ArmorStand findNpc(String input) {
        for (ArmorStand npc : allNpcs()) {
            if (npcName(npc).equalsIgnoreCase(input)) return npc;
        }
        return null;
    }

    private List<ArmorStand> allNpcs() {
        List<ArmorStand> result = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)) result.add(stand);
            }
        }
        return result;
    }

    private String npcName(ArmorStand npc) {
        return npc.getPersistentDataContainer().getOrDefault(npcNameKey, PersistentDataType.STRING, "Unknown");
    }

    private String sanitizeName(String raw) {
        return raw.matches("[A-Za-z0-9_]{1,16}") ? raw : null;
    }

    private List<String> tabComplete(String[] args) {
        if (args.length == 1) return filter(List.of("create", "tp", "move", "remove", "list", "reload"), args[0]);
        if (args.length == 2 && List.of("tp", "move", "remove", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(allNpcs().stream().map(this::npcName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("move")) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("MineTop Fake Players", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/fp create <name> - create at your position", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/fp tp <name> - teleport NPC to you", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/fp move <name> <world> <x> <y> <z> [yaw] [pitch]", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/fp remove <name> | /fp list | /fp reload", NamedTextColor.WHITE));
    }
}
