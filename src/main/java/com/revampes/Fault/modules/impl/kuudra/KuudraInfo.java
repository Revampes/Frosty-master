package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.Render2DEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.gui.screen.HudEditorScreen;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.Locale;

public class KuudraInfo extends Module {

	private final ButtonSetting highlightKuudra = new ButtonSetting("Highlight Kuudra", true);
	private final ColorSetting kuudraColor = new ColorSetting("Kuudra Color", new Color(255, 85, 85));
	private final ButtonSetting kuudraHPDisplay = new ButtonSetting("Kuudra HP", true);
	private final SliderSetting healthSize = new SliderSetting("Health Size", 4.0, 3.0, 8.0, 0.1);
	private final ButtonSetting scaledHealth = new ButtonSetting("Use Scaled", true);
	private final SliderSetting xPos = new SliderSetting("X Position", "%", 50, 0, 100, 1);
	private final SliderSetting yPos = new SliderSetting("Y Position", "%", 25, 0, 100, 1);

	public KuudraInfo() {
		super("Kuudra Info", category.Kuudra);
		this.registerSetting(highlightKuudra);
		this.registerSetting(kuudraColor);
		this.registerSetting(kuudraHPDisplay);
		this.registerSetting(healthSize);
		this.registerSetting(scaledHealth);
		this.registerSetting(xPos);
		this.registerSetting(yPos);

		kuudraColor.setVisibilityCondition(highlightKuudra::isToggled);
		healthSize.setVisibilityCondition(kuudraHPDisplay::isToggled);
		scaledHealth.setVisibilityCondition(kuudraHPDisplay::isToggled);
	}

	@Override
	public String getDesc() {
		return "Displays information about Kuudra entity itself.";
	}

	@EventHandler
	private void onRender3D(Render3DEvent event) {
		if (!KuudraUtils.isInKuudra()) return;

		MagmaCubeEntity kuudra = KuudraUtils.kuudraEntity;
		if (kuudra == null) return;

		if (highlightKuudra.isToggled()) {
			RenderUtils.drawBox(event.getMatrix(), kuudra.getBoundingBox(), kuudraColor.getColor(), 2.0);
		}

		if (kuudraHPDisplay.isToggled()) {
			Vec3d lookVec = kuudra.getRotationVec(event.getDelta());
			Vec3d textPos = new Vec3d(kuudra.getX(), kuudra.getY(), kuudra.getZ())
				.add(lookVec.multiply(13.0))
				.add(0.0, 10.0, 0.0);

			RenderUtils.draw3DText(
				event.getMatrix(),
				getCurrentHealthDisplay(kuudra.getHealth()),
				textPos,
				(float) healthSize.getInput()
			);
		}
	}

	@EventHandler
	private void onRender2D(Render2DEvent event) {
		boolean example = mc.currentScreen instanceof HudEditorScreen;
		if (!example && !KuudraUtils.isInKuudra()) return;

		String display;
		if (example) {
			display = "§a99.975M§7/§a240M§c❤";
		} else {
			MagmaCubeEntity kuudra = KuudraUtils.kuudraEntity;
			if (kuudra == null) return;
			display = getCurrentHealthDisplay(kuudra.getHealth());
		}

		int x = (int) ((mc.getWindow().getScaledWidth() * xPos.getInput()) / 100.0);
		int y = (int) ((mc.getWindow().getScaledHeight() * yPos.getInput()) / 100.0);
		event.drawContext.drawText(mc.textRenderer, display, x, y, -1, true);
	}

	private String getCurrentHealthDisplay(float kuudraHP) {
		String color;
		if (kuudraHP > 99000) {
			color = "§a";
		} else if (kuudraHP > 75000) {
			color = "§2";
		} else if (kuudraHP > 50000) {
			color = "§e";
		} else if (kuudraHP > 25000) {
			color = "§6";
		} else if (kuudraHP > 10000) {
			color = "§c";
		} else {
			color = "§4";
		}

		double health = kuudraHP / 1000.0;

		if (kuudraHP <= 25000 && scaledHealth.isToggled() && KuudraUtils.kuudraTier == 5) {
			return color + formatFixed(health * 9.6) + "M§7/§a240M§c❤";
		}

		return color + formatFixed(health) + "K§7/§a100k§c❤";
	}

	private String formatFixed(double value) {
		String out = String.format(Locale.US, "%.3f", value);
		while (out.contains(".") && out.endsWith("0")) {
			out = out.substring(0, out.length() - 1);
		}
		if (out.endsWith(".")) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}
}
