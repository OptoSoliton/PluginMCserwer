package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import com.ip2location.IP2Location;
import com.ip2location.IPResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
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
   private Set<UUID> firstAttemptDone = new HashSet<>();
   private IP2Location ip2Location = new IP2Location();

   private File getDataFile(String fileName) {
      return new File(getDataFolder(), fileName);
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
      firstAttemptDone.remove(uuid);

   }

   @Override
   public void onEnable() {
      if (!getDataFolder().exists()) {
         getDataFolder().mkdirs();
      }
      this.loadValidRooms();
      try {
         File dbFile = getDataFile("IP2LOCATION-LITE-DB11.BIN");
         if (dbFile.exists()) {
            ip2Location.Open(dbFile.getAbsolutePath());
         } else {
            getLogger().warning("IP2Location DB not found: " + dbFile.getAbsolutePath());
         }
      } catch (Exception e) {
         getLogger().warning("Failed to load IP2Location DB.");
      }
      this.getServer().getPluginManager().registerEvents(this, this);
   }

   @Override
   public void onDisable() {
      try {
         ip2Location.Close();
      } catch (Exception e) {
         // ignore
      }
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

   private boolean recordExists(Player player, String room, String name) {
      File dataFile = getDataFile("authenticated_players.txt");
      if (!dataFile.exists()) {
         return false;
      }
      String ip = player.getAddress().getAddress().getHostAddress();
      String nick = player.getName();
      try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
         String line;
         while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length >= 4) {
               String nickStored = parts[0].trim();
               String ipStored = parts[1].trim();
               String roomStored = parts[2].trim();
               String nameStored = parts[3].trim();
               if (nickStored.equalsIgnoreCase(nick) && ipStored.equals(ip)
                     && roomStored.equalsIgnoreCase(room)
                     && nameStored.equalsIgnoreCase(name)) {
                  return true;
               }
            }
         }
      } catch (IOException e) {
         getLogger().severe("Failed to read player data.");
         e.printStackTrace();
      }
      return false;
   }

   private void sendAuthMessage(Player player) {
      String msg = ChatColor.GOLD + "Podaj pok\u00F3j " + ChatColor.YELLOW + "oraz "
            + ChatColor.AQUA + "imi\u0119" + ChatColor.GOLD + ", np. "
            + ChatColor.GREEN + "1010B2 Kamil";
      player.sendMessage(msg);
   }

   private List<String> lookupIpInfo(String ip) {
      List<String> lines = new ArrayList<>();
      try {
         IPResult r = ip2Location.IPQuery(ip);
         if ("OK".equalsIgnoreCase(r.getStatus())) {
            lines.add(ChatColor.GRAY + "Kraj: " + ChatColor.WHITE + r.getCountryLong());
            lines.add(ChatColor.GRAY + "Region: " + ChatColor.WHITE + r.getRegion());
            lines.add(ChatColor.GRAY + "Miasto: " + ChatColor.WHITE + r.getCity());
            lines.add(ChatColor.GRAY + "ISP: " + ChatColor.WHITE + r.getISP());
            lines.add(ChatColor.GRAY + "Domena: " + ChatColor.WHITE + r.getDomain());
            lines.add(ChatColor.GRAY + "Kod pocztowy: " + ChatColor.WHITE + r.getZipCode());
            lines.add(ChatColor.GRAY + "Strefa: " + ChatColor.WHITE + r.getTimeZone());
            lines.add(ChatColor.GRAY + "Pr\u0119dko\u015B\u0107: " + ChatColor.WHITE + r.getNetSpeed());
         } else {
            lines.add(ChatColor.GRAY + "szczeg\u00F3\u0142owe dane w low level logach");
            getLogger().info("Lookup for " + ip + " status: " + r.getStatus());
         }
      } catch (Exception e) {
         getLogger().warning("IP2Location lookup failed for " + ip);
      }
      return lines;
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
         firstAttemptDone.remove(player.getUniqueId());
         sendAuthMessage(player);
         int taskId = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            public void run() {
               if (pendingAuth.contains(player.getUniqueId())) {
                  sendAuthMessage(player);
               }
            }
         }, 10L, 10L).getTaskId();
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
            firstAttemptDone.remove(uuid);
            sendAuthMessage(player);
            return;
         }

         String[] parts = message.split("\\s+", 2);
         String roomInput = parts[0].toLowerCase().replace(" ", "");
         String nameInput = parts.length > 1 ? parts[1].trim() : "";

        if (!validRooms.contains(roomInput)) {
            Bukkit.getScheduler().runTask(this, () -> {
                player.sendMessage(ChatColor.RED + "Niepoprawny pok\u00F3j. Je\u015bli chcesz spr\u00F3bowa\u0107 ponownie, wpisz 'reset'. Je\u015bli masz problem, napisz na rm.ds1@pg.edu.pl");
            });
            return;
        }

        if (!firstAttemptDone.contains(uuid)) {
            if (recordExists(player, roomInput, nameInput)) {
                Bukkit.getScheduler().runTask(this, () -> completeLogin(player, uuid, roomInput, nameInput));
            } else {
                firstAttemptDone.add(uuid);
                Bukkit.getScheduler().runTask(this, () -> {
                    player.sendMessage(ChatColor.RED + "Hej anio\u0142ku, to mia\u0142o by\u0107 twoje imie... Dawaj jeszcze raz :)");
                });
            }
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> completeLogin(player, uuid, roomInput, nameInput));
      }

   }

   private void completeLogin(Player player, UUID uuid, String room, String name) {
      authenticatedPlayers.add(uuid);
      pendingAuth.remove(uuid);
      firstAttemptDone.remove(uuid);
      String ip = player.getAddress().getAddress().getHostAddress();
      String displayName = name.isEmpty() ? player.getName() : name;
      player.sendMessage(ChatColor.GREEN + "Twoje IP: " + ChatColor.WHITE + ip);

      for (String line : lookupIpInfo(ip)) {
         player.sendMessage(line);
      }

      showPreviousData(player);

      player.sendMessage(ChatColor.AQUA + "Mi\u0142ej gry " + ChatColor.GOLD + displayName + ChatColor.AQUA + " :)");

      savePlayerData(player, room, name);
      Integer task = authTaskMap.remove(uuid);
      if (task != null) {
         Bukkit.getScheduler().cancelTask(task);
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

   private void showPreviousData(Player player) {
      File dataFile = getDataFile("authenticated_players.txt");
      if (!dataFile.exists()) {
         return;
      }
      try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
         String line;
         while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length >= 4 && parts[0].equalsIgnoreCase(player.getName())) {
               player.sendMessage(ChatColor.LIGHT_PURPLE + "Zapis: " + ChatColor.WHITE + line);
            }
         }
      } catch (IOException e) {
         getLogger().severe("Failed to read previous data for player " + player.getName());
      }

   }
}
