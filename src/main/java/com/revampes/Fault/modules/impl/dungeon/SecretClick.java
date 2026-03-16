package com.revampes.Fault.modules.impl.dungeon;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class SecretClick extends Module {

    public static final String WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23";
    public static final String REDSTONE_KEY_ID = "fed95410-aba1-39df-9b95-1d4f361eb66e";

    public static final List<String> DUNGEON_ITEM_DROPS = Arrays.asList(
        "Health Potion VIII Splash Potion", "Healing Potion 8 Splash Potion", "Healing Potion VIII Splash Potion", "Healing VIII Splash Potion", "Healing 8 Splash Potion",
        "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap", "Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
        "Secret Dye", "Candycomb"
    );

    // Mod settings
    private final SliderSetting duration = new SliderSetting("Stay Duration (s)", 3.0, 1.0, 10.0, 1.0);
    private final SelectSetting renderMode = new SelectSetting("Render Mode", 2, new String[]{"Outline", "Filled", "Filled Outline"});
    private final ButtonSetting throughWalls = new ButtonSetting("Show Through Walls", true);
    private final ButtonSetting secretChime = new ButtonSetting("Secret Chime", true);
    
    // Sliders for RGB color implementation
    private final SliderSetting colorRed = new SliderSetting("Red", 255.0, 0.0, 255.0, 1.0);
    private final SliderSetting colorGreen = new SliderSetting("Green", 50.0, 0.0, 255.0, 1.0);
    private final SliderSetting colorBlue = new SliderSetting("Blue", 50.0, 0.0, 255.0, 1.0);

    // Concurrent Maps to store highlighted blocks and entities against clicked timestamps
    private final Map<BlockPos, Long> clickedBlocks = new ConcurrentHashMap<>();
    private final Map<Vec3d, Long> killedBats = new ConcurrentHashMap<>();
    private final Map<Vec3d, Long> pickedUpItems = new ConcurrentHashMap<>();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public SecretClick() {
        super("SecretClick", "Highlights dungeon secrets when clicked, and bats when killed.", category.Dungeon);
        // Automatically registers settings to GUI
        this.registerSetting(duration);
        this.registerSetting(renderMode);
        this.registerSetting(throughWalls);
        this.registerSetting(secretChime);
        this.registerSetting(colorRed);
        this.registerSetting(colorGreen);
        this.registerSetting(colorBlue);
    }

    private void playChime() {
        if (secretChime.isEnabled) {
            mc.player.playSound(SoundEvents.ENTITY_BLAZE_HURT, 1.0f, 1.0f);
        }
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
                
                if (DungeonUtils.isSecret(pos)) {
                    if (!clickedBlocks.containsKey(pos)) {
                        playChime();
                    }
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
                        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
                        if (!killedBats.containsKey(pos)) {
                            killedBats.put(pos, System.currentTimeMillis());
                            playChime();
                        }
                    }
                });
            }
        }
        
        if (event.getPacket() instanceof ItemPickupAnimationS2CPacket packet) {
            mc.execute(() -> {
                if (mc.world == null || mc.player == null) return;
                if (!DungeonUtils.isInDungeon()) return;
                
                if (packet.getCollectorEntityId() == mc.player.getId()) {
                    Entity itemEnt = mc.world.getEntityById(packet.getEntityId());
                    if (itemEnt instanceof ItemEntity item) {
                        String name = item.getStack().getName().getString();
                        for (String drop : DUNGEON_ITEM_DROPS) {
                            if (name.contains(drop)) {
                                Vec3d pos = new Vec3d(itemEnt.getX(), itemEnt.getY(), itemEnt.getZ());
                                pickedUpItems.put(pos, System.currentTimeMillis());
                                playChime();
                                break;
                            }
                        }
                    }
                }
            });
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
            RenderUtils.drawHighlight(event.getMatrix(), box, fillColor, outlineColor, throughWalls.isEnabled, renderMode.getOption());
        }

        // Clean up expired bat highlights
        killedBats.entrySet().removeIf(entry -> currentTime - entry.getValue() > maxDuration);

        // Iterate and render Bat Highlights
        for (Vec3d pos : killedBats.keySet()) {
            // Render a 1x1 roughly-bat-sized box around bat death position
            Box box = new Box(pos.x - 0.5, pos.y, pos.z - 0.5, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5);
            RenderUtils.drawHighlight(event.getMatrix(), box, fillColor, outlineColor, throughWalls.isEnabled, renderMode.getOption());
        }

        // Clean up expired item highlights
        pickedUpItems.entrySet().removeIf(entry -> currentTime - entry.getValue() > maxDuration);

        // Iterate and render Item Highlights
        for (Vec3d pos : pickedUpItems.keySet()) {
            Box box = new Box(pos.x - 0.25, pos.y - 0.25, pos.z - 0.25, pos.x + 0.25, pos.y + 0.25, pos.z + 0.25);
            RenderUtils.drawHighlight(event.getMatrix(), box, fillColor, outlineColor, throughWalls.isEnabled, renderMode.getOption());
        }
    }
}