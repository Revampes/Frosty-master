package com.revampes.Fault.utility;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.Revampes;

import java.util.Collection;

public class LocationUtils {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static String cachedTabArea = "Unknown";
    private static boolean registered = false;

    public static void init() {
        if (!registered) {
            Revampes.EVENT_BUS.subscribe(LocationUtils.class);
            registered = true;
        }
    }

    @EventHandler
    private static void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof PlayerListHeaderS2CPacket packet) {
            String header = packet.header().getString().replaceAll("(?i)\\\\u00A7[0-9A-FK-OR]", "");
            String footer = packet.footer().getString().replaceAll("(?i)\\\\u00A7[0-9A-FK-OR]", "");

            if (header.contains("Area: ")) {
                try { cachedTabArea = header.split("Area: ")[1].split("\n")[0].trim(); } catch(Exception ignored) {}
            }
            if (footer.contains("Area: ")) {
                try { cachedTabArea = footer.split("Area: ")[1].split("\n")[0].trim(); } catch(Exception ignored) {}
            }
        }
    }

    /**
     * Gets the current area the player is in from the Tab List.
     * Looks for a specific string pattern: "Area: <area_name>"
     */
    public static String getCurrentArea() {
        if (mc.getNetworkHandler() == null) return "Unknown";

        try {
            // Check fake players in tab list FIRST
            Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();
            
            for (PlayerListEntry entry : playerList) {
                String tabName = "";

                if (entry.getDisplayName() != null) {
                    tabName = entry.getDisplayName().getString();
                } else if (entry.getProfile() != null && entry.getProfile().name() != null) {
                    tabName = entry.getProfile().name();
                }

                if (tabName != null) {
                    tabName = tabName.replaceAll("(?i)\\\\u00A7[0-9A-FK-OR]", "");
                    
                    if (tabName.contains("Area: ")) {
                        try {
                            return tabName.split("Area: ")[1].split("\n")[0].trim();
                        } catch (Exception e) {
                            return "Unknown";
                        }
                    }
                }
            }
        } catch (java.util.ConcurrentModificationException ignored) {
            // Failsafe for thread-safety issues when requested by async thread event calls
        }

        // Fallback to cached header/footer
        if (!cachedTabArea.equals("Unknown")) {
            return cachedTabArea;
        }

        return "Unknown";
    }

    /**
     * Checks if the player is currently in a Dungeon (Catacombs).
     */
    public static boolean isInDungeon() {
        if (mc.getNetworkHandler() == null) return false;

        Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();
        
        for (PlayerListEntry entry : playerList) {
            String tabName = "";

            if (entry.getDisplayName() != null) {
                tabName = entry.getDisplayName().getString();
            } else if (entry.getProfile() != null && entry.getProfile().name() != null) {
                tabName = entry.getProfile().name();
            }

            if (tabName != null) {
                tabName = tabName.replaceAll("(?i)\\\\u00A7[0-9A-FK-OR]", "");
                
                if (tabName.contains("Dungeon: Catacombs") || tabName.contains("Dungeon") || tabName.contains("Catacombs")) {
                    return true;
                }
            }
        }

        if (cachedTabArea != null && (cachedTabArea.contains("Dungeon") || cachedTabArea.contains("Catacombs"))) {
            return true;
        }

        return false;
    }
}
