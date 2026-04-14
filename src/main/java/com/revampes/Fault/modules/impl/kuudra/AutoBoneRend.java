package com.revampes.Fault.modules.impl.kuudra;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.aftertime.ratallofyou.utils.HotbarSwapUtils;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.Utils;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AutoBoneRend extends Module {
	private static final String BONEMERANG_ID = "BONE_BOOMERANG";
	private static final String ATOMSPLIT_ID = "ATOMSPLIT_KATANA";
	private static final String BONEMERANG_TOKEN = "bonemerang";
	private static final String ATOMSPLIT_TOKEN = "atomsplit katana";
	private static final String ELEGANT_TUXEDO_TOKEN = "elegant tuxedo";

	private static final int WARDROBE_TARGET_ROW = 5;
	private static final int WARDROBE_OPEN_TIMEOUT_TICKS = 80;
	private static final int EXTRA_DELAY_MIN_TICKS = 3;
	private static final int EXTRA_DELAY_MAX_TICKS = 5;

	private final SliderSetting katanaSwapDelayTicks = new SliderSetting("Katana Swap Delay", "ticks", 2.0, 0.0, 20.0, 1.0);
	private final SliderSetting wardrobeClickDelayMs = new SliderSetting("Wardrobe Click Delay", "ms", 150.0, 0.0, 2000.0, 25.0);
	private final SliderSetting postWardrobeDelaySeconds = new SliderSetting("Post Wardrobe Delay", "s", 0.35, 0.0, 3.0, 0.05);
	private final ButtonSetting autoFindColumn = new ButtonSetting("Auto Find Column", true);
	private final SliderSetting presetColumn = new SliderSetting("Preset Column", 1.0, 1.0, 9.0, 1.0);

	private Stage stage = Stage.IDLE;
	private int stageTicks = 0;
	private long wardrobeOpenedAtMs = -1L;
	private long wardrobeClickDelayWithExtraMs = -1L;

	public AutoBoneRend() {
		super("Auto Bone Rend", "Swaps to Atomsplit, opens wardrobe, equips Elegant Tuxedo row and throws Bonemerang.", category.Kuudra);

		registerSetting(katanaSwapDelayTicks);
		registerSetting(wardrobeClickDelayMs);
		registerSetting(postWardrobeDelaySeconds);
		registerSetting(autoFindColumn);
		registerSetting(presetColumn);

		presetColumn.setVisibilityCondition(() -> !autoFindColumn.isToggled());
	}

	@Override
	public void onDisable() {
		resetState();
	}

	@EventHandler
	public void onSendPacket(SendPacketEvent event) {
		if (!Utils.nullCheck() || mc.player == null || mc.player.networkHandler == null) {
			return;
		}

		if (stage != Stage.IDLE || mc.currentScreen != null) {
			return;
		}

		if (!(event.getPacket() instanceof PlayerInteractItemC2SPacket)) {
			return;
		}

		ItemStack heldStack = mc.player.getMainHandStack();
		if (!isBonemerang(heldStack)) {
			return;
		}

		if (findBonemerangHotbarSlot() == -1 || findAtomsplitHotbarSlot() == -1) {
			return;
		}

		stage = Stage.WAIT_KATANA_SWAP;
		stageTicks = withRandomExtraTicks((int) katanaSwapDelayTicks.getInput());
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		if (event == null) {
			return;
		}

		if (mc.player == null || mc.world == null) {
			resetState();
			return;
		}

		switch (stage) {
			case WAIT_KATANA_SWAP -> tickWaitKatanaSwap();
			case WAIT_WARDROBE -> tickWaitWardrobe();
			case WAIT_BONEMERANG_THROW -> tickWaitBonemerangThrow();
			default -> {
			}
		}
	}

	private void tickWaitKatanaSwap() {
		if (stageTicks > 0) {
			stageTicks--;
			return;
		}

		int katanaSlot = findAtomsplitHotbarSlot();
		if (katanaSlot == -1 || !selectHotbarSlot(katanaSlot) || mc.player.networkHandler == null) {
			resetState();
			return;
		}

		mc.player.networkHandler.sendChatCommand("wd");
		stage = Stage.WAIT_WARDROBE;
		stageTicks = WARDROBE_OPEN_TIMEOUT_TICKS;
		wardrobeOpenedAtMs = -1L;
		wardrobeClickDelayWithExtraMs = -1L;
	}

	private void tickWaitWardrobe() {
		GenericContainerScreenHandler wardrobe = getWardrobeHandler();
		if (wardrobe == null) {
			wardrobeOpenedAtMs = -1L;
			wardrobeClickDelayWithExtraMs = -1L;
			if (stageTicks > 0) {
				stageTicks--;
				return;
			}

			resetState();
			return;
		}

		if (wardrobeOpenedAtMs < 0L) {
			wardrobeOpenedAtMs = System.currentTimeMillis();
			wardrobeClickDelayWithExtraMs = Math.max(0L, (long) wardrobeClickDelayMs.getInput()) + ticksToMillis(randomExtraTicks());
		}

		if (System.currentTimeMillis() - wardrobeOpenedAtMs < wardrobeClickDelayWithExtraMs) {
			return;
		}

		int column = autoFindColumn.isToggled() ? findElegantTuxedoColumn(wardrobe) : clampToColumn((int) presetColumn.getInput());
		if (column == -1) {
			resetState();
			return;
		}

		int clickSlot = getWardrobeTargetSlot(wardrobe, column);
		if (clickSlot == -1) {
			resetState();
			return;
		}

		if (!clickSlot(wardrobe.syncId, clickSlot)) {
			return;
		}

		closeCurrentScreen();
		stage = Stage.WAIT_BONEMERANG_THROW;
		stageTicks = withRandomExtraTicks((int) Math.round(postWardrobeDelaySeconds.getInput() * 20.0));
	}

	private void tickWaitBonemerangThrow() {
		if (mc.currentScreen != null) {
			closeCurrentScreen();
			return;
		}

		if (stageTicks > 0) {
			stageTicks--;
			return;
		}

		int boneSlot = findBonemerangHotbarSlot();
		if (boneSlot == -1 || !selectHotbarSlot(boneSlot)) {
			resetState();
			return;
		}

		Utils.leftClick();
		resetState();
	}

	private GenericContainerScreenHandler getWardrobeHandler() {
		if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
			return null;
		}

		String cleanTitle = Utils.stripColor(screen.getTitle().getString()).toLowerCase(Locale.ROOT);
		if (!cleanTitle.contains("wardrobe")) {
			return null;
		}

		if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
			return null;
		}

		return handler;
	}

	private int findElegantTuxedoColumn(GenericContainerScreenHandler wardrobe) {
		int windowSize = wardrobe.getRows() * 9;
		int limit = Math.min(windowSize, wardrobe.slots.size());

		for (int slot = 0; slot < limit; slot++) {
			ItemStack stack = wardrobe.slots.get(slot).getStack();
			if (isElegantTuxedo(stack)) {
				return clampToColumn((slot % 9) + 1);
			}
		}

		return -1;
	}

	private int getWardrobeTargetSlot(GenericContainerScreenHandler wardrobe, int column) {
		int rows = wardrobe.getRows();
		int normalizedColumn = clampToColumn(column);
		if (rows < WARDROBE_TARGET_ROW || normalizedColumn == -1) {
			return -1;
		}

		int slot = (WARDROBE_TARGET_ROW - 1) * 9 + (normalizedColumn - 1);
		return slot < rows * 9 ? slot : -1;
	}

	private boolean clickSlot(int syncId, int slot) {
		if (mc.player == null || mc.interactionManager == null) {
			return false;
		}

		mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
		return true;
	}

	private boolean selectHotbarSlot(int slot) {
		if (mc.player == null || slot < 0 || slot > 8) {
			return false;
		}

		mc.player.getInventory().setSelectedSlot(slot);
		if (mc.player.networkHandler != null) {
			mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
		}

		return true;
	}

	private int findBonemerangHotbarSlot() {
		return findHotbarSlot(this::isBonemerang);
	}

	private int findAtomsplitHotbarSlot() {
		return findHotbarSlot(this::isAtomsplit);
	}

	private int findHotbarSlot(StackMatcher matcher) {
		if (mc.player == null) {
			return -1;
		}

		for (int slot = 0; slot <= 8; slot++) {
			ItemStack stack = mc.player.getInventory().getStack(slot);
			if (matcher.matches(stack)) {
				return slot;
			}
		}

		return -1;
	}

	private boolean isBonemerang(ItemStack stack) {
		return matchesSkyblockId(stack, BONEMERANG_ID) || containsNameToken(stack, BONEMERANG_TOKEN);
	}

	private boolean isAtomsplit(ItemStack stack) {
		return matchesSkyblockId(stack, ATOMSPLIT_ID) || containsNameToken(stack, ATOMSPLIT_TOKEN);
	}

	private boolean isElegantTuxedo(ItemStack stack) {
		return containsNameToken(stack, ELEGANT_TUXEDO_TOKEN);
	}

	private boolean containsNameToken(ItemStack stack, String token) {
		if (stack == null || stack.isEmpty() || token == null || token.isBlank()) {
			return false;
		}

		String cleanName = Utils.stripColor(stack.getName().getString()).toLowerCase(Locale.ROOT);
		return cleanName.contains(token);
	}

	private boolean matchesSkyblockId(ItemStack stack, String expectedId) {
		if (stack == null || stack.isEmpty() || expectedId == null || expectedId.isBlank()) {
			return false;
		}

		String target = normalizeSkyblockId(expectedId);

		String fromHotbarUtils = normalizeSkyblockId(HotbarSwapUtils.getSkyblockID(stack));
		if (target.equals(fromHotbarUtils)) {
			return true;
		}

		String fromToString = normalizeSkyblockId(Utils.getCustomDataIId(stack.toString()));
		if (target.equals(fromToString)) {
			return true;
		}

		String fromComponents = normalizeSkyblockId(Utils.getCustomDataIId(stack.getComponents().toString()));
		return target.equals(fromComponents);
	}

	private String normalizeSkyblockId(String rawId) {
		if (rawId == null) {
			return "";
		}

		return rawId.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private int clampToColumn(int rawColumn) {
		if (rawColumn < 1 || rawColumn > 9) {
			return -1;
		}
		return rawColumn;
	}

	private void closeCurrentScreen() {
		if (mc.player != null) {
			mc.player.closeHandledScreen();
		}
	}

	private int withRandomExtraTicks(int baseTicks) {
		return Math.max(0, baseTicks) + randomExtraTicks();
	}

	private int randomExtraTicks() {
		return ThreadLocalRandom.current().nextInt(EXTRA_DELAY_MIN_TICKS, EXTRA_DELAY_MAX_TICKS + 1);
	}

	private long ticksToMillis(int ticks) {
		return Math.max(0, ticks) * 50L;
	}

	private void resetState() {
		stage = Stage.IDLE;
		stageTicks = 0;
		wardrobeOpenedAtMs = -1L;
		wardrobeClickDelayWithExtraMs = -1L;
	}

	private enum Stage {
		IDLE,
		WAIT_KATANA_SWAP,
		WAIT_WARDROBE,
		WAIT_BONEMERANG_THROW
	}

	@FunctionalInterface
	private interface StackMatcher {
		boolean matches(ItemStack stack);
	}
}
