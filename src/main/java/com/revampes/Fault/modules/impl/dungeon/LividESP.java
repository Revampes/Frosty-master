package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;

public class LividESP extends Module {

    private final BlockPos ceilingPos = new BlockPos(5, 109, 43);
    private DyeColor targetColor = null;

    private final ButtonSetting filledBox = new ButtonSetting("Filled Box", true);
    private final ButtonSetting outline = new ButtonSetting("Outline", true);
    public final ButtonSetting hideWrongLivid = new ButtonSetting("Hide Wrong Livid Name Tag", false);

    public LividESP() {
        super("LividESP", "Finds the correct Livid via ceiling glass.", category.Dungeon);
        this.registerSetting(filledBox);
        this.registerSetting(outline);
        this.registerSetting(hideWrongLivid);
    }

    private boolean shouldCheck() {
        if (mc.world == null || mc.player == null) return false;
        if (!DungeonUtils.isInDungeon()) return false;

        String floorStr = DungeonUtils.getDungeonFloor();
        if (floorStr == null || !floorStr.endsWith("5")) return false;

        return DungeonUtils.inBoss();
    }

    @EventHandler
    public void onPreUpdate(PreUpdateEvent event) {
        if (!shouldCheck()) {
            targetColor = null;
            return;
        }

        BlockState state = mc.world.getBlockState(ceilingPos);
        if (state != null && state.getBlock() instanceof StainedGlassBlock) {
            targetColor = ((StainedGlassBlock) state.getBlock()).getColor();
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (targetColor == null || !shouldCheck()) return;

        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (isCorrectLivid(entity)) {
                renderESP(event.getMatrix(), entity, event.getDelta());
            }
        }
    }

    private boolean isCorrectLivid(net.minecraft.entity.Entity entity) {
        if (!(entity instanceof ArmorStandEntity) || !entity.hasCustomName()) return false;
        
        String name = entity.getCustomName().getString();
        String targetCode = getColorCode(targetColor);
                
        if (targetCode != null && name.contains("Livid")) {
            String formattedName = com.revampes.Fault.utility.Utils.FormattedText(entity.getCustomName());
            if (formattedName.contains(targetCode) || (name.contains(targetCode.replace("\u00a7", "")) && name.contains("Livid"))) {
                if (formattedName.contains(targetCode + "\u00a7lLivid") || formattedName.contains(targetCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isWrongLividConfigured(net.minecraft.entity.Entity entity) {
        LividESP module = (LividESP) com.revampes.Fault.modules.ModuleManager.lividESP;
        if (module == null || !module.isEnabled() || !module.hideWrongLivid.isToggled()) return false;
        if (!module.shouldCheck() || module.targetColor == null) return false;

        // Ensure it's Livid at all
        if (entity instanceof ArmorStandEntity && entity.hasCustomName()) {
            String name = entity.getCustomName().getString();
            if (name.contains("Livid") && !module.isCorrectLivid(entity)) {
                return true;
            }
        }
        // If we want to hide the zombie/player Livid entity as well, we would need to check if its passenger/helmet is the wrong armor stand.
        // Or if its name is just "Livid". Usually in 1.8.9 they have fake entities with nametags.
        if (entity.hasCustomName() && entity.getCustomName().getString().contains("Livid") && !module.isCorrectLivid(entity)) {
            return true;
        }

        return false;
    }

    private void renderESP(MatrixStack matrices, net.minecraft.entity.Entity entity, float partialTicks) {
        double x = MathHelper.lerp(partialTicks, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(partialTicks, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(partialTicks, entity.lastRenderZ, entity.getZ());

        Box box = new Box(-0.25, 0, -0.25, 0.25, 2.0, 0.25).offset(x, y - 2.0, z); // Adjust offset for armor stand height
        
        Color espColor = getESPColor(targetColor);

        if (filledBox.isToggled()) {
            RenderUtils.drawBoxFilled(matrices, box, new Color(espColor.getRed(), espColor.getGreen(), espColor.getBlue(), 50));
        }

        if (outline.isToggled()) {
            RenderUtils.drawBox(matrices, box, espColor, 2.0);
        }
    }

    private String getColorCode(DyeColor dye) {
        if (dye == null) return null;
        switch (dye) {
            case WHITE: return "\u00a7f";
            case MAGENTA: return "\u00a7d";
            case RED: return "\u00a7c";
            case LIGHT_GRAY: return "\u00a77";
            case GRAY: return "\u00a77";
            case GREEN: return "\u00a72";
            case LIME: return "\u00a7a";
            case BLUE: return "\u00a79";
            case PURPLE: return "\u00a75";
            case YELLOW: return "\u00a7e";
            default: return null;
        }
    }

    private Color getESPColor(DyeColor dye) {
        if (dye == null) return Color.RED;
        switch (dye) {
            case WHITE: return Color.WHITE;
            case MAGENTA: return new Color(255, 85, 255); // light purple
            case RED: return new Color(255, 85, 85);
            case LIGHT_GRAY: return new Color(170, 170, 170); // gray
            case GRAY: return new Color(170, 170, 170);
            case GREEN: return new Color(0, 170, 0); // dark green
            case LIME: return new Color(85, 255, 85); // green
            case BLUE: return new Color(85, 85, 255); // blue
            case PURPLE: return new Color(170, 0, 170); // dark purple
            case YELLOW: return new Color(255, 255, 85); // yellow
            default: return Color.WHITE;
        }
    }
}