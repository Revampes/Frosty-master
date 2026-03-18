package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.ItemUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import com.revampes.Fault.utility.skyblock.HeadTextures;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.awt.Color;

public class KeyHighlight extends Module {

    private final ButtonSetting announceKeySpawn = new ButtonSetting("Announce Key Spawn", true);
    private final ButtonSetting drawLine = new ButtonSetting("Draw Line", true);
    private final SliderSetting lineWidth = new SliderSetting("Line Width", 2, 1, 10, 0.5);
    private final SelectSetting currentHighlight = new SelectSetting("Highlight Type", 0, new String[]{"Both", "Filled", "Outline"});
    private final SliderSetting outlineWidth = new SliderSetting("Outline Width", 2, 1, 10, 0.5);
    private final ColorSetting witherColor = new ColorSetting("Wither Color", new Color(0, 0, 0, 204));
    private final ColorSetting witherLineColor = new ColorSetting("Wither Line Color", new Color(0, 0, 0, 204));
    private final ColorSetting bloodColor = new ColorSetting("Blood Color", new Color(255, 85, 85, 204));
    private final ColorSetting bloodLineColor = new ColorSetting("Blood Line Color", new Color(255, 85, 85, 204));

    private ArmorStandEntity currentWitherKey = null;
    private ArmorStandEntity currentBloodKey = null;

    public KeyHighlight() {
        super("KeyHighlight", category.Dungeon);
        this.registerSetting(announceKeySpawn);
        this.registerSetting(drawLine);
        this.registerSetting(lineWidth);
        this.registerSetting(currentHighlight);
        this.registerSetting(outlineWidth);
        this.registerSetting(witherColor);
        this.registerSetting(witherLineColor);
        this.registerSetting(bloodColor);
        this.registerSetting(bloodLineColor);
    }

    @Override
    public void guiUpdate() {
        this.lineWidth.setVisibilityCondition(() -> drawLine.isToggled());
        this.witherLineColor.setVisibilityCondition(() -> drawLine.isToggled());
        this.bloodLineColor.setVisibilityCondition(() -> drawLine.isToggled());
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!DungeonUtils.isInDungeon()) {
            currentWitherKey = null;
            currentBloodKey = null;
            return;
        }

        MatrixStack stack = event.getMatrix();

        if (currentWitherKey != null && (!currentWitherKey.isAlive() || mc.world.getEntityById(currentWitherKey.getId()) == null)) {
            currentWitherKey = null;
        }
        if (currentBloodKey != null && (!currentBloodKey.isAlive() || mc.world.getEntityById(currentBloodKey.getId()) == null)) {
            currentBloodKey = null;
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ArmorStandEntity stand) {
                ItemStack headStack = stand.getEquippedStack(EquipmentSlot.HEAD);
                if (headStack != null && !headStack.isEmpty() && headStack.isOf(Items.PLAYER_HEAD)) {
                    String texture = ItemUtils.getHeadTexture(headStack);
                    if (texture != null) {
                        if (texture.equals(HeadTextures.WITHER_KEY)) {
                            if (currentWitherKey != stand) {
                                currentWitherKey = stand;
                                if (announceKeySpawn.isToggled()) {
                                    Utils.addChatMessage("§8Wither Key§7 spawned!");
                                    mc.inGameHud.setTitleTicks(10, 40, 10);
                                    mc.inGameHud.setTitle(Text.literal("§8Wither Key§7 spawned!"));
                                }
                            }
                        } else if (texture.equals(HeadTextures.BLOOD_KEY)) {
                            if (currentBloodKey != stand) {
                                currentBloodKey = stand;
                                if (announceKeySpawn.isToggled()) {
                                    Utils.addChatMessage("§cBlood Key§7 spawned!");
                                    mc.inGameHud.setTitleTicks(10, 40, 10);
                                    mc.inGameHud.setTitle(Text.literal("§cBlood Key§7 spawned!"));
                                }
                            }
                        }
                    }
                }
            }
        }

        String mode = currentHighlight.getOption();
        boolean doFill = mode == null || mode.equals("Filled") || mode.equals("Both");
        boolean doOutline = mode == null || mode.equals("Outline") || mode.equals("Both");

        if (currentWitherKey != null) {
            double x = currentWitherKey.getX();
            double y = currentWitherKey.getY();
            double z = currentWitherKey.getZ();
            Box box = new Box(x - 0.5, y + 1.0, z - 0.5, x + 0.5, y + 2.0, z + 0.5);
            Color colored = witherColor.getColor();
            
            if (doFill) {
                RenderUtils.drawBoxFilled(stack, box, colored);
            }
            if (doOutline) {
                RenderUtils.drawBox(stack, box, colored, outlineWidth.getInput());
            }
            if (drawLine.isToggled()) {
                Vec3d crosshair = mc.player.getCameraPosVec(event.getDelta()).add(mc.player.getRotationVec(event.getDelta()).multiply(2.0));
                Vec3d center = new Vec3d(x, y + 1.5, z);
                RenderUtils.drawLine(stack, crosshair, center, witherLineColor.getColor(), lineWidth.getInput());
            }
        }

        if (currentBloodKey != null) {
            double x = currentBloodKey.getX();
            double y = currentBloodKey.getY();
            double z = currentBloodKey.getZ();
            Box box = new Box(x - 0.5, y + 1.0, z - 0.5, x + 0.5, y + 2.0, z + 0.5);
            Color colored = bloodColor.getColor();
            if (doFill) {
                RenderUtils.drawBoxFilled(stack, box, colored);
            }
            if (doOutline) {
                RenderUtils.drawBox(stack, box, colored, outlineWidth.getInput());
            }
            if (drawLine.isToggled()) {
                Vec3d crosshair = mc.player.getCameraPosVec(event.getDelta()).add(mc.player.getRotationVec(event.getDelta()).multiply(2.0));
                Vec3d center = new Vec3d(x, y + 1.5, z);
                RenderUtils.drawLine(stack, crosshair, center, bloodLineColor.getColor(), lineWidth.getInput());
            }
        }
    }
}