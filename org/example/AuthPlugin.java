package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AuthPlugin extends JavaPlugin implements Listener {
   private Set<UUID> authenticatedPlayers = new HashSet<>();
   private Set<UUID> pendingAuth = new HashSet<>();
   private Set<String> validRooms = new HashSet<>();
   private Map<UUID, Integer> authTaskMap = new HashMap<>();

   private File getDataFile(String fileName) {
      try {
         File jar = new File(AuthPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
         File dir = jar.getParentFile();
         return new File(dir, fileName);
      } catch (URISyntaxException e) {
         throw new RuntimeException("Failed to determine JAR file location.", e);
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      if (pendingAuth.remove(uuid)) {
         Integer task = authTaskMap.remove(uuid);
         if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
         }
      }

   }

   @Override
   public void onEnable() {
      if (!getDataFolder().exists()) {
         getDataFolder().mkdirs();
      }
      this.loadValidRooms();
      this.getServer().getPluginManager().registerEvents(this, this);
   }

   private void loadValidRooms() {
      validRooms.clear();
      File roomsFile = getDataFile("filtered_first_names_rooms.csv");
      if (!roomsFile.exists()) {
         getLogger().severe("Rooms file not found: " + roomsFile.getAbsolutePath());
         return;
      }

      try (BufferedReader reader = new BufferedReader(new FileReader(roomsFile))) {
         String line;
         while ((line = reader.readLine()) != null) {
            String room = line.trim().toLowerCase().replace(" ", "");
            if (!room.isEmpty()) {
               validRooms.add(room);
            }
         }
      } catch (IOException e) {
         getLogger().severe("Failed to load room list.");
         e.printStackTrace();
      }
   }

   private void sendAuthMessage(Player player) {
      player.sendMessage(ChatColor.YELLOW + "Podaj pok\u00F3j oraz imie, np. 1010B2 Kamil");
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      if (pendingAuth.contains(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (pendingAuth.contains(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      final Player player = event.getPlayer();
      if (!authenticatedPlayers.contains(player.getUniqueId())) {
         pendingAuth.add(player.getUniqueId());
         sendAuthMessage(player);
         int taskId = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            public void run() {
               if (pendingAuth.contains(player.getUniqueId())) {
                  sendAuthMessage(player);
               }
            }
         }, 20L, 20L).getTaskId();
         authTaskMap.put(player.getUniqueId(), taskId);
      }

   }

   @EventHandler
   public void onPlayerChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      if (pendingAuth.contains(uuid)) {
         event.setCancelled(true);
         String message = event.getMessage().trim();
         if (message.equalsIgnoreCase("reset")) {
            sendAuthMessage(player);
            return;
         }

         String[] parts = message.split("\\s+", 2);
         String roomInput = parts[0].toLowerCase().replace(" ", "");
         String nameInput = parts.length > 1 ? parts[1] : "";

         if (validRooms.contains(roomInput)) {
            Bukkit.getScheduler().runTask(this, () -> {
               authenticatedPlayers.add(uuid);
               pendingAuth.remove(uuid);
               player.sendMessage(ChatColor.GREEN + "Uwierzytelnienie zako\u0144czone sukcesem! Mi\u0142ej gry.");
               savePlayerData(player, roomInput, nameInput);
               Integer task = authTaskMap.remove(uuid);
               if (task != null) {
                  Bukkit.getScheduler().cancelTask(task);
               }
            });
         } else {
            Bukkit.getScheduler().runTask(this, () -> {
               player.sendMessage(ChatColor.RED + "Niepoprawny pok\u00F3j. Jeśli chcesz spr\u00F3bowa\u0107 ponownie, wpisz 'reset'. Jeśli masz problem, napisz na rm.ds1@pg.edu.pl");
            });
         }
      }

   }

   private void savePlayerData(Player player, String room, String name) {
      File dataFile = getDataFile("authenticated_players.txt");
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile, true))) {
         String line = player.getName() + "," + player.getAddress().getAddress().getHostAddress() + "," + room + "," + name;
         writer.write(line);
         writer.newLine();
      } catch (IOException e) {
         getLogger().severe("Failed to save player data.");
         e.printStackTrace();
      }

   }
}
