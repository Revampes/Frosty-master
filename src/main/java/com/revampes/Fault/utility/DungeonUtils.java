package com.revampes.Fault.utility;


import static com.revampes.Fault.Revampes.mc;
import com.revampes.Fault.modules.impl.dungeon.SecretClick;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonUtils {
    public static final int DUNGEON_ROOM_SIZE = 32;
    public static final int DUNGEON_ROOM_WORLD_START = -200;

    private static final Pattern DUNGEON_TEAMMATE_TAB_PATTERN = Pattern.compile(
        "^\\[(\\d+)]\\s+(?:\\[[^\\]]+\\]\\s+)*(\\w{1,16})\\s+.*?\\((\\w+)(?:\\s+([IVXLCDM]+|\\d+))?\\)\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TAB_COLOR_CODE_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    
    /**
     * Helper check to determine if the player is currently inside a dungeon instance.
     * @return true if LocationUtils detects "Dungeon" or "Catacombs".
     */
    public static boolean isInDungeon() {
        return LocationUtils.isInDungeon();
    }

    public static int toRoomCorner(int coord) {
        return Math.floorDiv(coord - DUNGEON_ROOM_WORLD_START, DUNGEON_ROOM_SIZE) * DUNGEON_ROOM_SIZE + DUNGEON_ROOM_WORLD_START;
    }

    public static int toRoomCorner(double coord) {
        return toRoomCorner((int) Math.floor(coord));
    }

    public static double playerDistanceSqToRoomCenter(int cornerX, int cornerZ) {
        if (mc == null || mc.player == null) {
            return Double.MAX_VALUE;
        }

        double centerX = cornerX + 15.5;
        double centerZ = cornerZ + 15.5;
        return mc.player.squaredDistanceTo(centerX, mc.player.getY(), centerZ);
    }

    public static BlockPos fromRoomCoordinates(int cornerX, int cornerZ, int compX, int compZ, int y, int rotation) {
        int x;
        int z;

        switch (rotation & 3) {
            case 1 -> {
                x = cornerX + (31 - compZ);
                z = cornerZ + compX;
            }
            case 2 -> {
                x = cornerX + (31 - compX);
                z = cornerZ + (31 - compZ);
            }
            case 3 -> {
                x = cornerX + compZ;
                z = cornerZ + (31 - compX);
            }
            default -> {
                x = cornerX + compX;
                z = cornerZ + compZ;
            }
        }

        return new BlockPos(x, y, z);
    }

    public static Vec3d fromRoomCoordinates(int cornerX, int cornerZ, double compX, double compZ, int rotation) {
        double x;
        double z;

        switch (rotation & 3) {
            case 1 -> {
                x = cornerX + (31.0 - compZ);
                z = cornerZ + compX;
            }
            case 2 -> {
                x = cornerX + (31.0 - compX);
                z = cornerZ + (31.0 - compZ);
            }
            case 3 -> {
                x = cornerX + compZ;
                z = cornerZ + (31.0 - compX);
            }
            default -> {
                x = cornerX + compX;
                z = cornerZ + compZ;
            }
        }

        return new Vec3d(x, 0.0, z);
    }

    public static int[] toRoomCoordinates(int cornerX, int cornerZ, int worldX, int worldZ, int rotation) {
        int relX = worldX - cornerX;
        int relZ = worldZ - cornerZ;

        int compX;
        int compZ;

        switch (rotation & 3) {
            case 1 -> {
                compX = relZ;
                compZ = 31 - relX;
            }
            case 2 -> {
                compX = 31 - relX;
                compZ = 31 - relZ;
            }
            case 3 -> {
                compX = 31 - relZ;
                compZ = relX;
            }
            default -> {
                compX = relX;
                compZ = relZ;
            }
        }

        if (compX < 0 || compX > 31 || compZ < 0 || compZ > 31) {
            return null;
        }

        return new int[] {compX, compZ};
    }

    /**
     * Parses dungeon teammate data from the tab list.
     * The returned map key is the lowercase teammate name.
     */
    public static Map<String, DungeonTeammate> getDungeonTeammateLookup() {
        if (mc == null || mc.getNetworkHandler() == null || !isInDungeon()) {
            return Collections.emptyMap();
        }

        Map<String, DungeonTeammate> teammates = new LinkedHashMap<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String tabLine = getTabLine(entry);
            DungeonTeammate teammate = parseDungeonTeammateTabLine(tabLine);
            if (teammate == null || teammate.name().isBlank()) {
                continue;
            }

            teammates.putIfAbsent(teammate.name().toLowerCase(Locale.ROOT), teammate);
        }

        return teammates;
    }

    public static List<DungeonTeammate> getDungeonTeammates() {
        return new ArrayList<>(getDungeonTeammateLookup().values());
    }

    public static List<DungeonTeammate> getDungeonTeammatesNoSelf() {
        List<DungeonTeammate> teammates = getDungeonTeammates();
        if (mc == null || mc.player == null || mc.player.getName() == null) {
            return teammates;
        }

        String selfName = mc.player.getName().getString();
        if (selfName == null || selfName.isBlank()) {
            return teammates;
        }

        List<DungeonTeammate> filtered = new ArrayList<>();
        for (DungeonTeammate teammate : teammates) {
            if (!teammate.name().equalsIgnoreCase(selfName)) {
                filtered.add(teammate);
            }
        }
        return filtered;
    }

    /**
     * Parses a single tab line in the format commonly used for dungeon teammates.
     * Example: [31] [MVP+] PlayerName ... (HEALER XXVIII)
     */
    public static DungeonTeammate parseDungeonTeammateTabLine(String tabLine) {
        String cleaned = normalizeTabText(tabLine);
        if (cleaned.isBlank()) {
            return null;
        }

        Matcher matcher = DUNGEON_TEAMMATE_TAB_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            return null;
        }

        String teammateName = matcher.group(2);
        DungeonClassType classType = DungeonClassType.fromTabToken(matcher.group(3));
        if (classType == DungeonClassType.UNKNOWN) {
            return null;
        }

        boolean isDead = classType == DungeonClassType.DEAD;
        int classLevel = isDead ? 0 : parseClassLevel(matcher.group(4));
        return new DungeonTeammate(teammateName, classType, classLevel, isDead);
    }

    private static String getTabLine(PlayerListEntry entry) {
        if (entry == null) {
            return "";
        }
        if (entry.getDisplayName() != null) {
            return entry.getDisplayName().getString();
        }
        if (entry.getProfile() != null && entry.getProfile().name() != null) {
            return entry.getProfile().name();
        }
        return "";
    }

    private static String normalizeTabText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = TAB_COLOR_CODE_PATTERN.matcher(text).replaceAll("");
        cleaned = cleaned.replace('\u00A0', ' ').trim();
        cleaned = MULTI_SPACE_PATTERN.matcher(cleaned).replaceAll(" ");
        return cleaned;
    }

    private static int parseClassLevel(String classLevelText) {
        if (classLevelText == null || classLevelText.isBlank()) {
            return 0;
        }

        String text = classLevelText.trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return romanToInt(text);
        }
    }

    private static int romanToInt(String roman) {
        if (roman == null || roman.isBlank()) {
            return 0;
        }

        int total = 0;
        int previous = 0;
        String upper = roman.toUpperCase(Locale.ROOT);
        for (int i = upper.length() - 1; i >= 0; i--) {
            int current = romanValue(upper.charAt(i));
            if (current <= 0) {
                return 0;
            }

            if (current < previous) {
                total -= current;
            } else {
                total += current;
                previous = current;
            }
        }

        return Math.max(total, 0);
    }

    private static int romanValue(char ch) {
        return switch (ch) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            case 'M' -> 1000;
            default -> 0;
        };
    }

    public static boolean isStarMob(net.minecraft.entity.decoration.ArmorStandEntity armorStand) {
        net.minecraft.text.Text text = armorStand.getCustomName();
        if (text == null) return false;
        
        String name = text.getString();
        return name.contains("\u272A") || name.contains("\u2728") || name.contains("✯") || name.contains("✪");

    }

    public static boolean isSecret(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();

        // Check for common redstone/chest secrets
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.LEVER) {
            return true;
        }

        // Check for specific player heads (Wither Essence / Redstone Key)
        if (block == Blocks.PLAYER_HEAD || block == Blocks.PLAYER_WALL_HEAD) {
            BlockEntity blockEntity = mc.world.getBlockEntity(pos);
            if (blockEntity instanceof SkullBlockEntity skull) {
                if (skull.getOwner() != null && skull.getOwner().getGameProfile() != null) {
                    String uuid = skull.getOwner().getGameProfile().id().toString();
                    return uuid.equals(SecretClick.WITHER_ESSENCE_ID) || uuid.equals(SecretClick.REDSTONE_KEY_ID);
                }
            }
        }
        return false;
    }

    public static String getDungeonFloor() {
        if (mc.world == null) return null;

        List<Text> sidebar = Utils.getScoreboardSidebar();
        if (sidebar == null) return null;

        for (Text text : sidebar) {
            String line = text.getString().replaceAll("\u00A7.", ""); // Clean any trailing color codes

            if (line.contains("The Catacombs (")) {
                int startIndex = line.indexOf("The Catacombs (") + 15;
                int endIndex = line.indexOf(")", startIndex);
                
                if (endIndex != -1) {
                    return line.substring(startIndex, endIndex); // e.g. "F1", "M7"
                }
            }
        }
        return null;
    }

    public static boolean isDungeonFloor(String floor) {
        String currentFloor = getDungeonFloor();
        return currentFloor != null && currentFloor.equalsIgnoreCase(floor);
    }

    public static boolean isMasterMode() {
        String floorStr = getDungeonFloor();
        return floorStr != null && floorStr.toLowerCase().startsWith("m");
    }

    public static boolean inBoss() {
        if (mc.player == null) return false;

        String floorStr = getDungeonFloor();
        if (floorStr == null || floorStr.isEmpty()) return false;

        int floorNumber = -1;
        try {
            // Extracts the number from "F1", "M7", etc.
            floorNumber = Integer.parseInt(floorStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return false;
        }

        return getBoss(floorNumber, mc.player.getX(), mc.player.getZ());
    }

    public static boolean getBoss(int floorNumber, double posX, double posZ) {
        switch (floorNumber) {
            case 1:
                return posX > -71 && posZ > -39;
            case 2:
            case 3:
            case 4:
                return posX > -39 && posZ > -39;
            case 5:
            case 6:
                return posX > -39 && posZ > -7;
            case 7:
                return posX > -7 && posZ > -7;
            default:
                return false;
        }
    }

    public enum DungeonClassType {
        ARCHER,
        BERSERK,
        HEALER,
        MAGE,
        TANK,
        DEAD,
        UNKNOWN;

        public static DungeonClassType fromTabToken(String token) {
            if (token == null || token.isBlank()) {
                return UNKNOWN;
            }

            return switch (token.trim().toUpperCase(Locale.ROOT)) {
                case "ARCH", "ARCHER" -> ARCHER;
                case "BERS", "BERSERK" -> BERSERK;
                case "HEAL", "HEALER" -> HEALER;
                case "MAGE" -> MAGE;
                case "TANK" -> TANK;
                case "DEAD" -> DEAD;
                default -> UNKNOWN;
            };
        }
    }

    public record DungeonTeammate(String name, DungeonClassType classType, int classLevel, boolean dead) {
    }
}
