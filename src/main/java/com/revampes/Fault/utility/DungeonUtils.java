package com.revampes.Fault.utility;


import static com.revampes.Fault.Revampes.mc;
import com.revampes.Fault.modules.impl.dungeon.SecretClick;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.util.math.BlockPos;

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

}
