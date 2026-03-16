package com.revampes.Fault.modules.impl.dungeon;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
// import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

public class MobHighlight extends Module {

    public enum MobType {
        STAR, TANK, MINI, FEL, ASSASSIN, MIMIC
    }

    private static final int LEATHER_BOOTS_ID = Item.getRawId(Items.LEATHER_BOOTS);

    private final ButtonSetting dontShowInvisibleMobs = new ButtonSetting("Hide Invisible", true);
    private final ButtonSetting highlightFel = new ButtonSetting("Highlight Fel", true);
    private final ButtonSetting highlightMimic = new ButtonSetting("Highlight Mimic", true);
    private final ButtonSetting highlightMidas = new ButtonSetting("Highlight King Midas", true);
    private final ButtonSetting highlightMiniBoss = new ButtonSetting("Highlight Miniboss", true);
    private final ButtonSetting highlightTankMob = new ButtonSetting("Highlight TankMob", true);
    private final ButtonSetting highlightStarMob = new ButtonSetting("Highlight StarMob", true);
    private final SelectSetting currentHighlight = new SelectSetting("Highlight Type", 0, new String[]{"Both", "Filled", "Outline"});
    private final SliderSetting outlineWidth = new SliderSetting("Outline Width", 4, 1, 10, 0.5);

    private final com.revampes.Fault.settings.impl.ColorSetting starColor = new com.revampes.Fault.settings.impl.ColorSetting("Star Color", new Color(0, 255, 0, 255));
    private final com.revampes.Fault.settings.impl.ColorSetting tankColor = new com.revampes.Fault.settings.impl.ColorSetting("Tank Color", new Color(255, 0, 0, 255));
    private final com.revampes.Fault.settings.impl.ColorSetting miniColor = new com.revampes.Fault.settings.impl.ColorSetting("Mini Color", new Color(255, 255, 0, 255));
    private final com.revampes.Fault.settings.impl.ColorSetting felColor = new com.revampes.Fault.settings.impl.ColorSetting("Fel Color", new Color(0, 255, 255, 255));
    private final com.revampes.Fault.settings.impl.ColorSetting assassinColor = new com.revampes.Fault.settings.impl.ColorSetting("Assassin Color", new Color(128, 0, 128, 255));
    private final com.revampes.Fault.settings.impl.ColorSetting mimicColor = new com.revampes.Fault.settings.impl.ColorSetting("Mimic Color", new Color(255, 255, 255, 255));

    public MobHighlight() {
        super("MobHighlight", category.Dungeon);
        this.registerSetting(dontShowInvisibleMobs);
        this.registerSetting(highlightFel);
        this.registerSetting(highlightMimic);
        this.registerSetting(highlightMidas);
        this.registerSetting(highlightMiniBoss);
        this.registerSetting(highlightTankMob);
        this.registerSetting(highlightStarMob);
        this.registerSetting(currentHighlight);
        this.registerSetting(outlineWidth);
        this.registerSetting(starColor);
        this.registerSetting(tankColor);
        this.registerSetting(miniColor);
        this.registerSetting(felColor);
        this.registerSetting(assassinColor);
        this.registerSetting(mimicColor);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!DungeonUtils.isInDungeon()) return;
        if (mc.player != null && mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) return;

        MatrixStack stack = event.getMatrix();
        
        List<Entity> targetEntities = new ArrayList<>();
        List<MobType> targetTypes = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ArmorStandEntity armorStand) {
                MobType type = getType(armorStand);
                if (type != null) {
                    int idOffset = getIdOffset(armorStand);
                    if (idOffset >= 0) {
                        int id = armorStand.getId() - idOffset;
                        Entity target = mc.world.getEntityById(id);
                        if (target != null && target.isAlive() && !(target instanceof ArmorStandEntity)) {
                            targetEntities.add(target);
                            targetTypes.add(type);
                        }
                    }
                }
            } else if (entity instanceof PlayerEntity player && isShadowAssassin(player)) {
                targetEntities.add(player);
                targetTypes.add(MobType.ASSASSIN);
            }
        }

        for (int i = 0; i < targetEntities.size(); i++) {
            Entity target = targetEntities.get(i);
            MobType type = targetTypes.get(i);

            if (dontShowInvisibleMobs.isToggled() && target.isInvisible() && target instanceof PlayerEntity) continue;

            Box box = getBox(target);
            Color color = new Color(getFilledColor(type), true);
            
            String mode = currentHighlight.getOption();
            boolean doFill = mode == null || mode.equals("Filled") || mode.equals("Both");
            boolean doOutline = mode == null || mode.equals("Outline") || mode.equals("Both");

            if (doFill) {
                RenderUtils.drawBoxFilled(stack, box, color);
            }
            if (doOutline) {
                RenderUtils.drawBox(stack, box, color, outlineWidth.getInput());
            }
        }
    }

    private MobType getType(ArmorStandEntity armorStand) {
        Text text = armorStand.getCustomName();
        if (text == null) return null;
        String name = text.getString();
        
        if (name.contains("King Midas") && highlightMidas.isToggled()) return MobType.MINI;
        if (name.contains("Mimic") && highlightMimic.isToggled()) return MobType.MIMIC;
        if (name.contains("Fel") && highlightFel.isToggled()) return MobType.FEL;
        if (isMiniBoss(name) && highlightMiniBoss.isToggled()) return MobType.MINI;
        if (isTankMob(name) && highlightTankMob.isToggled()) return MobType.TANK;

        if (DungeonUtils.isStarMob(armorStand) && highlightStarMob.isToggled()) return MobType.STAR;
        
        return null;
    }

    private int getIdOffset(ArmorStandEntity armorStand) {
        Text text = armorStand.getCustomName();
        if (text == null) return -1;
        String name = text.getString();
        if (name.toLowerCase().contains("withermancer")) return 3;
        return 1;
    }

    private boolean isTankMob(String name) {
        return name.contains("Zombie Commander") || name.contains("Zombie Lord") || 
               name.contains("Skeleton Lord") || name.contains("Withermancer") || 
               name.contains("Super Archer");
    }

    private boolean isMiniBoss(String name) {
        return name.contains("Lost Adventurer") || name.contains("Angry Archaeologist") || 
               name.contains("Frozen Adventurer");
    }

    private boolean isShadowAssassin(PlayerEntity player) {
        if (player == mc.player) return false;
        
        ItemStack heldItem = player.getMainHandStack();
        ItemStack boots = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET);
        
        Text text = heldItem.getCustomName();
        if (text == null) return false;
        if (!text.getString().contains("Silent Death")) return false;

        return Item.getRawId(boots.getItem()) == LEATHER_BOOTS_ID;
    }

    private int getFilledColor(MobType mob) {
        return switch (mob) {
            case STAR -> starColor.getRGB();
            case TANK -> tankColor.getRGB();
            case MINI -> miniColor.getRGB();
            case FEL -> felColor.getRGB();
            case ASSASSIN -> assassinColor.getRGB();
            case MIMIC -> mimicColor.getRGB();
        };
    }

    private Box getBox(Entity entity) {
        Box box = entity.getBoundingBox();

        if (entity instanceof EndermanEntity && entity.isInvisible() && dontShowInvisibleMobs.isToggled()) {
            box = box.expand(0, -1.8, 0).offset(0, -1.2, 0);
        }

        if (entity instanceof ZombieEntity zombie) {
            if (zombie.isBaby()) {
                box = box.expand(0.15, 0.2, 0.15);
            }
        }
        return box;
    }
}
