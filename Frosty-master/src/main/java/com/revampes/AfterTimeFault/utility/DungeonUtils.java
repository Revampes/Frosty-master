package com.revampes.AfterTimeFault.utility;


import java.awt.Color;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexFormat;
import static com.revampes.AfterTimeFault.Revampes.mc;
import com.revampes.AfterTimeFault.modules.impl.dungeon.SecretClick;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;

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
        
        for (net.minecraft.text.Text sib : text.getSiblings()) {
            net.minecraft.text.TextColor color = sib.getStyle().getColor();
            if (color == null) continue;

            if (color.getRgb() == 0xFFAA00 && sib.getString().equals("\u272A ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the block at the given position is a Secret (Chests, Levers, Wither/Redstone heads).
     */
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
