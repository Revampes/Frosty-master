package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.InputSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.LocationUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoCroesus extends Module {
	private static final long PRICE_CACHE_MAX_AGE_MS = 1_800_000L;
	private static final double AURA_RANGE = 3.5D;

	private static final Pattern COST_PATTERN = Pattern.compile("^([\\d,]+)\\s+Coins$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BOOK_PATTERN = Pattern.compile("^Enchanted Book \\((Ultimate )?([A-Za-z' ]+) ([IVXLCDM]+|\\d+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ESSENCE_PATTERN = Pattern.compile("^([A-Za-z]+) Essence x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern REWARD_PREFIX_PATTERN = Pattern.compile("^(?:[A-Z]+\\s+REWARD!\\s*)+");
	private static final Pattern TRAILING_QUANTITY_PATTERN = Pattern.compile("^(.*) x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern FLOOR_PATTERN = Pattern.compile("Floor\\s+([IVXLCDM]+|\\d+)", Pattern.CASE_INSENSITIVE);

	private static final Map<String, String> ITEM_REPLACEMENTS = createItemReplacements();

	private final SliderSetting clickDelay = new SliderSetting("Click Delay", "ms", 300.0, 100.0, 1000.0, 25.0);
	private final ButtonSetting chestKeys = new ButtonSetting("Use chest keys", false);
	private final SliderSetting chestKeyMinProfit = new SliderSetting("Key min profit", "m", 0.5, 0.0, 2.0, 0.01);
	private final ButtonSetting kismets = new ButtonSetting("Use Kismet", false);
	private final SliderSetting kismetsMinProfit = new SliderSetting("Kismet min profit", "m", 2.0, 1.0, 3.5, 0.05);
	private final InputSetting kismetFloors = new InputSetting("Kismet Floors", 64, "F1,F2,F3,F4,F5,F6,F7,M1,M2,M3,M4,M5,M6,M7");
	private final ButtonSetting startButton = new ButtonSetting("Start", this::start);
	private final ButtonSetting refreshPricesButton = new ButtonSetting("Refresh Prices", this::refreshPrices);

	private boolean running = false;
	private Action action = Action.IDLE;
	private boolean kismetting = false;
	private int currentPage = 1;
	private long nextClickAtMs = 0L;
	private Reward pendingReward = null;
	private boolean ignoreNextClosePacket = false;

	public AutoCroesus() {
		super("AutoCroesus", "Automatically opens profitable Croesus chests.", category.Dungeon);

		registerSetting(clickDelay);
		registerSetting(chestKeys);
		registerSetting(chestKeyMinProfit);
		registerSetting(kismets);
		registerSetting(kismetsMinProfit);
		registerSetting(kismetFloors);
		registerSetting(startButton);
		registerSetting(refreshPricesButton);

		chestKeyMinProfit.setVisibilityCondition(chestKeys::isToggled);
		kismetsMinProfit.setVisibilityCondition(kismets::isToggled);
		kismetFloors.setVisibilityCondition(kismets::isToggled);

		CroesusLoader.load();
	}

	@Override
	public String getDesc() {
		return "Automatically claims profitable Croesus dungeon rewards.";
	}

	@Override
	public void onEnable() {
		start();
	}

	@Override
	public void onDisable() {
		resetState();
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		if (!running) {
			return;
		}

		if (mc.player == null || mc.world == null) {
			resetState();
			return;
		}

		if (!isInDungeonHub()) {
			modMessage(Formatting.RED + "Stopped because you left the Dungeon Hub.");
			resetState();
			return;
		}

		switch (action) {
			case CROESUS -> tickCroesus();
			case REWARDS -> tickRewards();
			case CHEST -> tickChest();
			default -> {
			}
		}
	}

	@EventHandler
	public void onSendPacket(SendPacketEvent event) {
		if (!(event.getPacket() instanceof CloseHandledScreenC2SPacket)) {
			return;
		}

		if (ignoreNextClosePacket) {
			ignoreNextClosePacket = false;
			return;
		}

		if (running && action != Action.IDLE && isInDungeonHub()) {
			modMessage("Stopped.");
			resetState();
		}
	}

	public void start() {
		start(true);
	}

	public void start(boolean checkPrice) {
		if (!isEnabled()) {
			modMessage(Formatting.RED + "Module is not enabled.");
			return;
		}

		if (!isInDungeonHub()) {
			modMessage(Formatting.RED + "You must be in the Dungeon Hub.");
			return;
		}

		if (running) {
			modMessage("Already claiming.");
			return;
		}

		if (checkPrice && !CroesusPriceService.hasFreshData(PRICE_CACHE_MAX_AGE_MS)) {
			if (CroesusPriceService.isUpdating()) {
				modMessage("Price update already running...");
				return;
			}

			modMessage("Updating price data from API...");
			CroesusPriceService.updateAsync(() -> start(false));
			return;
		}

		running = true;
		action = Action.CROESUS;
		pendingReward = null;
		kismetting = false;
		nextClickAtMs = 0L;

		if (!clickCroesus()) {
			modMessage(Formatting.RED + "Failed to click Croesus. Face the NPC and try again.");
			resetState();
		}
	}

	private void refreshPrices() {
		if (CroesusPriceService.isUpdating()) {
			modMessage("Price update already running...");
			return;
		}

		modMessage("Refreshing price data...");
		CroesusPriceService.updateAsync(() -> modMessage("Price update finished."));
	}

	private void resetState() {
		running = false;
		action = Action.IDLE;
		kismetting = false;
		currentPage = 1;
		pendingReward = null;
		nextClickAtMs = 0L;
		ignoreNextClosePacket = false;
	}

	private void tickCroesus() {
		if (isCroesusScreen()) {
			if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu)) {
				return;
			}

			currentPage = getPage(menu.slots);

			if (tryOpenRun(menu)) {
				return;
			}

			if (menu.slots.size() > 53) {
				ItemStack nextArrow = menu.slots.get(53).getStack();
				if (nextArrow.isOf(Items.ARROW)) {
					clickSlot(menu.syncId, 53);
					return;
				}
			}

			modMessage("All chests looted.");
			resetState();
			closeCurrentScreen();
			return;
		}

		if (mc.currentScreen != null) {
			return;
		}

		clickCroesus();
	}

	private void tickRewards() {
		if (!isRewardsScreen()) {
			return;
		}

		if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu)) {
			return;
		}

		List<Reward> rewards = new ArrayList<>();
		int maxSlots = Math.min(menu.slots.size(), 46);
		for (int i = 0; i < maxSlots; i++) {
			Reward reward = parseRewardSlot(menu.slots.get(i));
			if (reward != null) {
				rewards.add(reward);
			}
		}

		if (rewards.isEmpty()) {
			return;
		}

		String title = Utils.stripColor(mc.currentScreen.getTitle().getString());
		RunType runType = RunType.findByTitle(title);
		String floorToken = parseFloorToken(title, runType);

		Reward bedrock = rewards.stream().filter(r -> r.chest.type == ChestType.BEDROCK).findFirst().orElse(null);
		Reward alwaysBuyReward = rewards.stream().filter(r -> r.alwaysBuy).findFirst().orElse(null);

		if (bedrock != null
			&& kismets.isToggled()
			&& isKismetAvailable(menu)
			&& isKismetFloorEnabled(floorToken)
			&& bedrock.chest.profit < kismetsMinProfit.getInput() * 1_000_000.0) {

			kismetting = true;
			action = Action.CHEST;
			pendingReward = bedrock;

			if (isRewardSlotValid(bedrock.slot)) {
				clickSlot(menu.syncId, bedrock.slot);
			} else {
				modMessage(Formatting.RED + "Invalid chest slot: " + bedrock.slot);
				resetState();
			}
			return;
		}

		Reward best = alwaysBuyReward != null ? alwaysBuyReward : getBestProfit(rewards);
		if (best == null) {
			modMessage(Formatting.RED + "No valid reward was found.");
			resetState();
			return;
		}

		if (!isRewardSlotValid(best.slot)) {
			modMessage(Formatting.RED + "Invalid chest slot: " + best.slot);
			resetState();
			return;
		}

		modMessage("Claiming " + best.name + Formatting.RESET + " chest | Profit: " + formatProfit(best.chest.profit));
		pendingReward = best;
		action = Action.CHEST;
		clickSlot(menu.syncId, best.slot);
	}

	private void tickChest() {
		if (!isChestScreen()) {
			return;
		}

		if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu)) {
			return;
		}

		if (kismetting) {
			kismetting = false;

			if (menu.slots.size() <= 50) {
				modMessage(Formatting.RED + "Kismet slot is not available.");
				resetState();
				return;
			}

			ItemStack kismetStack = menu.slots.get(50).getStack();
			if (containsLore(kismetStack, "Bring a Kismet Feather")) {
				modMessage(Formatting.RED + "No Kismet Feathers available.");
				resetState();
				closeCurrentScreen();
				return;
			}

			action = Action.REWARDS;
			clickSlot(menu.syncId, 50);
			return;
		}

		if (menu.slots.size() <= 31) {
			modMessage(Formatting.RED + "Open chest button is not available.");
			resetState();
			return;
		}

		ItemStack openButton = menu.slots.get(31).getStack();
		boolean requiresKey = containsLore(openButton, "Dungeon Chest Key");

		if (requiresKey && !chestKeys.isToggled()) {
			skipChest("Skipping key chest (Use chest keys disabled).");
			return;
		}

		if (requiresKey && pendingReward != null) {
			double minProfit = chestKeyMinProfit.getInput() * 1_000_000.0;
			if (pendingReward.chest.profit < minProfit) {
				skipChest("Skipping key chest (profit below threshold).");
				return;
			}
		}

		clickSlot(menu.syncId, 31);

		if (pendingReward != null) {
			CroesusLoader.addRunLog(pendingReward.chest);
		}

		pendingReward = null;
		action = Action.CROESUS;
		nextClickAtMs = System.currentTimeMillis() + clickDelayMs() * 2L;
	}

	private boolean clickCroesus() {
		if (!isClickReady() || mc.player == null || mc.world == null || mc.interactionManager == null) {
			return false;
		}

		PlayerEntity croesus = findCroesus();
		if (croesus == null) {
			return false;
		}

		if (croesus.squaredDistanceTo(mc.player) > 16.0) {
			modMessage(Formatting.RED + "Croesus is too far away.");
			return false;
		}

		mc.interactionManager.attackEntity(mc.player, croesus);
		mc.player.swingHand(Hand.MAIN_HAND);
		setClickDelay();
		return true;
	}

	private PlayerEntity findCroesus() {
		if (mc.player == null || mc.world == null) {
			return null;
		}

				Vec3d eyePos = new Vec3d(
					mc.player.getX(),
					mc.player.getY() + mc.player.getStandingEyeHeight(),
					mc.player.getZ()
				);
		Box box = new Box(eyePos, eyePos).expand(AURA_RANGE, AURA_RANGE, AURA_RANGE);

		List<PlayerEntity> playerMatches = mc.world.getEntitiesByClass(
			PlayerEntity.class,
			box,
			entity -> "Croesus".equalsIgnoreCase(Utils.stripColor(entity.getName().getString()))
		);

		if (!playerMatches.isEmpty()) {
			return playerMatches.stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player))).orElse(null);
		}

		List<ArmorStandEntity> stands = mc.world.getEntitiesByClass(
			ArmorStandEntity.class,
			box,
			stand -> Utils.stripColor(stand.getName().getString()).contains("Croesus")
		);

		if (stands.isEmpty()) {
			return null;
		}

		ArmorStandEntity closestStand = stands.stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player))).orElse(null);
		if (closestStand == null) {
			return null;
		}

		List<PlayerEntity> linkedPlayers = mc.world.getEntitiesByClass(
			PlayerEntity.class,
			box,
			entity -> entity.squaredDistanceTo(closestStand) <= 0.01
		);

		return linkedPlayers.stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player))).orElse(null);
	}

	private boolean tryOpenRun(GenericContainerScreenHandler menu) {
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getStack();
			if (!stack.isOf(Items.PLAYER_HEAD)) {
				continue;
			}

			RunType runType = RunType.findByDisplayName(stack.getName().getString());
			if (runType == RunType.NONE) {
				continue;
			}

			List<String> lore = getCleanLore(stack);
			boolean hasUnopened = lore.stream().anyMatch(line -> line.contains("No chests opened yet!"));
			if (!hasUnopened) {
				continue;
			}

			action = Action.REWARDS;
			clickSlot(menu.syncId, slot.id);
			return true;
		}

		return false;
	}

	private Reward parseRewardSlot(Slot slot) {
		ItemStack stack = slot.getStack();
		if (!stack.isOf(Items.PLAYER_HEAD)) {
			return null;
		}

		ChestType chestType = ChestType.fromDisplayName(stack.getName().getString());
		if (chestType == ChestType.NONE) {
			return null;
		}

		List<String> lore = getCleanLore(stack);
		if (lore.size() < 3) {
			return null;
		}

		int costIndex = -1;
		for (int i = 0; i < lore.size(); i++) {
			if ("Cost".equalsIgnoreCase(lore.get(i))) {
				costIndex = i;
				break;
			}
		}

		if (costIndex <= 1 || costIndex + 1 >= lore.size()) {
			return null;
		}

		ChestInfo chestInfo = new ChestInfo();
		chestInfo.type = chestType;
		chestInfo.cost = parseCost(lore.get(costIndex + 1));

		boolean alwaysBuy = false;

		for (int i = 1; i < costIndex; i++) {
			String line = lore.get(i);
			ChestItem item = parseItemLine(line);
			if (item == null) {
				continue;
			}

			double price = getSellPrice(item.id);
			chestInfo.value += price * item.quantity;
			chestInfo.items.add(item);

			if (CroesusLoader.getAlwaysBuy().contains(item.id)) {
				alwaysBuy = true;
			}
		}

		chestInfo.profit = chestInfo.value - chestInfo.cost;

		Reward reward = new Reward(chestInfo, alwaysBuy);
		reward.slot = slot.id;
		reward.name = Utils.stripColor(stack.getName().getString());
		return reward;
	}

	private ChestItem parseItemLine(String line) {
		String clean = Utils.stripColor(line == null ? "" : line).trim();
		if (clean.isEmpty()) {
			return null;
		}

		clean = REWARD_PREFIX_PATTERN.matcher(clean).replaceFirst("").trim();
		if (clean.isEmpty()) {
			return null;
		}

		Matcher bookMatcher = BOOK_PATTERN.matcher(clean);
		if (bookMatcher.matches()) {
			boolean ultimate = bookMatcher.group(1) != null;
			String enchantName = bookMatcher.group(2)
				.toUpperCase(Locale.ROOT)
				.replace(" ", "_")
				.replace("'", "");
			int level = parseNumeral(bookMatcher.group(3));
			if (level <= 0) {
				return null;
			}

			String id = "ENCHANTMENT_" + (ultimate ? "ULTIMATE_" : "") + enchantName + "_" + level;
			id = id.replace("ULTIMATE_ULTIMATE_", "ULTIMATE_");
			return new ChestItem(id, 1);
		}

		Matcher essenceMatcher = ESSENCE_PATTERN.matcher(clean);
		if (essenceMatcher.matches()) {
			String type = essenceMatcher.group(1).toUpperCase(Locale.ROOT);
			int amount = parseIntSafe(essenceMatcher.group(2), 1);
			return new ChestItem("ESSENCE_" + type, Math.max(1, amount));
		}

		int quantity = 1;
		Matcher quantityMatcher = TRAILING_QUANTITY_PATTERN.matcher(clean);
		if (quantityMatcher.matches()) {
			clean = quantityMatcher.group(1).trim();
			quantity = Math.max(1, parseIntSafe(quantityMatcher.group(2), 1));
		}

		String replacement = ITEM_REPLACEMENTS.get(clean);
		if (replacement != null) {
			return new ChestItem(replacement, quantity);
		}

		String id = CroesusPriceService.findItemIdByName(clean);
		if (id == null || id.isBlank()) {
			id = toIdFallback(clean);
		}

		if (id == null || id.isBlank()) {
			return null;
		}

		return new ChestItem(id, quantity);
	}

	private double parseCost(String rawCost) {
		String clean = Utils.stripColor(rawCost == null ? "" : rawCost).trim();
		if (clean.equalsIgnoreCase("FREE") || clean.equalsIgnoreCase("aFREE") || clean.equalsIgnoreCase("§aFREE")) {
			return 0.0;
		}

		Matcher matcher = COST_PATTERN.matcher(clean);
		if (!matcher.find()) {
			return 0.0;
		}

		return parseDoubleSafe(matcher.group(1).replace(",", ""), 0.0);
	}

	private double getSellPrice(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return 0.0;
		}

		if (CroesusLoader.getWorthless().contains(itemId)) {
			return 0.0;
		}

		double price = CroesusPriceService.getSellPrice(itemId);
		if (price > 0.0) {
			return price;
		}

		if (itemId.startsWith("STARRED_")) {
			return CroesusPriceService.getSellPrice(itemId.substring("STARRED_".length()));
		}

		return 0.0;
	}

	private Reward getBestProfit(List<Reward> rewards) {
		return rewards.stream().max(Comparator.comparingDouble(reward -> reward.chest.profit)).orElse(null);
	}

	private boolean isKismetAvailable(GenericContainerScreenHandler menu) {
		if (menu.slots.size() <= 32) {
			return false;
		}

		ItemStack modifiers = menu.slots.get(32).getStack();
		for (Text line : getLore(modifiers)) {
			String plain = Utils.stripColor(line.getString());
			if (plain.contains("Kismet Feather")) {
				return !hasStrikethrough(line);
			}
		}

		return false;
	}

	private boolean hasStrikethrough(Text line) {
		if (line.getStyle() != null && line.getStyle().isStrikethrough()) {
			return true;
		}

		for (Text sibling : line.getSiblings()) {
			if (hasStrikethrough(sibling)) {
				return true;
			}
		}

		return false;
	}

	private boolean isKismetFloorEnabled(String floor) {
		if (floor == null || floor.isBlank()) {
			return false;
		}

		String raw = kismetFloors.getValue();
		if (raw == null || raw.isBlank()) {
			return false;
		}

		String wanted = floor.trim().toUpperCase(Locale.ROOT);
		for (String token : raw.split(",")) {
			if (wanted.equals(token.trim().toUpperCase(Locale.ROOT))) {
				return true;
			}
		}

		return false;
	}

	private String parseFloorToken(String title, RunType runType) {
		if (title == null || runType == RunType.NONE) {
			return null;
		}

		Matcher matcher = FLOOR_PATTERN.matcher(title);
		if (!matcher.find()) {
			return null;
		}

		int floorIndex = parseNumeral(matcher.group(1));
		if (floorIndex <= 0) {
			return null;
		}

		String prefix = runType == RunType.MASTER_CATACOMBS ? "M" : "F";
		return prefix + floorIndex;
	}

	private boolean isInDungeonHub() {
		String area = LocationUtils.getCurrentArea();
		if (area == null) {
			return false;
		}

		return area.toLowerCase(Locale.ROOT).contains("dungeon hub");
	}

	private boolean isCroesusScreen() {
		return mc.currentScreen != null
			&& "Croesus".equalsIgnoreCase(Utils.stripColor(mc.currentScreen.getTitle().getString()));
	}

	private boolean isRewardsScreen() {
		if (mc.currentScreen == null) {
			return false;
		}

		return RunType.findByTitle(Utils.stripColor(mc.currentScreen.getTitle().getString())) != RunType.NONE;
	}

	private boolean isChestScreen() {
		if (mc.currentScreen == null) {
			return false;
		}

		String title = Utils.stripColor(mc.currentScreen.getTitle().getString());
		String[] split = title.split(" ");
		if (split.length == 0) {
			return false;
		}

		return ChestType.fromDisplayName(split[0]) != ChestType.NONE;
	}

	private int getPage(DefaultedList<Slot> slots) {
		if (slots == null || slots.isEmpty()) {
			return 1;
		}

		int nextPage = parseArrowPage(slots, 53);
		if (nextPage > 1) {
			return nextPage - 1;
		}

		int previousPage = parseArrowPage(slots, 45);
		if (previousPage > 0) {
			return previousPage + 1;
		}

		return 1;
	}

	private int parseArrowPage(DefaultedList<Slot> slots, int index) {
		if (slots.size() <= index) {
			return -1;
		}

		ItemStack stack = slots.get(index).getStack();
		if (!stack.isOf(Items.ARROW)) {
			return -1;
		}

		List<String> lore = getCleanLore(stack);
		if (lore.isEmpty()) {
			return -1;
		}

		Matcher matcher = Pattern.compile("(\\d+)").matcher(lore.get(0));
		if (!matcher.find()) {
			return -1;
		}

		return parseIntSafe(matcher.group(1), -1);
	}

	private void clickSlot(int windowId, int slot) {
		if (mc.player == null || mc.interactionManager == null || !isClickReady()) {
			return;
		}

		mc.interactionManager.clickSlot(windowId, slot, 0, SlotActionType.PICKUP, mc.player);
		setClickDelay();
	}

	private void skipChest(String message) {
		modMessage(message);
		pendingReward = null;
		action = Action.CROESUS;
		nextClickAtMs = System.currentTimeMillis() + clickDelayMs() * 2L;
		closeCurrentScreen();
	}

	private boolean isRewardSlotValid(int slot) {
		return slot >= 0 && slot <= 45;
	}

	private void closeCurrentScreen() {
		if (mc.player == null) {
			return;
		}

		ignoreNextClosePacket = true;
		mc.player.closeHandledScreen();
	}

	private boolean containsLore(ItemStack stack, String contains) {
		if (contains == null || contains.isBlank()) {
			return false;
		}

		String wanted = contains.toLowerCase(Locale.ROOT);
		for (String line : getCleanLore(stack)) {
			if (line.toLowerCase(Locale.ROOT).contains(wanted)) {
				return true;
			}
		}

		return false;
	}

	private List<Text> getLore(ItemStack stack) {
		LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
		if (loreComponent == null) {
			return List.of();
		}

		return loreComponent.styledLines();
	}

	private List<String> getCleanLore(ItemStack stack) {
		List<String> clean = new ArrayList<>();
		for (Text line : getLore(stack)) {
			clean.add(Utils.stripColor(line.getString()).trim());
		}
		return clean;
	}

	private boolean isClickReady() {
		return System.currentTimeMillis() >= nextClickAtMs;
	}

	private void setClickDelay() {
		nextClickAtMs = System.currentTimeMillis() + clickDelayMs();
	}

	private long clickDelayMs() {
		return Math.max(0L, (long) clickDelay.getInput());
	}

	private static int parseNumeral(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0;
		}

		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException ignored) {
			return romanToInt(raw);
		}
	}

	private static int romanToInt(String raw) {
		String roman = raw.toUpperCase(Locale.ROOT).trim();
		if (roman.isEmpty()) {
			return 0;
		}

		int total = 0;
		int previous = 0;
		for (int i = roman.length() - 1; i >= 0; i--) {
			int current = romanValue(roman.charAt(i));
			if (current <= 0) {
				return 0;
			}

			if (current < previous) {
				total -= current;
			} else {
				total += current;
				previous = current;
			}
		}

		return Math.max(total, 0);
	}

	private static int romanValue(char character) {
		return switch (character) {
			case 'I' -> 1;
			case 'V' -> 5;
			case 'X' -> 10;
			case 'L' -> 50;
			case 'C' -> 100;
			case 'D' -> 500;
			case 'M' -> 1000;
			default -> 0;
		};
	}

	private static String toIdFallback(String itemName) {
		String normalized = itemName
			.toUpperCase(Locale.ROOT)
			.replace("'", "")
			.replaceAll("[^A-Z0-9]+", "_")
			.replaceAll("_+", "_")
			.replaceAll("^_+", "")
			.replaceAll("_+$", "");

		return normalized;
	}

	private static int parseIntSafe(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static double parseDoubleSafe(String value, double fallback) {
		try {
			return Double.parseDouble(value);
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private static String formatProfit(double coins) {
		double abs = Math.abs(coins);
		if (abs >= 1_000_000.0) {
			return new DecimalFormat("0.00").format(coins / 1_000_000.0) + "m";
		}

		if (abs >= 1_000.0) {
			return new DecimalFormat("0.00").format(coins / 1_000.0) + "k";
		}

		return new DecimalFormat("0").format(coins);
	}

	private static Map<String, String> createItemReplacements() {
		Map<String, String> map = new HashMap<>();
		map.put("Shiny Wither Boots", "WITHER_BOOTS");
		map.put("Shiny Wither Leggings", "WITHER_LEGGINGS");
		map.put("Shiny Wither Chestplate", "WITHER_CHESTPLATE");
		map.put("Shiny Wither Helmet", "WITHER_HELMET");
		map.put("Shiny Necron's Handle", "NECRON_HANDLE");
		map.put("Wither Shard", "SHARD_WITHER");
		map.put("Thorn Shard", "SHARD_THORN");
		map.put("Apex Dragon Shard", "SHARD_APEX_DRAGON");
		map.put("Power Dragon Shard", "SHARD_POWER_DRAGON");
		map.put("Scarf Shard", "SHARD_SCARF");
		map.put("Necron Dye", "DYE_NECRON");
		map.put("Livid Dye", "DYE_LIVID");
		return map;
	}

	private static void modMessage(String message) {
		Utils.addChatMessage("§eAutoCroesus §7» §r" + message);
	}

	private enum Action {
		CROESUS,
		REWARDS,
		CHEST,
		IDLE
	}

	public static class ChestInfo {
		public ChestType type = ChestType.NONE;
		public double cost = 0.0;
		public transient double value = 0.0;
		public transient double profit = 0.0;
		public final List<ChestItem> items = new ArrayList<>();
	}

	public static class ChestItem {
		public final String id;
		public final int quantity;

		public ChestItem(String id, int quantity) {
			this.id = id;
			this.quantity = quantity;
		}
	}

	public enum ChestType {
		BEDROCK,
		OBSIDIAN,
		DIAMOND,
		GOLD,
		WOOD,
		NONE;

		public static ChestType fromDisplayName(String rawName) {
			String clean = Utils.stripColor(rawName == null ? "" : rawName).toUpperCase(Locale.ROOT);
			if (clean.startsWith("BEDROCK")) {
				return BEDROCK;
			}
			if (clean.startsWith("OBSIDIAN")) {
				return OBSIDIAN;
			}
			if (clean.startsWith("DIAMOND")) {
				return DIAMOND;
			}
			if (clean.startsWith("GOLD")) {
				return GOLD;
			}
			if (clean.startsWith("WOOD")) {
				return WOOD;
			}
			return NONE;
		}
	}

	private static class Reward {
		public final ChestInfo chest;
		public int slot = -1;
		public String name = "unknown";
		public final boolean alwaysBuy;

		private Reward(ChestInfo chest, boolean alwaysBuy) {
			this.chest = chest;
			this.alwaysBuy = alwaysBuy;
		}
	}

	private enum RunType {
		MASTER_CATACOMBS("Master Mode The Catacombs", "Master Catacombs - Floor "),
		CATACOMBS("The Catacombs", "Catacombs - Floor "),
		NONE("None", "None");

		private final String displayName;
		private final String rewardsTitle;

		RunType(String displayName, String rewardsTitle) {
			this.displayName = displayName;
			this.rewardsTitle = rewardsTitle;
		}

		public static RunType findByDisplayName(String formatted) {
			String name = Utils.stripColor(formatted == null ? "" : formatted);
			return Arrays.stream(values())
				.filter(type -> name.equals(type.displayName))
				.findFirst()
				.orElse(NONE);
		}

		public static RunType findByTitle(String title) {
			String name = Utils.stripColor(title == null ? "" : title);
			return Arrays.stream(values())
				.filter(type -> name.startsWith(type.rewardsTitle))
				.findFirst()
				.orElse(NONE);
		}
	}
}
