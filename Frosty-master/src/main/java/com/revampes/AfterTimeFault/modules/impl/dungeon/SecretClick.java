package com.revampes.AfterTimeFault.modules.impl.dungeon;

import com.revampes.AfterTimeFault.events.impl.ReceivePacketEvent;
import com.revampes.AfterTimeFault.events.impl.Render3DEvent;
import com.revampes.AfterTimeFault.events.impl.SendPacketEvent;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;
import com.revampes.AfterTimeFault.settings.impl.SelectSetting;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;
import com.revampes.AfterTimeFault.utility.DungeonUtils;
import com.revampes.AfterTimeFault.utility.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import com.mojang.authlib.properties.Property;

import java.awt.Color;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SecretClick extends Module {

    public static final String WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23";
    public static final String REDSTONE_KEY_ID = "fed95410-aba1-39df-9b95-1d4f361eb66e";

    // Mod settings
    private final SliderSetting duration = new SliderSetting("Stay Duration (s)", 3.0, 1.0, 10.0, 1.0);
    private final SelectSetting renderMode = new SelectSetting("Render Mode", 2, new String[]{"Outline", "Filled", "Filled Outline"});
    private final ButtonSetting throughWalls = new ButtonSetting("Show Through Walls", true);
    
    // Sliders for RGB color implementation
    private final SliderSetting colorRed = new SliderSetting("Red", 255.0, 0.0, 255.0, 1.0);
    private final SliderSetting colorGreen = new SliderSetting("Green", 50.0, 0.0, 255.0, 1.0);
    private final SliderSetting colorBlue = new SliderSetting("Blue", 50.0, 0.0, 255.0, 1.0);

    // Concurrent Maps to store highlighted blocks and entities against clicked timestamps
    private final Map<BlockPos, Long> clickedBlocks = new ConcurrentHashMap<>();
    private final Map<Vec3d, Long> killedBats = new ConcurrentHashMap<>();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public SecretClick() {
        super("SecretClick", "Highlights dungeon secrets when clicked, and bats when killed.", category.Dungeon);
        // Automatically registers settings to GUI
        this.registerSetting(duration);
        this.registerSetting(renderMode);
        this.registerSetting(throughWalls);
        this.registerSetting(colorRed);
        this.registerSetting(colorGreen);
        this.registerSetting(colorBlue);
    }

    @EventHandler
    public void onSendPacket(SendPacketEvent event) {
        // Detect player interacting with a block on the netty thread lightly
        if (event.getPacket() instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos pos = packet.getBlockHitResult().getBlockPos();
            
            mc.execute(() -> {
                if (mc.world == null || mc.player == null) return;
                
                // Moved check inside execute() to prevent CME reading tab list off-thread
                if (!DungeonUtils.isInDungeon()) return;
                
                if (isSecret(pos)) {
                    clickedBlocks.put(pos, System.currentTimeMillis());
                }
            });
        }
    }

    @EventHandler
    public void onReceivePacket(ReceivePacketEvent event) {
        // Detect entity death statuses sent by the server on the netty thread
        if (event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 3) {
                mc.execute(() -> {
                    if (mc.world == null) return;
                    
                    // Moved check inside execute() to prevent CME reading tab list off-thread
                    if (!DungeonUtils.isInDungeon()) return;
                    
                    Entity entity = packet.getEntity(mc.world);
                    if (entity instanceof BatEntity) {
                        killedBats.put(new Vec3d(entity.getX(), entity.getY(), entity.getZ()), System.currentTimeMillis());
                    }
                });
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        long currentTime = System.currentTimeMillis();
        long maxDuration = (long) (duration.getInput() * 1000L);
        
        // Define color from user settings (Alpha at 150 for partial transparency on fills)
        Color fillColor = new Color((int) colorRed.getInput(), (int) colorGreen.getInput(), (int) colorBlue.getInput(), 150);
        Color outlineColor = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 255);

        // Clean up expired highlights
        clickedBlocks.entrySet().removeIf(entry -> currentTime - entry.getValue() > maxDuration);

        // Iterate and render Block Highlights
        for (BlockPos pos : clickedBlocks.keySet()) {
            Box box = new Box(pos);
            drawHighlight(event.getMatrix(), box, fillColor, outlineColor);
        }

        // Clean up expired bat highlights
        killedBats.entrySet().removeIf(entry -> currentTime - entry.getValue() > maxDuration);

        // Iterate and render Bat Highlights
        for (Vec3d pos : killedBats.keySet()) {
            // Render a 1x1 roughly-bat-sized box around bat death position
            Box box = new Box(pos.x - 0.5, pos.y, pos.z - 0.5, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5);
            drawHighlight(event.getMatrix(), box, fillColor, outlineColor);
        }
    }

    private void drawHighlight(net.minecraft.client.util.math.MatrixStack stack, Box box, Color fillColor, Color outlineColor) {
        if (throughWalls.isEnabled) {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        }

        String mode = renderMode.getOption();
        if ("Filled".equals(mode) || "Filled Outline".equals(mode)) {
            RenderUtils.drawBoxFilled(stack, box, fillColor);
        }
        if ("Outline".equals(mode) || "Filled Outline".equals(mode)) {
            RenderUtils.drawBox(stack, box, outlineColor, 2.0);
        }

        if (throughWalls.isEnabled) {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        }
    }

    /**
     * Checks if the block at the given position is a Secret (Chests, Levers, Wither/Redstone heads).
     */
    private boolean isSecret(BlockPos pos) {
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
                    return uuid.equals(WITHER_ESSENCE_ID) || uuid.equals(REDSTONE_KEY_ID);
                }
            }
        }
        return false;
    }
}