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
   private Set<UUID> authenticatedPlayers = new HashSet();
   private Map<UUID, AuthPlugin.AuthData> pendingAuth = new HashMap();
   private Set<String> validRooms = new HashSet();
   private Map<UUID, Integer> authTaskMap = new HashMap();

   private File getCsvFile(String fileName) {
      try {
         File jarFile = new File(AuthPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
         File jarDir = jarFile.getParentFile();
         return new File(jarDir, fileName);
      } catch (URISyntaxException var4) {
         throw new RuntimeException("Failed to determine JAR file location.", var4);
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      if (this.pendingAuth.containsKey(uuid)) {
         Bukkit.getScheduler().cancelTask((Integer)this.authTaskMap.get(uuid));
         this.authTaskMap.remove(uuid);
         this.pendingAuth.remove(uuid);
      }

   }

   public void onEnable() {
      this.loadResidentsData();
      this.getServer().getPluginManager().registerEvents(this, this);
   }

   private void loadResidentsData() {
      File csvFile = this.getCsvFile("filtered_first_names_rooms.csv");
      if (!csvFile.exists()) {
         this.getLogger().severe("CSV file not found: " + csvFile.getAbsolutePath());
      } else {
         try {
            BufferedReader reader = new BufferedReader(new FileReader(csvFile));

            String line;
            try {
               while((line = reader.readLine()) != null) {
                  String room = line.trim().toLowerCase().replace(" ", "");
                  if (!room.isEmpty()) {
                     this.validRooms.add(room);
                  }
               }
            } catch (Throwable var8) {
               try {
                  reader.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            reader.close();
         } catch (IOException var9) {
            this.getLogger().severe("Failed to load residents data.");
            var9.printStackTrace();
         }

      }
   }

   @EventHandler
   public void onPlayerMove(PlayerMoveEvent event) {
      if (this.pendingAuth.containsKey(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (this.pendingAuth.containsKey(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }

   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      final Player player = event.getPlayer();
      if (!this.authenticatedPlayers.contains(player.getUniqueId())) {
         this.pendingAuth.put(player.getUniqueId(), new AuthPlugin.AuthData());
         player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Witaj! Kliknij 't' i podaj pok\u00F3j oraz opcjonalnie imie, np. 1010B2 Kamil");
         int taskId = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            public void run() {
               if (AuthPlugin.this.pendingAuth.containsKey(player.getUniqueId())) {
                  player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Wpisz pok\u00F3j oraz opcjonalnie imie, np. 1010B2 Kamil");
               }

            }
         }, 20L, 40L).getTaskId();
         this.authTaskMap.put(player.getUniqueId(), taskId);
      }

   }

   @EventHandler
   public void onPlayerChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      UUID uuid = player.getUniqueId();
      if (this.pendingAuth.containsKey(uuid)) {
         AuthPlugin.AuthData authData = (AuthPlugin.AuthData)this.pendingAuth.get(uuid);
         String message = event.getMessage().trim();
         event.setCancelled(true);
         if (message.equalsIgnoreCase("reset")) {
            authData.studentName = null;
            authData.roomNumber = null;
            player.sendMessage(String.valueOf(ChatColor.YELLOW) + "Resetowano proces uwierzytelniania. Wpisz pok\u00F3j oraz opcjonalnie imie, np. 1010B2 Kamil");
            return;
         }

         if (authData.roomNumber == null) {
            String[] parts = message.split("\\s+", 2);
            String roomInput = parts[0].toLowerCase().replace(" ", "");
            authData.roomNumber = roomInput;
            if (parts.length > 1) {
               authData.studentName = parts[1];
            }
            if (AuthPlugin.this.validRooms.contains(roomInput)) {
               Bukkit.getScheduler().runTask(this, () -> {
                  this.authenticatedPlayers.add(uuid);
                  this.pendingAuth.remove(uuid);
                  player.sendMessage(String.valueOf(ChatColor.GREEN) + "Uwierzytelnienie zako\u0144czone sukcesem! Mi\u0142ej gry.");
                  this.savePlayerData(player, authData);
                  Bukkit.getScheduler().cancelTask((Integer)this.authTaskMap.get(uuid));
                  this.authTaskMap.remove(uuid);
               });
            } else {
               Bukkit.getScheduler().runTask(this, () -> {
                  player.sendMessage(String.valueOf(ChatColor.RED) + "Niepoprawne dane. Jeśli chcesz spr\u00F3bowa\u0107 ponownie, wpisz 'reset', lub je\u015Bli wpisujesz poprawne dane, lub nie mieszkasz w DS1 a chcesz gra\u0107, napisz swoje dane itp na rm.ds1@pg.edu.pl");
               });
            }
         }
      }

   }

   private void savePlayerData(Player player, AuthPlugin.AuthData authData) {
      try {
         BufferedWriter writer = new BufferedWriter(new FileWriter("authenticated_players.txt", true));

         try {
            String var10000 = player.getName();
            String line = var10000 + "," + player.getAddress().getAddress().getHostAddress() + "," + (authData.studentName != null ? authData.studentName : "") + "," + authData.roomNumber;
            writer.write(line);
            writer.newLine();
         } catch (Throwable var7) {
            try {
               writer.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }

            throw var7;
         }

         writer.close();
      } catch (IOException var8) {
         this.getLogger().severe(System.getProperty("user.dir"));
         this.getLogger().severe("Failed to save player data.");
         var8.printStackTrace();
      }

   }

   private static class AuthData {
      String studentName;
      String roomNumber;
   }
}
