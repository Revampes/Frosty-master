package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;

public class SecretESP extends Module {

    private static final String WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23";
    private static final String REDSTONE_KEY_ID = "fed95410-aba1-39df-9b95-1d4f361eb66e";

    private final ColorSetting chestColor = new ColorSetting("Chest Color", new Color(200, 125, 0, 255));
    private final ColorSetting witherEssenceColor = new ColorSetting("Wither Essence Color", new Color(170, 85, 255, 255));
    private final ColorSetting redstoneKeyColor = new ColorSetting("Redstone Key Color", new Color(255, 85, 85, 255));
    private final SliderSetting lineWidth = new SliderSetting("Line Width", 3.0, 1.0, 6.0, 0.5);

    public SecretESP() {
        super("SecretESP", category.Dungeon);
        this.registerSetting(chestColor);
        this.registerSetting(witherEssenceColor);
        this.registerSetting(redstoneKeyColor);
        this.registerSetting(lineWidth);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!Utils.nullCheck() || !DungeonUtils.isInDungeon()) {
            return;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        int renderDistance = mc.options.getViewDistance().getValue();
        ChunkPos centerChunk = new ChunkPos(playerPos);
        int chunkRadius = Math.min(renderDistance, 8);

        for (int x = centerChunk.x - chunkRadius; x <= centerChunk.x + chunkRadius; x++) {
            for (int z = centerChunk.z - chunkRadius; z <= centerChunk.z + chunkRadius; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getChunk(x, z, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ChestBlockEntity chest) {
                        renderChest(event.getMatrix(), chest, event.getDelta());
                    } else if (blockEntity instanceof SkullBlockEntity skull) {
                        renderSkullSecret(event.getMatrix(), skull);
                    }
                }
            }
        }
    }

    private void renderChest(MatrixStack matrices, ChestBlockEntity chest, float partialTicks) {
        if (chest.getAnimationProgress(partialTicks) > 0.0F) {
            return;
        }

        BlockPos pos = chest.getPos();
        Block block = mc.world.getBlockState(pos).getBlock();
        if (!(block instanceof ChestBlock)) {
            return;
        }

        RenderUtils.drawBox(matrices, adjustChestBox(pos), chestColor.getColor(), lineWidth.getInput());
    }

    private void renderSkullSecret(MatrixStack matrices, SkullBlockEntity skull) {
        BlockPos pos = skull.getPos();
        Block block = mc.world.getBlockState(pos).getBlock();
        if (block != Blocks.PLAYER_HEAD && block != Blocks.PLAYER_WALL_HEAD) {
            return;
        }

        if (skull.getOwner() == null || skull.getOwner().getGameProfile() == null || skull.getOwner().getGameProfile().id() == null) {
            return;
        }

        String uuid = skull.getOwner().getGameProfile().id().toString();
        if (WITHER_ESSENCE_ID.equals(uuid)) {
            RenderUtils.drawBox(matrices, new Box(pos), witherEssenceColor.getColor(), lineWidth.getInput());
        } else if (REDSTONE_KEY_ID.equals(uuid)) {
            RenderUtils.drawBox(matrices, new Box(pos), redstoneKeyColor.getColor(), lineWidth.getInput());
        }
    }

    private Box adjustChestBox(BlockPos pos) {
        double scale = 0.875;
        double singleWidth = 1.0 * scale;
        double singleMinX = pos.getX() + (1.0 - scale) / 2;
        double singleMinZ = pos.getZ() + (1.0 - scale) / 2;
        Box box = new Box(
                singleMinX, pos.getY(), singleMinZ,
                singleMinX + singleWidth, pos.getY() + singleWidth, singleMinZ + singleWidth
        );

        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return box;
        }

        Direction facing = state.get(ChestBlock.FACING);
        ChestType chestType = state.get(ChestBlock.CHEST_TYPE);

        if (chestType != ChestType.SINGLE) {
            BlockPos connectedPos;
            if (chestType == ChestType.RIGHT) {
                connectedPos = pos.offset(facing.rotateYCounterclockwise());
            } else {
                connectedPos = pos.offset(facing.rotateYClockwise());
            }

            if (mc.world.getBlockState(connectedPos).getBlock() instanceof ChestBlock) {
                double minX = Math.min(pos.getX(), connectedPos.getX()) + (1.0 - scale) / 2;
                double minZ = Math.min(pos.getZ(), connectedPos.getZ()) + (1.0 - scale) / 2;
                double doubleWidthX = (facing.getAxis() == Direction.Axis.X) ? 2.0 * scale : singleWidth;
                double doubleWidthZ = (facing.getAxis() == Direction.Axis.Z) ? 2.0 * scale : singleWidth;
                return new Box(
                        minX, pos.getY(), minZ,
                        minX + doubleWidthX, pos.getY() + singleWidth, minZ + doubleWidthZ
                );
            }
        }

        return box;
    }
}