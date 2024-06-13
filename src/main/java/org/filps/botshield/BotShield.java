package org.filps.botshield;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.filps.botshield.BotShieldCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;

public final class BotShield extends JavaPlugin {

    private String apikey;
    private String serverId;

    private final String expectedVersion = "1.0";
    private String actualVersion;

    @Override
    public void onEnable() {
        getLogger().info("---------------------------------------");
        getLogger().info("BotShield enabled!");
        getLogger().info("---------------------------------------");

        if (!checkVersion()) {
            getLogger().severe("---------------------------------------");
            getLogger().severe("Plugin version mismatch or API error. Shutting down server.");
            getLogger().severe("---------------------------------------");
            getServer().shutdown();
            return;
        }

        if (!loadConfig()) {
            getLogger().severe("---------------------------------------");
            getLogger().severe("Invalid API key in config.yml. Shutting down server.");
            getLogger().severe("---------------------------------------");
            getServer().shutdown();
            return;
        }

        if (!validateApiKey()) {
            getLogger().severe("---------------------------------------");
            getLogger().severe("Invalid API key. Shutting down server.");
            getLogger().severe("---------------------------------------");
            getServer().shutdown();
            return;
        }

        if (!loadSettings()) {
            getLogger().warning("---------------------------------------");
            getLogger().warning("Failed to load settings. Plugin will continue, but with default settings.");
            getLogger().warning("---------------------------------------");
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        this.getCommand("botshield").setExecutor(new BotShieldCommand(this));
    }

    @Override
    public void onDisable() {
        getLogger().info("BotShield disabled!");

        // Delete temporary settings file
        File settingsFile = new File(getDataFolder(), "DO_NOT_MODIFY.txt");
        if (settingsFile.exists()) {
            settingsFile.delete();
        }
    }

    public boolean checkVersion() {
        try {
            String apiUrl = "https://botshield.filps.software/api/v1/version/jar";
            URL url = new URL(apiUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String jsonResponse = response.toString();
            String versionString = jsonResponse.replace("{\"version\":\"", "").replace("\"}", "").trim();
            actualVersion = versionString;

            // Porównanie wersji
            if (compareVersions(expectedVersion, actualVersion) >= 0) {
                return true;
            } else {
                getLogger().severe("Expected version " + expectedVersion + ", but found " + actualVersion);
                return false;
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error checking plugin version: " + e.getMessage());
            return false;
        }
    }

    public static int compareVersions(String expectedVersion, String actualVersion) {
        String[] expectedParts = expectedVersion.split("\\.");
        String[] actualParts = actualVersion.split("\\.");

        int length = Math.max(expectedParts.length, actualParts.length);

        for (int i = 0; i < length; i++) {
            int expectedPart = i < expectedParts.length ? Integer.parseInt(expectedParts[i]) : 0;
            int actualPart = i < actualParts.length ? Integer.parseInt(actualParts[i]) : 0;

            if (actualPart < expectedPart) {
                return 1;
            } else if (actualPart > expectedPart) {
                return -1;
            }
        }
        return 0;
    }

    public boolean loadConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                getLogger().warning("Config file not found. Creating default config.");
                saveDefaultConfig();
            }

            apikey = getConfig().getString("apikey");

            return apikey != null;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error loading config: " + e.getMessage());
            return false;
        }
    }

    public boolean validateApiKey() {
        try {
            String apiUrl = "https://botshield.filps.software/api/v1/" + apikey + "/test/";
            URL url = new URL(apiUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            return con.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error validating API key: " + e.getMessage());
            return false;
        }
    }

    public boolean loadSettings() {
        try {
            String apiUrl = "https://botshield.filps.software/api/v1/" + apikey + "/settings/get";
            URL url = new URL(apiUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String jsonResponse = response.toString();

            // Extract serverId from the response
            if (jsonResponse.contains("\"serverid\":")) {
                int start = jsonResponse.indexOf("\"serverid\":") + 11;
                int end = jsonResponse.indexOf(",", start);
                if (end == -1) { // In case "serverid" is the last element and not followed by a comma
                    end = jsonResponse.indexOf("}", start);
                }
                serverId = jsonResponse.substring(start, end).replace("\"", "").trim();
            } else {
                getLogger().severe("Server ID not found in settings");
                return false;
            }

            File settingsFile = new File(getDataFolder(), "DO_NOT_MODIFY.txt");
            FileWriter writer = new FileWriter(settingsFile);
            writer.write(jsonResponse);
            writer.close();

            return true;
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Error loading settings: " + e.getMessage());
            return false;
        }
    }

    public String[] getSettings() {
        try {
            File settingsFile = new File(getDataFolder(), "DO_NOT_MODIFY.txt");
            BufferedReader reader = new BufferedReader(new FileReader(settingsFile));
            StringBuilder jsonContent = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            return jsonContent.toString().split("\n");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error reading settings: " + e.getMessage());
            return null;
        }
    }

    public void checkPlayerIP(Player player) {
        try {
            String ip = player.getAddress().getHostString();
            String[] settings = getSettings();
            boolean captchaVerifyOn = false;
            boolean vpnDetectorOn = false;

            for (String setting : settings) {
                if (setting.contains("\"captchaverify\":\"on\"")) {
                    captchaVerifyOn = true;
                }
                if (setting.contains("\"vpndetector\":\"on\"")) {
                    vpnDetectorOn = true;
                }
            }

            if (captchaVerifyOn) {
                String captchaUrl = "https://botshield.filps.software/api/v1/" + apikey + "/checkverify/" + ip;
                URL url = new URL(captchaUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String jsonResponse = response.toString();
                boolean isVerified = jsonResponse.contains("\"verified\":\"yes\"");

                if (!isVerified) {
                    String serverId = getServerIdFromSettings();
                    player.kickPlayer("Please go to https://botshield.filps.software/server/" + serverId + "/verify and complete the verification before trying to join again.");
                    return;
                }
            }

            if (vpnDetectorOn) {
                String apiUrl = "https://botshield.filps.software/api/v1/" + apikey + "/checkip/" + ip;
                URL url = new URL(apiUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String jsonResponse = response.toString();
                boolean isVpn = jsonResponse.contains("\"isVpn\":true");

                if (isVpn) {
                    player.kickPlayer("&cAntibot detected irregularities. You have been kicked from the server. " +
                            "Protected by BotShield. " +
                            "https://botshield.filps.software/");
                }
            }

        } catch (IOException e) {
            getLogger().log(Level.WARNING, "An error occurred while checking player IP: " + e.getMessage());
        }
    }

    private String getServerIdFromSettings() {
        try {
            File settingsFile = new File(getDataFolder(), "DO_NOT_MODIFY.txt");
            BufferedReader reader = new BufferedReader(new FileReader(settingsFile));
            StringBuilder jsonContent = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            String jsonResponse = jsonContent.toString();
            if (jsonResponse.contains("\"serverid\":")) {
                int start = jsonResponse.indexOf("\"serverid\":") + 11;
                int end = jsonResponse.indexOf(",", start);
                if (end == -1) { // In case "serverid" is the last element and not followed by a comma
                    end = jsonResponse.indexOf("}", start);
                }
                return jsonResponse.substring(start, end).replace("\"", "").trim();
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error reading server ID from settings: " + e.getMessage());
        }
        return null;
    }
}

class PlayerListener implements Listener {

    private final BotShield plugin;

    public PlayerListener(BotShield plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.checkPlayerIP(player);
    }
}
