package com.revampes.Fault.modules.impl.render;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.KuudraUtils;

public class HidePlayer extends Module { 

    private static final String[] HIT_TRHOUGH_OPTIONS = new String[] {
        "Off",
        "Dungeon & Kuudra",
        "Always"
    };

    private final SelectSetting hitThroughMode = new SelectSetting("Hit Through", 0, HIT_TRHOUGH_OPTIONS);
    private final SliderSetting hideRadius = new SliderSetting("Hide Radius", " blocks", 6.0, 1.0, 64.0, 0.5);
    private final ButtonSetting wither = new ButtonSetting("Hide Wither", false);

    public HidePlayer() {
        super("Hide Player", category.Render);
        this.registerSetting(hitThroughMode);
        this.registerSetting(hideRadius);
        this.registerSetting(wither);
    }

    public SelectSetting getHitThroughMode() {
        return hitThroughMode;
    }

    public SliderSetting getHideRadius() {
        return hideRadius;
    }

    public ButtonSetting getWither() {
        return wither;
    }

    public static boolean shouldHideEntity(Entity entity) {
        HidePlayer module = ModuleManager.hidePlayer;
        if (module == null || !module.isEnabled() || entity == null || mc.player == null) {
            return false;
        }

        if (entity == mc.player) {
            return false;
        }

        if (module.wither.isToggled() && isDungeonWither(entity)) {
            return true;
        }

        if (entity instanceof PlayerEntity) {
            double radius = module.hideRadius.getInput();
            return mc.player.squaredDistanceTo(entity) <= radius * radius;
        }
        return false;

    }

    public static boolean shouldHitThrough(Entity entity) {
        HidePlayer module = ModuleManager.hidePlayer;
        if (module == null || !module.isEnabled() || entity == null) {
            return false;
        }

        if (module.wither.isToggled() && isDungeonWither(entity)) {
            return true;
        }

        if (!(entity instanceof PlayerEntity)) {
            return false;
        }

        int modeIndex = (int) module.hitThroughMode.getValue();
        if (modeIndex == 1) {
            return DungeonUtils.isInDungeon() || KuudraUtils.isInKuudra();
        }
        if (modeIndex == 2) {
            return true;
        }
        return false;
    }

    private static boolean isDungeonWither(Entity entity) {
        if (entity.getType() != EntityType.WITHER || !(entity instanceof LivingEntity livingEntity)) {
            return false;
        }
        return livingEntity.getMaxHealth() == 300.0F;
    }
}