package com.revampes.Fault.modules.impl.other;

import com.aftertime.ratallofyou.utils.HotbarSwapUtils;
import com.revampes.Fault.events.impl.EntityJoinEvent;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ServerConnectBeginEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.LocationUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoGFS extends Module {
	private final ButtonSetting enderPearl = new ButtonSetting("Ender Pearl", false);
	private final ButtonSetting spiritLeap = new ButtonSetting("Spirit Leap", false);
	private final ButtonSetting superBoom = new ButtonSetting("Super Boom", false);
	private final SliderSetting worldLoadTicks = new SliderSetting("World Load Delay", 40.0, 20.0, 80.0, 1.0);
	private final SliderSetting getItemDelay = new SliderSetting("Get Item Delay", 40.0, 20.0, 80.0, 1.0);

	private int loadDelay = 0;
	private boolean worldLoaded = false;
	private boolean countdownStarted = false;
	private int globalDelay = 0;
	private boolean unknownAreaLastTick = true;

	public AutoGFS() {
		super("AutoGFS", category.Dungeon);
		this.registerSetting(this.enderPearl);
		this.registerSetting(this.spiritLeap);
		this.registerSetting(this.superBoom);
		this.registerSetting(this.getItemDelay);
		this.registerSetting(this.worldLoadTicks);
	}

	@Override
	public void onDisable() {
		this.loadDelay = 0;
		this.worldLoaded = false;
		this.countdownStarted = false;
		this.globalDelay = 0;
		this.unknownAreaLastTick = true;
	}

	@EventHandler
	private void onServerConnectBegin(ServerConnectBeginEvent event) {
		startWorldLoadCountdown();
		this.unknownAreaLastTick = true;
	}

	@EventHandler
	private void onEntityJoin(EntityJoinEvent event) {
		if (mc.player == null || event == null || event.entity == null) {
			return;
		}

		if (event.entity == mc.player || event.entity.getUuid().equals(mc.player.getUuid())) {
			startWorldLoadCountdown();
		}
	}

	@EventHandler
	public void onTick(PreUpdateEvent event) {
		if (mc.player == null || mc.world == null) {
			this.worldLoaded = false;
			return;
		}

		boolean unknownArea = "Unknown".equalsIgnoreCase(LocationUtils.getCurrentArea());
		if (unknownArea && !LocationUtils.isInDungeon()) {
			this.worldLoaded = false;
			this.unknownAreaLastTick = true;
			return;
		}

		if (this.unknownAreaLastTick && !this.countdownStarted && !this.worldLoaded) {
			startWorldLoadCountdown();
		}
		this.unknownAreaLastTick = unknownArea;

		if (this.countdownStarted) {
			this.worldLoaded = false;
			if (this.loadDelay > 0) {
				this.loadDelay--;
				return;
			}

			this.countdownStarted = false;
			this.worldLoaded = true;
		}

		if (!this.worldLoaded || mc.player.networkHandler == null) {
			return;
		}

		if (this.globalDelay > 0) {
			this.globalDelay--;
			return;
		}

		int itemDelayTicks = Math.max(1, (int) this.getItemDelay.getInput());

		boolean sentCommand = false;
		if (this.enderPearl.isToggled() && tryGetItem(16, "ENDER_PEARL", true)) {
			this.globalDelay = itemDelayTicks;
			sentCommand = true;
		}

		if (!sentCommand && this.spiritLeap.isToggled() && tryGetItem(16, "SPIRIT_LEAP", true)) {
			this.globalDelay = itemDelayTicks;
			sentCommand = true;
		}

		if (!sentCommand && this.superBoom.isToggled() && tryGetItem(64, "SUPERBOOM_TNT", true)) {
			this.globalDelay = itemDelayTicks;
		}
	}

	public static boolean tryGetItem(int maxStack, String sbId) {
		return tryGetItem(maxStack, sbId, false);
	}

	public static boolean tryGetItem(int maxStack, String sbId, boolean notExisting) {
		if (mc.player == null || mc.player.networkHandler == null) {
			return false;
		}

		int slot = getItemSlot(sbId);
		if (slot == -1) {
			if (notExisting) {
				mc.player.networkHandler.sendChatCommand("gfs " + sbId + " " + maxStack);
				return true;
			}
			return false;
		}

		ItemStack stack = mc.player.getInventory().getStack(slot);
		int count = stack.getCount();
		if (count > 0 && count < maxStack) {
			int missing = maxStack - count;
			mc.player.networkHandler.sendChatCommand("gfs " + sbId + " " + missing);
			return true;
		}

		return false;
	}

	private static int getItemSlot(String sbId) {
		if (mc.player == null || sbId == null || sbId.isBlank()) {
			return -1;
		}

		String targetId = normalizeSkyblockId(sbId);

		for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
			ItemStack stack = mc.player.getInventory().getStack(slot);
			if (stack == null || stack.isEmpty()) {
				continue;
			}

			if (matchesTargetItem(stack, targetId)) {
				return slot;
			}
		}

		return -1;
	}

	private static boolean matchesTargetItem(ItemStack stack, String targetId) {
		String skyblockId = HotbarSwapUtils.getSkyblockID(stack);
		if (skyblockId != null && normalizeSkyblockId(skyblockId).equals(targetId)) {
			return true;
		}

		String parsedId = Utils.getCustomDataIId(stack.toString());
		if (parsedId != null && !parsedId.isBlank() && normalizeSkyblockId(parsedId).equals(targetId)) {
			return true;
		}

		return "ENDER_PEARL".equals(targetId) && stack.getItem() == Items.ENDER_PEARL;
	}

	private static String normalizeSkyblockId(String id) {
		if (id == null) {
			return "";
		}
		return id.trim().toUpperCase().replace(' ', '_');
	}

	private void startWorldLoadCountdown() {
		this.countdownStarted = true;
		this.worldLoaded = false;
		this.loadDelay = Math.max(0, (int) this.worldLoadTicks.getInput());
	}

	public ButtonSetting getEnderPearl() {
		return this.enderPearl;
	}

	public ButtonSetting getSpiritLeap() {
		return this.spiritLeap;
	}

	public ButtonSetting getSuperBoom() {
		return this.superBoom;
	}

	public SliderSetting getWorldLoadTicks() {
		return this.worldLoadTicks;
	}

	public SliderSetting getGetItemDelay() {
		return this.getItemDelay;
	}

	public int getLoadDelay() {
		return this.loadDelay;
	}

	public boolean isWorldLoaded() {
		return this.worldLoaded;
	}

	public boolean isCountdownStarted() {
		return this.countdownStarted;
	}

	public int getGlobalDelay() {
		return this.globalDelay;
	}
}
