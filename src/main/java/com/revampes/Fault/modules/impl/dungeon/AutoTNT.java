package com.revampes.Fault.modules.impl.dungeon;

import com.aftertime.ratallofyou.utils.HotbarSwapUtils;
import com.revampes.Fault.events.impl.MouseButtonEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.KeyAction;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class AutoTNT extends Module {
	private static final String SUPERBOOM_ID = "SUPERBOOM_TNT";
	private static final String INFINITYBOOM_ID = "INFINITYBOOM_TNT";

	private final ButtonSetting fromInv = new ButtonSetting("From Inv", true);

	public AutoTNT() {
		super("AutoTNT", "Swap to boom TNT and click cracked bricks instantly.", category.Dungeon);
		this.registerSetting(fromInv);
	}

	@EventHandler
	public void onMouseButton(MouseButtonEvent event) {
		if (!isTriggerClick(event)) {
			return;
		}

		if (!Utils.nullCheck() || mc.currentScreen != null || mc.interactionManager == null) {
			return;
		}

		if (!DungeonUtils.isInDungeon()) {
			return;
		}

		if (getTargetedCrackedBricks() == null) {
			return;
		}

		int originalHotbarSlot = mc.player.getInventory().getSelectedSlot();
		if (tryBoomClick(originalHotbarSlot)) {
			event.cancel();
		}
	}

	private boolean isTriggerClick(MouseButtonEvent event) {
		if (event == null || event.action != KeyAction.Press) {
			return false;
		}

		return event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
	}

	private BlockHitResult getTargetedCrackedBricks() {
		HitResult hit = mc.crosshairTarget;
		if (!(hit instanceof BlockHitResult blockHit)) {
			return null;
		}

		BlockState state = mc.world.getBlockState(blockHit.getBlockPos());
		return state.isOf(Blocks.CRACKED_STONE_BRICKS) ? blockHit : null;
	}

	private boolean tryBoomClick(int originalHotbarSlot) {
		int boomHotbarSlot = findBoomInHotbar();
		if (boomHotbarSlot != -1) {
			return clickWithHotbarBoom(originalHotbarSlot, boomHotbarSlot);
		}

		if (!fromInv.isToggled()) {
			return false;
		}

		int boomInventorySlot = findBoomInInventory();
		if (boomInventorySlot == -1) {
			return false;
		}

		return clickWithInventoryBoom(originalHotbarSlot, boomInventorySlot);
	}

	private boolean clickWithHotbarBoom(int originalHotbarSlot, int boomHotbarSlot) {
		if (!selectHotbarSlot(boomHotbarSlot)) {
			return false;
		}

		try {
			Utils.leftClick();
			return true;
		} finally {
			selectHotbarSlot(originalHotbarSlot);
		}
	}

	private boolean clickWithInventoryBoom(int originalHotbarSlot, int boomInventorySlot) {
		int containerSlotId = toContainerSlotId(boomInventorySlot);
		if (containerSlotId == -1) {
			return false;
		}

		if (!swapContainerSlotWithHotbar(containerSlotId, originalHotbarSlot)) {
			return false;
		}

		try {
			Utils.leftClick();
			return true;
		} finally {
			swapContainerSlotWithHotbar(containerSlotId, originalHotbarSlot);
			selectHotbarSlot(originalHotbarSlot);
		}
	}

	private boolean swapContainerSlotWithHotbar(int containerSlotId, int hotbarSlot) {
		if (mc.interactionManager == null || mc.player == null || mc.player.currentScreenHandler == null) {
			return false;
		}

		mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, containerSlotId, hotbarSlot, SlotActionType.SWAP, mc.player);
		return true;
	}

	private boolean selectHotbarSlot(int slot) {
		if (slot < 0 || slot > 8 || mc.player == null) {
			return false;
		}

		mc.player.getInventory().setSelectedSlot(slot);
		if (mc.player.networkHandler != null) {
			mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
		}
		return true;
	}

	private int findBoomInHotbar() {
		for (int slot = 0; slot <= 8; slot++) {
			ItemStack stack = mc.player.getInventory().getStack(slot);
			if (isBoom(stack)) {
				return slot;
			}
		}
		return -1;
	}

	private int findBoomInInventory() {
		for (int slot = 9; slot <= 35; slot++) {
			ItemStack stack = mc.player.getInventory().getStack(slot);
			if (isBoom(stack)) {
				return slot;
			}
		}
		return -1;
	}

	private boolean isBoom(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}

		String skyblockId = HotbarSwapUtils.getSkyblockID(stack);
		if (skyblockId != null) {
			String normalizedId = normalizeId(skyblockId);
			if (SUPERBOOM_ID.equals(normalizedId) || INFINITYBOOM_ID.equals(normalizedId)) {
				return true;
			}
		}

		String cleanName = Utils.stripColor(stack.getName().getString()).toLowerCase(Locale.ROOT);
		return cleanName.contains("superboom tnt") || cleanName.contains("infinityboom tnt");
	}

	private String normalizeId(String id) {
		if (id == null) {
			return "";
		}
		return id.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private int toContainerSlotId(int inventorySlot) {
		if (inventorySlot >= 9 && inventorySlot <= 35) {
			return inventorySlot;
		}
		if (inventorySlot >= 0 && inventorySlot <= 8) {
			return inventorySlot + 36;
		}
		return -1;
	}
}
