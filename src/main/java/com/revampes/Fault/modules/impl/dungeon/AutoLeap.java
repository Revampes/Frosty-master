package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.MouseButtonEvent;
import com.revampes.Fault.events.impl.PostUpdateEvent;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.mixin.HandledScreenAccessor;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.KeyAction;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AutoLeap extends Module {
	private static final int FIRST_LEAP_SLOT = 9;
	private static final int LAST_LEAP_SLOT = 17;
	private static final int NAME_COLOR = 0xFF3FCE3F;
	private static final int MAX_NAME_LENGTH = 5;
	private static final long LEAP_OPEN_TIMEOUT_MS = 2500L;

	private final ButtonSetting autoTpToMage = new ButtonSetting("Auto Tp To Mage", false);
	private final SliderSetting autoTpDelayMs = new SliderSetting("Auto Tp Delay", "ms", 1000.0, 100.0, 5000.0, 50.0);
	private final ButtonSetting spiritLeapName = new ButtonSetting("Spirit Leap Name", true);

	private final Map<Integer, String> shortNamesBySlot = new LinkedHashMap<>();
	private long lastClickAtMs = 0L;
	private boolean pendingAutoTpToMage = false;
	private long pendingAutoTpStartedAtMs = 0L;

	public AutoLeap() {
		super("AutoLeap", "Auto leap to mage and render leap names.", category.Dungeon);
		this.registerSetting(autoTpToMage);
		this.registerSetting(autoTpDelayMs);
		this.registerSetting(spiritLeapName);

		autoTpDelayMs.setVisibilityCondition(autoTpToMage::isToggled);
	}

	@Override
	public void onDisable() {
		shortNamesBySlot.clear();
		lastClickAtMs = 0L;
		clearPendingAutoTp();
	}

	@EventHandler
	public void onMouseButton(MouseButtonEvent event) {
		if (!autoTpToMage.isToggled() || event.action != KeyAction.Press) {
			return;
		}
		if (!DungeonUtils.isInDungeon() || mc.player == null || mc.interactionManager == null) {
			return;
		}
		if (mc.currentScreen != null) {
			return;
		}

		int attackKeyCode = mc.options.attackKey.getDefaultKey().getCode();
		if (event.button != attackKeyCode) {
			return;
		}
		if (!isLeapItem(mc.player.getMainHandStack())) {
			return;
		}

		long now = System.currentTimeMillis();
		long clickDelayMs = (long) autoTpDelayMs.getInput();
		if (now - lastClickAtMs < clickDelayMs) {
			return;
		}

		event.cancel();
		mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
		pendingAutoTpToMage = true;
		pendingAutoTpStartedAtMs = now;
	}

	@EventHandler
	public void onUpdate(PostUpdateEvent event) {
		if (!DungeonUtils.isInDungeon()) {
			shortNamesBySlot.clear();
			clearPendingAutoTp();
			return;
		}
		if (!autoTpToMage.isToggled()) {
			clearPendingAutoTp();
		}

		HandledScreen<?> screen = getOpenLeapScreen();
		GenericContainerScreenHandler handler = getLeapHandler(screen);
		if (screen == null || handler == null) {
			shortNamesBySlot.clear();
			isPendingAutoTpValid();
			return;
		}

		if (spiritLeapName.isToggled()) {
			cacheShortNames(handler);
		} else {
			shortNamesBySlot.clear();
		}

		if (isPendingAutoTpValid() && tryLeapToMage(handler)) {
			clearPendingAutoTp();
		}
	}

	@EventHandler
	public void onRenderScreen(RenderScreenEvent event) {
		if (!spiritLeapName.isToggled() || !DungeonUtils.isInDungeon()) {
			return;
		}

		HandledScreen<?> screen = getOpenLeapScreen();
		GenericContainerScreenHandler handler = getLeapHandler(screen);
		if (screen == null || handler == null || screen != event.screen) {
			return;
		}

		if (shortNamesBySlot.isEmpty()) {
			cacheShortNames(handler);
		}
		if (shortNamesBySlot.isEmpty()) {
			return;
		}

		int originX = 0;
		int originY = 0;
		if (screen instanceof HandledScreenAccessor accessor) {
			originX = accessor.getX();
			originY = accessor.getY();
		}

		for (Map.Entry<Integer, String> entry : shortNamesBySlot.entrySet()) {
			int slotIndex = entry.getKey();
			String label = entry.getValue();
			if (label == null || label.isBlank() || slotIndex < 0 || slotIndex >= handler.slots.size()) {
				continue;
			}

			Slot slot = handler.slots.get(slotIndex);
			int width = mc.textRenderer.getWidth(label);
			int x = originX + slot.x + (16 - width) / 2;
			int y = originY + slot.y + 4;
			event.context.drawTextWithShadow(mc.textRenderer, label, x, y, NAME_COLOR);
		}
	}

	private boolean tryLeapToMage(GenericContainerScreenHandler handler) {
		if (mc.player == null || mc.interactionManager == null) {
			return false;
		}

		long now = System.currentTimeMillis();
		long clickDelayMs = (long) autoTpDelayMs.getInput();
		if (now - lastClickAtMs < clickDelayMs) {
			return false;
		}

		Map<String, DungeonUtils.DungeonTeammate> teammateLookup = DungeonUtils.getDungeonTeammateLookup();
		if (teammateLookup.isEmpty()) {
			return false;
		}

		int maxSlotExclusive = Math.min(handler.slots.size(), LAST_LEAP_SLOT + 1);
		for (int slotIndex = FIRST_LEAP_SLOT; slotIndex < maxSlotExclusive; slotIndex++) {
			ItemStack stack = handler.slots.get(slotIndex).getStack();
			if (stack == null || stack.isEmpty() || isPlaceholder(stack)) {
				continue;
			}

			String name = normalizePlayerName(stack.getName().getString());
			if (name.isBlank()) {
				continue;
			}

			DungeonUtils.DungeonTeammate teammate = teammateLookup.get(name.toLowerCase(Locale.ROOT));
			if (teammate == null || teammate.dead() || teammate.classType() != DungeonUtils.DungeonClassType.MAGE) {
				continue;
			}

			mc.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
			lastClickAtMs = now;
			return true;
		}

		return false;
	}

	private void cacheShortNames(GenericContainerScreenHandler handler) {
		shortNamesBySlot.clear();

		int maxSlotExclusive = Math.min(handler.slots.size(), LAST_LEAP_SLOT + 1);
		for (int slotIndex = FIRST_LEAP_SLOT; slotIndex < maxSlotExclusive; slotIndex++) {
			ItemStack stack = handler.slots.get(slotIndex).getStack();
			if (stack == null || stack.isEmpty() || isPlaceholder(stack)) {
				continue;
			}

			String name = normalizePlayerName(stack.getName().getString());
			if (!isLikelyPlayerName(name)) {
				continue;
			}

			shortNamesBySlot.put(slotIndex, abbreviate(name));
		}
	}

	private HandledScreen<?> getOpenLeapScreen() {
		if (mc == null || mc.currentScreen == null) {
			return null;
		}

		if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
			return null;
		}

		String title = Utils.stripColor(screen.getTitle().getString()).trim();
		if (!title.equalsIgnoreCase("Spirit Leap") && !title.equalsIgnoreCase("Teleport to Player")) {
			return null;
		}

		return screen;
	}

	private GenericContainerScreenHandler getLeapHandler(HandledScreen<?> screen) {
		if (screen == null) {
			return null;
		}
		if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
			return null;
		}
		return handler;
	}

	private boolean isPlaceholder(ItemStack stack) {
		String itemId = Registries.ITEM.getId(stack.getItem()).toString();
		return itemId.contains("stained_glass_pane");
	}

	private boolean isPendingAutoTpValid() {
		if (!pendingAutoTpToMage) {
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - pendingAutoTpStartedAtMs > LEAP_OPEN_TIMEOUT_MS) {
			clearPendingAutoTp();
			return false;
		}

		return true;
	}

	private void clearPendingAutoTp() {
		pendingAutoTpToMage = false;
		pendingAutoTpStartedAtMs = 0L;
	}

	private boolean isLeapItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}

		String rawName = stack.getName() == null ? "" : stack.getName().getString();
		String lowerName = Utils.stripColor(rawName).toLowerCase(Locale.ROOT);
		return lowerName.contains("spirit leap") || lowerName.contains("infinileap") || lowerName.contains("infinite leap");
	}

	private String normalizePlayerName(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}

		String stripped = Utils.stripColor(raw).trim();
		if (stripped.isBlank()) {
			return "";
		}

		return stripped.replaceAll("[^A-Za-z0-9_]", "");
	}

	private boolean isLikelyPlayerName(String name) {
		return name != null && name.matches("[A-Za-z0-9_]{1,16}");
	}

	private String abbreviate(String name) {
		if (name == null) {
			return "";
		}
		return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
	}
}
