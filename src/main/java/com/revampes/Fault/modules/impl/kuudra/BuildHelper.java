package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.Render2DEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildHelper extends Module {

    private final ButtonSetting buildHelperDraw = new ButtonSetting("Render on Ballista", false);
    private final ButtonSetting unfinishedWaypoints = new ButtonSetting("Unfinished Waypoints", true);
    private final ButtonSetting hideDefaultTag = new ButtonSetting("Hide Default Tag", true);
    private final ButtonSetting hudSettings = new ButtonSetting("HUD", true);

    private final SliderSetting stunNotificationNumber = new SliderSetting("Stun Percent", "%", 93, 0, 100, 1);
    
    private final ColorSetting color0To33 = new ColorSetting("0-33% Color", new Color(255, 0, 0));
    private final ColorSetting color33To77 = new ColorSetting("33-77% Color", new Color(255, 255, 0));
    private final ColorSetting color77To100 = new ColorSetting("77-100% Color", new Color(0, 255, 0));

    private boolean alerted = false;
    private static final Pattern PROGRESS_REGEX = Pattern.compile("PROGRESS: (\\d+)%");

    public BuildHelper() {
        super("Build Helper", category.Kuudra);
        this.registerSetting(buildHelperDraw);
        this.registerSetting(unfinishedWaypoints);
        this.registerSetting(hideDefaultTag);
        this.registerSetting(hudSettings);
        this.registerSetting(stunNotificationNumber);
        this.registerSetting(color0To33);
        this.registerSetting(color33To77);
        this.registerSetting(color77To100);
    }

    @Override
    public String getDesc() {
        return "Displays various information about the current state of the ballista build.";
    }

    @EventHandler
    private void onUpdate(PreUpdateEvent event) {
        if (!KuudraUtils.isInKuudra() || KuudraUtils.phase != 2) {
            alerted = false;
            return;
        }

        double stunTarget = stunNotificationNumber.getInput();
        if (stunTarget > 0 && KuudraUtils.kuudraTier >= 3 && KuudraUtils.buildDonePercentage >= stunTarget) {
            if (!alerted) {
                if (mc.inGameHud != null) {
                    mc.inGameHud.setTitleTicks(10, 40, 10);
                    mc.inGameHud.setTitle(Text.literal("§l§3Go to stun"));
                }
                Utils.addChatMessage("§l§3Go to stun");
                alerted = true;
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!KuudraUtils.isInKuudra() || KuudraUtils.phase != 2) return;

        if (buildHelperDraw.isToggled()) {
            RenderUtils.draw3DText(event.getMatrix(), "§bBuild " + colorBuild(KuudraUtils.buildDonePercentage) + "%", new Vec3d(-101.5, 82.0, -105.5), 3f);
            RenderUtils.draw3DText(event.getMatrix(), "§bBuilders " + colorBuilders(KuudraUtils.playersBuildingAmount), new Vec3d(-101.5, 81.0, -105.5), 3f);
        }

        if (unfinishedWaypoints.isToggled()) {
            for (ArmorStandEntity entity : KuudraUtils.buildingPiles) {
                String name = entity.hasCustomName() ? entity.getCustomName().getString() : "";
                
                int percentage = 0;
                try {
                    String stripped = Utils.stripFormatting(name);
                    Matcher matcher = PROGRESS_REGEX.matcher(stripped);
                    if (matcher.find()) {
                        percentage = Integer.parseInt(matcher.group(1));
                    }
                } catch (Exception ignored) {}

                Color beaconColor = percentage <= 33 ? color0To33.getColor() : (percentage <= 77 ? color33To77.getColor() : color77To100.getColor());
                
                RenderUtils.drawCustomBeacon(event.getMatrix(), name, entity.getBlockPos(), beaconColor);
                
                if (hideDefaultTag.isToggled()) {
                    entity.setCustomNameVisible(false);
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!hudSettings.isToggled() || !KuudraUtils.isInKuudra() || KuudraUtils.phase != 2) return;

        int x = 10;
        int y = mc.getWindow().getScaledHeight() / 2 - 20; // Middle left of screen
        
        event.drawContext.drawText(mc.textRenderer, "§bFreshers: " + colorBuilders(KuudraUtils.freshers.size()), x, y, -1, true);
        event.drawContext.drawText(mc.textRenderer, "§bBuilders: " + colorBuilders(KuudraUtils.playersBuildingAmount), x, y + 9, -1, true);
        event.drawContext.drawText(mc.textRenderer, "§bBuild: " + colorBuild(KuudraUtils.buildDonePercentage) + "%", x, y + 18, -1, true);
    }

    private String colorBuild(int build) {
        if (build >= 75) return "§a" + build;
        if (build >= 50) return "§e" + build;
        if (build >= 25) return "§6" + build;
        return "§c" + build;
    }

    private String colorBuilders(int builders) {
        if (builders >= 3) return "§a" + builders;
        if (builders >= 2) return "§e" + builders;
        return "§c" + builders;
    }
}
