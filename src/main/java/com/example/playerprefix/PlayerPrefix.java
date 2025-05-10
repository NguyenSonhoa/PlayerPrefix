package com.example.playerprefix;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerPrefix extends JavaPlugin {

    private PlayerPrefixExpansion expansion;

    @Override
    public void onEnable() {
        // Kiểm tra PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("Could not find PlaceholderAPI! This plugin is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Kiểm tra LuckPerms
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("Could not find LuckPerms! This plugin is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Đăng ký expansion
        expansion = new PlayerPrefixExpansion(this);
        if (expansion.register()) {
            getLogger().info("PlayerPrefix placeholders have been registered!");
        } else {
            getLogger().warning("Failed to register PlayerPrefix placeholders!");
        }
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.cleanup();
        }
    }

    private static class PlayerPrefixExpansion extends PlaceholderExpansion {
        private final JavaPlugin plugin;
        private final LuckPerms luckPerms;
        private EventSubscription<UserDataRecalculateEvent> subscription;
        private final Map<UUID, Map<String, String>> cache = new HashMap<>();

        public PlayerPrefixExpansion(JavaPlugin plugin) {
            this.plugin = plugin;
            this.luckPerms = LuckPermsProvider.get();

            EventBus eventBus = luckPerms.getEventBus();
            subscription = eventBus.subscribe(UserDataRecalculateEvent.class, event -> {
                UUID uuid = event.getUser().getUniqueId();
                cache.remove(uuid);
            });
        }

        @Override
        public String getIdentifier() {
            return "playerprefix";
        }

        @Override
        public String getAuthor() {
            return plugin.getDescription().getAuthors().get(0);
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public boolean canRegister() {
            return true;
        }
        public void cleanup() {
            if (subscription != null) {
                subscription.close();
                subscription = null;
            }
            // Xóa cache
            cache.clear();
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (params == null || params.isEmpty()) {
                return null;
            }
            String[] parts = params.split("_", 2);
            if (parts.length < 2) {
                return null;
            }

            String type = parts[0].toLowerCase();
            String targetName = parts[1];

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            if (targetPlayer == null) {
                return "Player not found";
            }

            UUID uuid = targetPlayer.getUniqueId();

            if (cache.containsKey(uuid) && cache.get(uuid).containsKey(type)) {
                return cache.get(uuid).get(type);
            }
            CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(uuid);

            try {
                User user = userFuture.join();
                if (user == null) {
                    return "No data";
                }

                String result;
                switch (type) {
                    case "prefix":
                        result = user.getCachedData().getMetaData().getPrefix();
                        result = result != null ? result : "";
                        break;
                    case "rank":
                        result = getHighestWeightGroup(user);
                        break;
                    default:
                        return null;
                }
                cache.computeIfAbsent(uuid, k -> new HashMap<>()).put(type, result);
                return result;
            } catch (Exception e) {
                plugin.getLogger().warning("Error getting data for player " + targetName + ": " + e.getMessage());
                return "Error";
            }
        }

        private String getHighestWeightGroup(User user) {
            int highestWeight = -1;
            String highestGroup = "default";
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                String groupName = node.getGroupName();
                Group group = luckPerms.getGroupManager().getGroup(groupName);

                if (group != null) {
                    int weight = group.getWeight().orElse(0);
                    if (weight > highestWeight) {
                        highestWeight = weight;
                        highestGroup = groupName;
                    }
                }
            }

            return highestGroup;
        }
    }
}