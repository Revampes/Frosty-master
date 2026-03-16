package com.revampes.Fault.utility;


import static com.revampes.Fault.Revampes.mc;
import com.revampes.Fault.modules.impl.dungeon.SecretClick;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class DungeonUtils {
    
    /**
     * Helper check to determine if the player is currently inside a dungeon instance.
     * @return true if LocationUtils detects "Dungeon" or "Catacombs".
     */
    public static boolean isInDungeon() {
        return LocationUtils.isInDungeon();
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
}
