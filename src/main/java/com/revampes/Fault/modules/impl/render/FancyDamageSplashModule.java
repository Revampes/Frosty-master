package com.revampes.Fault.modules.impl.render;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.modules.impl.render.fancydamagesplash.FancyDamageRenderer;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.Utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FancyDamageSplashModule extends Module {
    private static final Pattern DAMAGE_TEXT_PATTERN = Pattern.compile("([✧✯]?)([\\d,]+)[✧✯]?([❤+⚔☄♞]?)");
    private static final int MAX_TRACKED_ENTRIES = 512;
    private static final long STALE_ENTRY_MS = 2500L;

    private final SelectSetting colorMode;
    private final ButtonSetting compactNumbers;
    private final ButtonSetting criticalGradient;
    private final ButtonSetting renderOriginalSymbol;
    private final SliderSetting renderRange;

    private final Map<UUID, SeenEntry> recentIndicators = new HashMap<>();
    private final Set<UUID> hiddenOriginalIndicators = new HashSet<>();

    public FancyDamageSplashModule() {
        super("FancyDamageSplash", category.Render);

        this.registerSetting(colorMode = new SelectSetting("Color Mode", 0, new String[]{"Original", "Random"}));
        this.registerSetting(compactNumbers = new ButtonSetting("Compact Numbers", true));
        this.registerSetting(criticalGradient = new ButtonSetting("Critical Gradient", true));
        this.registerSetting(renderOriginalSymbol = new ButtonSetting("Keep Symbol", true));
        this.registerSetting(renderRange = new SliderSetting("Range", 30, 8, 64, 1));
    }

    public static boolean shouldSuppressOriginalIndicator(Entity entity) {
        if (ModuleManager.fancyDamageSplash == null || !ModuleManager.fancyDamageSplash.isEnabled()) {
            return false;
        }

        if (!(entity instanceof ArmorStandEntity stand)) {
            return false;
        }

        Text customName = stand.getCustomName();
        if (!stand.hasCustomName() || customName == null) {
            return false;
        }

        String text = customName.getString();
        return !text.isBlank() && DAMAGE_TEXT_PATTERN.matcher(text).matches();
    }

    @Override
    public void onDisable() {
        restoreOriginalIndicatorVisibility();
        recentIndicators.clear();
        FancyDamageRenderer.clear();
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            return;
        }

        double range = renderRange.getInput();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Box scanBox = new Box(
                playerPos.x - range, playerPos.y - range, playerPos.z - range,
                playerPos.x + range, playerPos.y + range, playerPos.z + range
        );

        long now = System.currentTimeMillis();
        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, scanBox, this::isDamageIndicator)) {
            if (entity instanceof ArmorStandEntity stand && stand.isCustomNameVisible()) {
                stand.setCustomNameVisible(false);
                hiddenOriginalIndicators.add(stand.getUuid());
            }

            Text indicator = entity.getCustomName();
            if (indicator == null) {
                continue;
            }

            String key = indicator.getString();
            if (key.isBlank()) {
                continue;
            }

            UUID entityId = entity.getUuid();
            SeenEntry seen = recentIndicators.get(entityId);
            if (seen != null && key.equals(seen.text) && (now - seen.time) < STALE_ENTRY_MS) {
                continue;
            }

            recentIndicators.put(entityId, new SeenEntry(key, now));
            enqueueDamageIndicator(indicator, new Vec3d(entity.getX(), entity.getY() + 0.3, entity.getZ()));
        }

        cleanupStaleEntries(now);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        FancyDamageRenderer.render(event.getMatrix(), event.getDelta());
    }

    private void enqueueDamageIndicator(Text originalText, Vec3d targetPos) {
        Matcher matcher = DAMAGE_TEXT_PATTERN.matcher(originalText.getString());
        if (!matcher.matches()) {
            return;
        }

        long damage;
        try {
            damage = Long.parseLong(matcher.group(2).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return;
        }

        int originalColor = extractColorFromText(originalText);
        Text displayText = compactNumbers.isToggled()
                ? FancyDamageRenderer.createCompactDamageText(originalText, damage, renderOriginalSymbol.isToggled(), criticalGradient.isToggled())
                : originalText;

        int finalColor = colorMode.getValue() == 0
                ? originalColor
                : FancyDamageRenderer.generatePaletteColor(damage);

        FancyDamageRenderer.addDamageNumber(damage, targetPos, displayText, finalColor);
    }

    private int extractColorFromText(Text text) {
        if (text == null) {
            return 0xFFFFFF;
        }

        Style style = text.getStyle();
        if (style.getColor() != null) {
            return style.getColor().getRgb();
        }

        for (Text sibling : text.getSiblings()) {
            Style siblingStyle = sibling.getStyle();
            if (siblingStyle.getColor() != null) {
                return siblingStyle.getColor().getRgb();
            }
        }

        return 0xFFFFFF;
    }

    private boolean isDamageIndicator(Entity entity) {
        if (!(entity instanceof ArmorStandEntity stand)) {
            return false;
        }

        if (!stand.hasCustomName() || stand.getCustomName() == null) {
            return false;
        }

        String text = stand.getCustomName().getString();
        return !text.isBlank() && DAMAGE_TEXT_PATTERN.matcher(text).matches();
    }

    private void cleanupStaleEntries(long now) {
        if (recentIndicators.isEmpty()) {
            return;
        }

        recentIndicators.entrySet().removeIf(entry -> now - entry.getValue().time > STALE_ENTRY_MS);

        if (recentIndicators.size() > MAX_TRACKED_ENTRIES) {
            recentIndicators.clear();
        }
    }

    private void restoreOriginalIndicatorVisibility() {
        if (mc.world == null || hiddenOriginalIndicators.isEmpty()) {
            hiddenOriginalIndicators.clear();
            return;
        }

        for (UUID uuid : hiddenOriginalIndicators) {
            Entity entity = mc.world.getEntity(uuid);
            if (entity instanceof ArmorStandEntity stand && stand.hasCustomName() && !stand.isCustomNameVisible()) {
                stand.setCustomNameVisible(true);
            }
        }

        hiddenOriginalIndicators.clear();
    }

    private record SeenEntry(String text, long time) {}
}