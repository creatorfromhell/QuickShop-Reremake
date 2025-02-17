package org.maxgamer.quickshop.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.maxgamer.quickshop.QuickShop;

public class Updater {
    /**
     * Check new update
     *
     * @return True=Have a new update; False=No new update or check update failed.
     */
    public static UpdateInfomation checkUpdate() {
        if (!QuickShop.instance.getConfig().getBoolean("updater")) {
            return new UpdateInfomation(null, false);
        }
        try {
            HttpsURLConnection connection = (HttpsURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=62575")
                    .openConnection();
            int timed_out = 300000;
            connection.setConnectTimeout(timed_out);
            connection.setReadTimeout(timed_out);
            String localPluginVersion = QuickShop.instance.getDescription().getVersion();
            String spigotPluginVersion = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
            if (spigotPluginVersion != null && !spigotPluginVersion.equals(localPluginVersion)) {
//                QuickShgetLogger().info("New QuickShop release now updated on SpigotMC.org! ");
//                getLogger().info("Update plugin in there:https://www.spigotmc.org/resources/59134/");
                connection.disconnect();
                return new UpdateInfomation(spigotPluginVersion, spigotPluginVersion.toLowerCase().contains("beta"));
            }
            connection.disconnect();
            return new UpdateInfomation(spigotPluginVersion, false);
        } catch (IOException e) {
            Bukkit.getConsoleSender()
                    .sendMessage(ChatColor.RED + "[QuickShop] Failed to check for an update on SpigotMC.org! It might be an internet issue or the SpigotMC host is down. If you want disable the update checker, you can disable in config.yml, but we still high-recommend check for updates on SpigotMC.org often.");
            return new UpdateInfomation(null, false);
        }
    }
}
