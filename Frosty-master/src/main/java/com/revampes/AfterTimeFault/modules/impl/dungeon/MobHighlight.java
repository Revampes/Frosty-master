package com.revampes.AfterTimeFault.modules.impl.dungeon;

import com.revampes.AfterTimeFault.events.impl.Render3DEvent;
import com.revampes.AfterTimeFault.modules.Module;
import com.revampes.AfterTimeFault.settings.impl.ButtonSetting;
import com.revampes.AfterTimeFault.settings.impl.SelectSetting;
import com.revampes.AfterTimeFault.settings.impl.SliderSetting;
import com.revampes.AfterTimeFault.utility.DungeonUtils;
import com.revampes.AfterTimeFault.utility.RenderUtils;
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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MobHighlight extends Module {

    public enum MobType {
        STAR, TANK, MINI, FEL, ASSASSIN, MIMIC
    }

    private static final int LEATHER_BOOTS_ID = Item.getRawId(Items.LEATHER_BOOTS);

    private final ButtonSetting dontShowInvisibleMobs = new ButtonSetting("Hide Invisible", true);
    private final SelectSetting currentHighlight = new SelectSetting("Highlight Type", 0, new String[]{"Both", "Filled", "Outline"});
    private final SliderSetting outlineWidth = new SliderSetting("Outline Width", 4, 1, 10, 0.5);

    private static final int starFilledColor = 0xff00ff00;
    private static final int tankFilledColor = 0xffff0000;
    private static final int miniFilledColor = 0xffffff00;
    private static final int felFilledColor = 0xff00ffff;
    private static final int assassinFilledColor = 0xff800080;
    private static final int mimicFilledColor = 0xffffffff;

    public MobHighlight() {
        super("MobHighlight", category.Dungeon);
        this.registerSetting(dontShowInvisibleMobs);
        this.registerSetting(currentHighlight);
        this.registerSetting(outlineWidth);
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
        
        if (name.contains("King Midas")) return MobType.MINI;
        if (name.contains("Mimic")) return MobType.MIMIC;
        if (!DungeonUtils.isStarMob(armorStand)) return null;
        if (name.contains("Fel")) return MobType.FEL;
        if (isMiniBoss(name)) return MobType.MINI;
        if (isTankMob(name)) return MobType.TANK;
        return MobType.STAR;
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
        if (!text.getString().equals("Silent Death")) return false;

        return Item.getRawId(boots.getItem()) == LEATHER_BOOTS_ID;
    }

    private int getFilledColor(MobType mob) {
        return switch (mob) {
            case STAR -> starFilledColor;
            case TANK -> tankFilledColor;
            case MINI -> miniFilledColor;
            case FEL -> felFilledColor;
            case ASSASSIN -> assassinFilledColor;
            case MIMIC -> mimicFilledColor;
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
