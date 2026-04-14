package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.mixin.HandledScreenAccessor;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.impl.dungeon.CroesusPriceService;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.LocationUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Vesuvius extends Module {
	private static final long PRICE_CACHE_MAX_AGE_MS = 1_800_000L;
	private static final double AURA_RANGE = 3.5D;

	private static final int[] CHEST_SLOTS = new int[]{
		10, 11, 12, 13, 14, 15, 16,
		19, 20, 21, 22, 23, 24, 25,
		28, 29, 30, 31, 32, 33, 34,
		37, 38, 39, 40, 41, 42, 43
	};

	private static final Pattern COST_PATTERN = Pattern.compile("^([\\d,]+)\\s+Coins$", Pattern.CASE_INSENSITIVE);
	private static final Pattern BOOK_PATTERN = Pattern.compile("^Enchanted Book \\((Ultimate )?([A-Za-z' ]+) ([IVXLCDM]+|\\d+)\\)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PREVIEW_BOOK_PATTERN = Pattern.compile("^Enchanted Book \\(?([A-Za-z' ]+) ([IVXLCDM]+|\\d+)\\)?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ESSENCE_PATTERN = Pattern.compile("^([A-Za-z]+) Essence x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SHARD_PATTERN = Pattern.compile("^([A-Za-z' ]+) Shard(?: x(\\d+))?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern TEETH_PATTERN = Pattern.compile("^Kuudra Teeth x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PEARL_PATTERN = Pattern.compile("^Heavy Pearl x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern TRAILING_QUANTITY_PATTERN = Pattern.compile("^(.*) x(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern USELESS_LINE_PATTERN = Pattern.compile("^(Contents|Cost|Click to open!|FREE|Already opened!|Can't open another chest!|Paid Chest|Free Chest)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLAIMED_LINE_PATTERN = Pattern.compile("^(No more chests to open!|Already opened!|Can't open another chest!)$", Pattern.CASE_INSENSITIVE);

	private static final Set<String> ULTIMATE_ENCHANTS = Set.of("FATAL TEMPO", "INFERNO");

	private static final Map<String, String> ITEM_REPLACEMENTS = createItemReplacements();
    private static final Map<Integer, Double> STAR_COUNT_TO_ESSENCE = createStarEssenceMap();
    private static final List<KeyData> KEYS = createKeyData();

	private final SliderSetting clickDelay = new SliderSetting("Click Delay", "ms", 300.0, 100.0, 1000.0, 25.0);
	private final ButtonSetting autoBuy = new ButtonSetting("Auto Buy", false);
	private final ButtonSetting openPaidChests = new ButtonSetting("Open paid chests", true);
	private final SliderSetting paidMinProfit = new SliderSetting("Paid min profit", "m", 0.75, 0.0, 5.0, 0.05);
	private final ButtonSetting hideClaimed = new ButtonSetting("Hide Claimed", true);
	private final ButtonSetting useSalvagePrices = new ButtonSetting("Use Salvaged", false);
	private final ButtonSetting highlightUnopened = new ButtonSetting("Highlight unopened", true);
	private final ButtonSetting chestItemsHud = new ButtonSetting("Chest Items HUD", true);
	private final SliderSetting chestHudX = new SliderSetting("HUD X", "%", 70.0, 0.0, 100.0, 1.0);
	private final SliderSetting chestHudY = new SliderSetting("HUD Y", "%", 35.0, 0.0, 100.0, 1.0);
	private final SliderSetting chestHudScale = new SliderSetting("HUD Scale", "x", 1.0, 0.5, 2.5, 0.05);
	private final ButtonSetting startButton = new ButtonSetting("Start", this::start);
	private final ButtonSetting refreshPricesButton = new ButtonSetting("Refresh Prices", this::refreshPrices);

	private final BitSet unopenedSlots = new BitSet(64);
	private final BitSet claimedSlots = new BitSet(64);

	private boolean running = false;
	private Action action = Action.IDLE;
	private long nextClickAtMs = 0L;
	private int listMenuSyncId = -1;
	private int chestMenuSyncId = -1;
	private boolean ignoreNextClosePacket = false;
	private boolean autoBuyNoticeSent = false;
	private ChestInfo activeChestInfo = null;

	public Vesuvius() {
		super("Auto Vesuvius", "Automatically opens profitable Vesuvius chests.", category.Kuudra);

		registerSetting(clickDelay);
		registerSetting(autoBuy);
		registerSetting(openPaidChests);
		registerSetting(paidMinProfit);
		registerSetting(hideClaimed);
		registerSetting(useSalvagePrices);
		registerSetting(highlightUnopened);
		registerSetting(chestItemsHud);
		registerSetting(chestHudX);
		registerSetting(chestHudY);
		registerSetting(chestHudScale);
		registerSetting(startButton);
		registerSetting(refreshPricesButton);

		paidMinProfit.setVisibilityCondition(openPaidChests::isToggled);
		chestHudX.setVisibilityCondition(chestItemsHud::isToggled);
		chestHudY.setVisibilityCondition(chestItemsHud::isToggled);
		chestHudScale.setVisibilityCondition(chestItemsHud::isToggled);
	}

	@Override
	public String getDesc() {
		return "Automates Vesuvius chest opening and highlights unopened entries.";
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

		if (!isInVesuviusArea()) {
			modMessage(Formatting.RED + "Stopped because you left the Vesuvius area.");
			resetState();
			return;
		}

		switch (action) {
			case VESUVIUS -> tickVesuvius();
			case CHEST -> tickChest();
			default -> {
			}
		}
	}

	@EventHandler
	public void onRenderScreen(RenderScreenEvent event) {
		if (!(event.screen.getScreenHandler() instanceof GenericContainerScreenHandler menu)) {
			return;
		}

		String title = getTitle(event.screen);

		if (isVesuviusMenuTitle(title)) {
			refreshChestSlots(menu);
			if (hideClaimed.isToggled()) {
				drawClaimedSlotsMask(event, menu);
			}
			if (highlightUnopened.isToggled()) {
				drawUnopenedHighlights(event, menu);
			}
			return;
		}

		if (!isChestTitle(title)) {
			return;
		}

		ChestInfo chest = parseChestInfo(menu, title);
		if (chest == null) {
			return;
		}

		if (chestItemsHud.isToggled()) {
			drawChestItemsHud(event, chest);
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

		if (running && action != Action.IDLE && isInVesuviusArea()) {
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

		if (!isInVesuviusArea()) {
			modMessage(Formatting.RED + "You must be in the Forgotten Skull area.");
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
		action = Action.VESUVIUS;
		nextClickAtMs = 0L;
		autoBuyNoticeSent = false;
		if (!clickVesuvius()) {
			modMessage(Formatting.RED + "Failed to click Vesuvius. Face the NPC and try again.");
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
		nextClickAtMs = 0L;
		listMenuSyncId = -1;
		chestMenuSyncId = -1;
		unopenedSlots.clear();
		claimedSlots.clear();
		ignoreNextClosePacket = false;
		autoBuyNoticeSent = false;
		activeChestInfo = null;
	}

	private void tickVesuvius() {
		if (isVesuviusScreen()) {
			if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu)) {
				return;
			}

			refreshChestSlots(menu);

			int target = unopenedSlots.nextSetBit(0);
			if (target < 0) {
				modMessage("All Vesuvius chests are opened.");
				resetState();
				closeCurrentScreen();
				return;
			}

			if (clickSlot(menu.syncId, target)) {
				action = Action.CHEST;
				chestMenuSyncId = -1;
				activeChestInfo = null;
			}
			return;
		}

		if (mc.currentScreen != null) {
			return;
		}

		clickVesuvius();
	}

	private void tickChest() {
		if (isVesuviusScreen()) {
			action = Action.VESUVIUS;
			tickVesuvius();
			return;
		}

		if (!isChestScreen()) {
			if (mc.currentScreen != null) {
				return;
			}

			action = Action.VESUVIUS;
			clickVesuvius();
			return;
		}

		if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu)) {
			return;
		}

		String title = getTitle(mc.currentScreen);
		if (chestMenuSyncId != menu.syncId) {
			chestMenuSyncId = menu.syncId;
		}

		activeChestInfo = parseChestInfo(menu, title);
		if (activeChestInfo == null) {
			return;
		}

		if (!autoBuy.isToggled()) {
			if (!autoBuyNoticeSent) {
				autoBuyNoticeSent = true;
				modMessage("Auto Buy is disabled. Open the chest manually to purchase.");
			}
			return;
		}

		autoBuyNoticeSent = false;

		if (shouldSkipPaidChest(activeChestInfo)) {
			modMessage(
				"Skipping paid chest | Profit: " + formatProfit(activeChestInfo.profit) +
					" (min " + formatProfit(paidMinProfit.getInput() * 1_000_000.0) + ")"
			);
			action = Action.VESUVIUS;
			nextClickAtMs = System.currentTimeMillis() + clickDelayMs() * 2L;
			closeCurrentScreen();
			return;
		}

		if (menu.slots.size() <= 31) {
			modMessage(Formatting.RED + "Open chest button is not available.");
			resetState();
			return;
		}

		if (!clickSlot(menu.syncId, 31)) {
			return;
		}

		modMessage(
			"Opening " + activeChestInfo.type.displayName +
				" | Profit: " + formatProfit(activeChestInfo.profit)
		);

		action = Action.VESUVIUS;
		nextClickAtMs = System.currentTimeMillis() + clickDelayMs() * 2L;
	}

	private boolean shouldSkipPaidChest(ChestInfo chest) {
		if (chest.type != ChestType.PAID) {
			return false;
		}

		if (!openPaidChests.isToggled()) {
			return true;
		}

		double minProfit = paidMinProfit.getInput() * 1_000_000.0;
		return chest.profit < minProfit;
	}

	private boolean clickVesuvius() {
		if (!isClickReady() || mc.player == null || mc.world == null || mc.interactionManager == null) {
			return false;
		}

		PlayerEntity vesuvius = findVesuvius();
		if (vesuvius == null) {
			return false;
		}

		if (vesuvius.squaredDistanceTo(mc.player) > 16.0) {
			modMessage(Formatting.RED + "Vesuvius is too far away.");
			return false;
		}

		mc.interactionManager.attackEntity(mc.player, vesuvius);
		mc.player.swingHand(Hand.MAIN_HAND);
		setClickDelay();
		return true;
	}

	private PlayerEntity findVesuvius() {
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
			entity -> "Vesuvius".equalsIgnoreCase(Utils.stripColor(entity.getName().getString()))
		);

		if (!playerMatches.isEmpty()) {
			return playerMatches.stream()
				.min((a, b) -> Double.compare(a.squaredDistanceTo(mc.player), b.squaredDistanceTo(mc.player)))
				.orElse(null);
		}

		List<ArmorStandEntity> stands = mc.world.getEntitiesByClass(
			ArmorStandEntity.class,
			box,
			stand -> Utils.stripColor(stand.getName().getString()).contains("Vesuvius")
		);

		if (stands.isEmpty()) {
			return null;
		}

		ArmorStandEntity closestStand = stands.stream()
			.min((a, b) -> Double.compare(a.squaredDistanceTo(mc.player), b.squaredDistanceTo(mc.player)))
			.orElse(null);
		if (closestStand == null) {
			return null;
		}

		List<PlayerEntity> linkedPlayers = mc.world.getEntitiesByClass(
			PlayerEntity.class,
			box,
			entity -> entity.squaredDistanceTo(closestStand) <= 0.01
		);

		return linkedPlayers.stream()
			.min((a, b) -> Double.compare(a.squaredDistanceTo(mc.player), b.squaredDistanceTo(mc.player)))
			.orElse(null);
	}

	private void refreshChestSlots(GenericContainerScreenHandler menu) {
		unopenedSlots.clear();
		claimedSlots.clear();
		listMenuSyncId = menu.syncId;

		for (int slotId : CHEST_SLOTS) {
			if (slotId >= menu.slots.size()) {
				continue;
			}

			ItemStack stack = menu.slots.get(slotId).getStack();
			if (stack.isEmpty() || !stack.isOf(Items.PLAYER_HEAD)) {
				continue;
			}

			if (isClaimedChestStack(stack)) {
				claimedSlots.set(slotId);
				continue;
			}

			unopenedSlots.set(slotId);
		}
	}

	private boolean isClaimedChestStack(ItemStack stack) {
		for (String line : getCleanLore(stack)) {
			if (CLAIMED_LINE_PATTERN.matcher(line).matches()) {
				return true;
			}
		}
		return false;
	}

	private ChestInfo parseChestInfo(GenericContainerScreenHandler menu, String rawTitle) {
		ChestType type = ChestType.fromTitle(rawTitle);
		if (type == ChestType.NONE || menu.slots.size() <= 31) {
			return null;
		}

		ChestInfo info = new ChestInfo();
		info.type = type;
		info.tier = menu.slots.size() > 49 ? tierFromBackItem(menu.slots.get(49).getStack()) : KuudraTier.UNKNOWN;

		// Vesuvius/Croesus style menus often encode full content+cost in preview lore.
		ChestInfo previewInfo = parseChestFromPreviewLore(menu, info.type, info.tier);
		if (previewInfo != null && !previewInfo.displayItems.isEmpty()) {
			return previewInfo;
		}

		ItemStack openButton = menu.slots.get(31).getStack();
		info.cost = parseChestCost(openButton);

		int limit = Math.min(54, menu.slots.size());
		for (int i = 0; i < limit; i++) {
			if (i == 31 || i == 49 || i == 50 || i == 51) {
				continue;
			}

			Slot slot = menu.slots.get(i);
			ItemStack stack = slot.getStack();
			if (stack.isEmpty() || isDecorativeStack(stack)) {
				continue;
			}

			ChestItem item = parseItemStack(stack);
			if (item == null) {
				continue;
			}

			double unitPrice = getSellPrice(item.id);
			double totalPrice = unitPrice * item.quantity;
			info.value += unitPrice * item.quantity;
			info.items.add(item);
			info.displayItems.add(new ChestDisplayItem(item.displayName, totalPrice));
		}

		info.profit = info.value - info.cost;
		info.displayItems.sort(Comparator.comparingDouble((ChestDisplayItem item) -> item.price).reversed());
		return info;
	}

	private ChestInfo parseChestFromPreviewLore(GenericContainerScreenHandler menu, ChestType type, KuudraTier tier) {
		ItemStack preview = ItemStack.EMPTY;
		if (menu.slots.size() > 31 && menu.slots.get(31).getStack().isOf(Items.CHEST)) {
			preview = menu.slots.get(31).getStack();
		} else if (menu.slots.size() > 14 && menu.slots.get(14).getStack().isOf(Items.PLAYER_HEAD)) {
			preview = menu.slots.get(14).getStack();
		}

		if (preview.isEmpty()) {
			return null;
		}

		ChestInfo info = new ChestInfo();
		info.type = type;
		info.tier = tier;
		info.cost = parseChestCost(preview);

		for (Text lineComponent : getLore(preview)) {
			String cleanLine = Utils.stripColor(lineComponent.getString()).trim();
			if (cleanLine.isEmpty()) {
				continue;
			}

			if (cleanLine.contains("Kuudra Key")) {
				info.cost = Math.max(info.cost, getPriceOfKey(cleanLine));
				continue;
			}

			if (USELESS_LINE_PATTERN.matcher(cleanLine).matches()) {
				continue;
			}

			double price = parseItemValue(cleanLine);
			info.value += price;
			info.displayItems.add(new ChestDisplayItem(cleanLine, price));
		}

		info.profit = info.value - info.cost;
		info.displayItems.sort(Comparator.comparingDouble((ChestDisplayItem item) -> item.price).reversed());
		return info;
	}

	private boolean isDecorativeStack(ItemStack stack) {
		return stack.isOf(Items.BLACK_STAINED_GLASS_PANE)
			|| stack.isOf(Items.GRAY_STAINED_GLASS_PANE)
			|| stack.isOf(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
			|| stack.isOf(Items.BROWN_STAINED_GLASS_PANE)
			|| stack.isOf(Items.RED_STAINED_GLASS_PANE)
			|| stack.isOf(Items.BARRIER)
			|| stack.isOf(Items.ARROW)
			|| stack.isOf(Items.CHEST)
			|| stack.isOf(Items.PLAYER_HEAD);
	}

	private ChestItem parseItemStack(ItemStack stack) {
		String clean = Utils.stripColor(stack.getName().getString()).trim();
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
			return new ChestItem(id, Math.max(1, stack.getCount()), clean);
		}

		Matcher essenceMatcher = ESSENCE_PATTERN.matcher(clean);
		if (essenceMatcher.matches()) {
			String type = essenceMatcher.group(1).toUpperCase(Locale.ROOT);
			int amount = Math.max(1, parseIntSafe(essenceMatcher.group(2), stack.getCount()));
			return new ChestItem("ESSENCE_" + type, amount, clean);
		}

		int quantity = Math.max(1, stack.getCount());
		Matcher quantityMatcher = TRAILING_QUANTITY_PATTERN.matcher(clean);
		if (quantityMatcher.matches()) {
			clean = quantityMatcher.group(1).trim();
			quantity = Math.max(quantity, parseIntSafe(quantityMatcher.group(2), quantity));
		}

		String replacement = ITEM_REPLACEMENTS.get(clean);
		if (replacement != null) {
			return new ChestItem(replacement, quantity, clean);
		}

		String id = CroesusPriceService.findItemIdByName(clean);
		if (id == null || id.isBlank()) {
			id = toIdFallback(clean);
		}

		if (id == null || id.isBlank()) {
			return null;
		}

		return new ChestItem(id, quantity, clean);
	}

	private double parseItemValue(String rawLine) {
		String clean = rawLine.replace("✪", "").trim();
		if (clean.isEmpty()) {
			return 0.0;
		}

		if (useSalvagePrices.isToggled() && clean.contains("Molten")) {
			return getSellPrice("ESSENCE_CRIMSON") * 600.0;
		}

		Matcher bookMatcher = PREVIEW_BOOK_PATTERN.matcher(clean);
		if (bookMatcher.matches()) {
			String enchantName = bookMatcher.group(1).trim();
			int level = parseNumeral(bookMatcher.group(2));
			if (level > 0) {
				String normalized = enchantName.toUpperCase(Locale.ROOT).replace(" ", "_").replace("'", "");
				boolean ultimate = ULTIMATE_ENCHANTS.contains(enchantName.toUpperCase(Locale.ROOT));
				String id = "ENCHANTMENT_" + (ultimate ? "ULTIMATE_" : "") + normalized + "_" + level;
				return getSellPrice(id);
			}
		}

		Matcher essenceMatcher = ESSENCE_PATTERN.matcher(clean);
		if (essenceMatcher.matches()) {
			double unit = getSellPrice("ESSENCE_" + essenceMatcher.group(1).toUpperCase(Locale.ROOT));
			return unit * parseIntSafe(essenceMatcher.group(2), 1);
		}

		Matcher shardMatcher = SHARD_PATTERN.matcher(clean);
		if (shardMatcher.matches()) {
			String shardId = "SHARD_" + shardMatcher.group(1).toUpperCase(Locale.ROOT).replace(" ", "_").replace("'", "");
			double unit = getSellPrice(shardId);
			double qty = shardMatcher.group(2) == null ? 1.0 : parseDoubleSafe(shardMatcher.group(2), 1.0);
			return unit * Math.max(1.0, qty);
		}

		Matcher teethMatcher = TEETH_PATTERN.matcher(clean);
		if (teethMatcher.matches()) {
			double unit = getSellPrice("KUUDRA_TEETH");
			return unit * parseIntSafe(teethMatcher.group(1), 1);
		}

		Matcher pearlMatcher = PEARL_PATTERN.matcher(clean);
		if (pearlMatcher.matches()) {
			double unit = getSellPrice("HEAVY_PEARL");
			return unit * parseIntSafe(pearlMatcher.group(1), 1);
		}

		if (useSalvagePrices.isToggled() && isSalvageItem(clean)) {
			double essencePrice = getSellPrice("ESSENCE_CRIMSON");
			double essenceAmount = STAR_COUNT_TO_ESSENCE.getOrDefault(countStars(rawLine), 120.0);
			return essencePrice * essenceAmount;
		}

		String replacement = ITEM_REPLACEMENTS.get(clean);
		if (replacement != null) {
			return getSellPrice(replacement);
		}

		String id = CroesusPriceService.findItemIdByName(clean);
		if (id == null || id.isBlank()) {
			id = toIdFallback(clean);
		}

		return getSellPrice(id);
	}

	private boolean isSalvageItem(String clean) {
		String lower = clean.toLowerCase(Locale.ROOT);
		return lower.contains("boots")
			|| lower.contains("chestplate")
			|| lower.contains("helmet")
			|| lower.contains("cloak")
			|| lower.contains("aurora staff")
			|| lower.contains("hollow wand");
	}

	private int countStars(String text) {
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '✪') {
				count++;
			}
		}
		return Math.max(0, count);
	}

	private double getPriceOfKey(String line) {
		for (KeyData key : KEYS) {
			if (!line.toLowerCase(Locale.ROOT).contains(key.type.toLowerCase(Locale.ROOT))) {
				continue;
			}

			double material = Math.min(getSellPrice("ENCHANTED_RED_SAND"), getSellPrice("ENCHANTED_MYCELIUM"));
			double star = getSellPrice("CORRUPTED_NETHER_STAR");
			return key.coins + (material * key.quantity) + (star * 2.0);
		}

		return 0.0;
	}

	private double parseChestCost(ItemStack openButton) {
		List<String> lore = getCleanLore(openButton);
		for (String line : lore) {
			String clean = Utils.stripColor(line == null ? "" : line).trim();
			if (clean.equalsIgnoreCase("FREE") || clean.equalsIgnoreCase("aFREE")) {
				return 0.0;
			}

			Matcher matcher = COST_PATTERN.matcher(clean);
			if (matcher.find()) {
				return parseDoubleSafe(matcher.group(1).replace(",", ""), 0.0);
			}
		}

		return 0.0;
	}

	private KuudraTier tierFromBackItem(ItemStack item) {
		if (item == null || item.isEmpty()) {
			return KuudraTier.UNKNOWN;
		}

		List<String> lore = getCleanLore(item);
		if (lore.isEmpty()) {
			return KuudraTier.UNKNOWN;
		}

		String first = lore.get(0);
		String prefix = "To Kuudra - ";
		if (!first.startsWith(prefix)) {
			return KuudraTier.UNKNOWN;
		}

		String tier = first.substring(prefix.length());
		return switch (tier) {
			case "Basic" -> KuudraTier.BASIC;
			case "Hot" -> KuudraTier.HOT;
			case "Burning" -> KuudraTier.BURNING;
			case "Fiery" -> KuudraTier.FIERY;
			case "Infernal" -> KuudraTier.INFERNAL;
			default -> KuudraTier.UNKNOWN;
		};
	}

	private void drawUnopenedHighlights(RenderScreenEvent event, GenericContainerScreenHandler menu) {
		if (menu.syncId != listMenuSyncId || unopenedSlots.isEmpty()) {
			return;
		}

		int left = ((HandledScreenAccessor) event.screen).getX();
		int top = ((HandledScreenAccessor) event.screen).getY();

		for (int slotId = unopenedSlots.nextSetBit(0); slotId >= 0; slotId = unopenedSlots.nextSetBit(slotId + 1)) {
			if (slotId >= menu.slots.size()) {
				continue;
			}

			Slot slot = menu.slots.get(slotId);
			if (!slot.hasStack()) {
				continue;
			}

			int x = left + slot.x;
			int y = top + slot.y;

			event.context.fill(x, y, x + 16, y + 16, 0x5530D5FF);
			event.context.fill(x, y, x + 16, y + 1, 0xDD7FE7FF);
			event.context.fill(x, y + 15, x + 16, y + 16, 0xDD7FE7FF);
			event.context.fill(x, y, x + 1, y + 16, 0xDD7FE7FF);
			event.context.fill(x + 15, y, x + 16, y + 16, 0xDD7FE7FF);
		}
	}

	private void drawClaimedSlotsMask(RenderScreenEvent event, GenericContainerScreenHandler menu) {
		if (menu.syncId != listMenuSyncId || claimedSlots.isEmpty()) {
			return;
		}

		int left = ((HandledScreenAccessor) event.screen).getX();
		int top = ((HandledScreenAccessor) event.screen).getY();

		for (int slotId = claimedSlots.nextSetBit(0); slotId >= 0; slotId = claimedSlots.nextSetBit(slotId + 1)) {
			if (slotId >= menu.slots.size()) {
				continue;
			}

			Slot slot = menu.slots.get(slotId);
			int x = left + slot.x;
			int y = top + slot.y;

			event.context.fill(x, y, x + 16, y + 16, 0xB0101010);
			event.context.fill(x, y, x + 16, y + 1, 0xE0404040);
			event.context.fill(x, y + 15, x + 16, y + 16, 0xE0404040);
			event.context.fill(x, y, x + 1, y + 16, 0xE0404040);
			event.context.fill(x + 15, y, x + 16, y + 16, 0xE0404040);
		}
	}

	private void drawChestItemsHud(RenderScreenEvent event, ChestInfo chest) {
		if (chest.displayItems.isEmpty()) {
			return;
		}

		int screenW = event.context.getScaledWindowWidth();
		int screenH = event.context.getScaledWindowHeight();

		float scale = (float) chestHudScale.getInput();
		int x = (int) Math.round((chestHudX.getInput() / 100.0) * screenW);
		int y = (int) Math.round((chestHudY.getInput() / 100.0) * screenH);

		List<ChestDisplayItem> sorted = new ArrayList<>(chest.displayItems);
		sorted.sort(Comparator.comparingDouble((ChestDisplayItem i) -> i.price).reversed());

		int maxLines = Math.min(10, sorted.size());
		int width = 260;
		int height = 18 + (maxLines * 9) + 26;

		event.context.getMatrices().pushMatrix();
		event.context.getMatrices().translate(x, y);
		event.context.getMatrices().scale(scale, scale);

		event.context.fill(0, 0, width, height, 0xB0000000);
		event.context.fill(0, 0, width, 1, 0xFF38D2FF);
		event.context.fill(0, height - 1, width, height, 0xFF38D2FF);

		event.context.drawTextWithShadow(mc.textRenderer, "Vesuvius Chest", 4, 4, 0xFFFFFFFF);

		int yOffset = 15;
		for (int i = 0; i < maxLines; i++) {
			ChestDisplayItem line = sorted.get(i);
			String label = trimToWidth(line.name, width - 96);
			String value = formatProfit(line.price);

			event.context.drawText(mc.textRenderer, label, 4, yOffset, 0xFFFFFFFF, false);
			event.context.drawText(mc.textRenderer, value, width - 4 - mc.textRenderer.getWidth(value), yOffset, 0xFFAAAAAA, false);
			yOffset += 9;
		}

		yOffset += 2;
		String cost = formatProfit(chest.cost);
		String profit = formatSignedProfit(chest.profit);
		int profitColor = chest.profit >= 0.0 ? 0xFF55FF55 : 0xFFFF5555;

		event.context.drawText(mc.textRenderer, "Cost:", 4, yOffset, 0xFFFF7777, false);
		event.context.drawText(mc.textRenderer, cost, width - 4 - mc.textRenderer.getWidth(cost), yOffset, 0xFFFF7777, false);

		yOffset += 10;
		event.context.drawText(mc.textRenderer, "Profit:", 4, yOffset, profitColor, false);
		event.context.drawText(mc.textRenderer, profit, width - 4 - mc.textRenderer.getWidth(profit), yOffset, profitColor, false);

		event.context.getMatrices().popMatrix();
	}

	private String trimToWidth(String raw, int maxWidth) {
		String clean = Utils.stripColor(raw == null ? "" : raw);
		if (mc.textRenderer.getWidth(clean) <= maxWidth) {
			return clean;
		}

		String ellipsis = "...";
		String out = clean;
		while (!out.isEmpty() && mc.textRenderer.getWidth(out + ellipsis) > maxWidth) {
			out = out.substring(0, out.length() - 1);
		}

		return out + ellipsis;
	}

	private double getSellPrice(String itemId) {
		if (itemId == null || itemId.isBlank()) {
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

	private boolean isInVesuviusArea() {
		String area = LocationUtils.getCurrentArea();
		if (area == null) {
			return false;
		}

		String lower = area.toLowerCase(Locale.ROOT);
		return lower.contains("forgotten skull") || lower.contains("kuudra");
	}

	private boolean isVesuviusScreen() {
		return isVesuviusMenuTitle(getTitle(mc.currentScreen));
	}

	private boolean isChestScreen() {
		return isChestTitle(getTitle(mc.currentScreen));
	}

	private boolean isVesuviusMenuTitle(String title) {
		return title.equalsIgnoreCase("Vesuvius");
	}

	private boolean isChestTitle(String title) {
		String lower = title.toLowerCase(Locale.ROOT);
		return lower.contains("paid chest") || lower.contains("free chest");
	}

	private String getTitle(Screen screen) {
		if (screen == null) {
			return "";
		}

		return Utils.stripColor(screen.getTitle().getString()).trim();
	}

	private boolean clickSlot(int windowId, int slot) {
		if (mc.player == null || mc.interactionManager == null || !isClickReady()) {
			return false;
		}

		mc.interactionManager.clickSlot(windowId, slot, 0, SlotActionType.PICKUP, mc.player);
		setClickDelay();
		return true;
	}

	private void closeCurrentScreen() {
		if (mc.player == null) {
			return;
		}

		ignoreNextClosePacket = true;
		mc.player.closeHandledScreen();
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

	private static String toIdFallback(String itemName) {
		return itemName
			.toUpperCase(Locale.ROOT)
			.replace("'", "")
			.replaceAll("[^A-Z0-9]+", "_")
			.replaceAll("_+", "_")
			.replaceAll("^_+", "")
			.replaceAll("_+$", "");
	}

	private static String formatSignedProfit(double coins) {
		if (coins > 0) {
			return "+" + formatProfit(coins);
		}
		return formatProfit(coins);
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
		map.put("Hellstorm Wand", "HELLSTORM_STAFF");
		map.put("Aurora Staff", "RUNIC_STAFF");
		map.put("Shiny Wither Boots", "WITHER_BOOTS");
		map.put("Shiny Wither Leggings", "WITHER_LEGGINGS");
		map.put("Shiny Wither Chestplate", "WITHER_CHESTPLATE");
		map.put("Shiny Wither Helmet", "WITHER_HELMET");
		map.put("Shiny Necron's Handle", "NECRON_HANDLE");
		map.put("Wither Shard", "SHARD_WITHER");
		map.put("Apex Dragon Shard", "SHARD_APEX_DRAGON");
		map.put("Power Dragon Shard", "SHARD_POWER_DRAGON");
		map.put("Scarf Shard", "SHARD_SCARF");
		return map;
	}

	private static Map<Integer, Double> createStarEssenceMap() {
		Map<Integer, Double> map = new HashMap<>();
		map.put(0, 120.0);
		map.put(1, 138.0);
		map.put(2, 158.0);
		map.put(3, 182.0);
		map.put(4, 210.0);
		map.put(5, 240.0);
		map.put(6, 272.0);
		map.put(7, 308.0);
		return map;
	}

	private static List<KeyData> createKeyData() {
		List<KeyData> keys = new ArrayList<>();
		keys.add(new KeyData("Kuudra Key", 155200, 2));
		keys.add(new KeyData("Hot Kuudra Key", 310400, 4));
		keys.add(new KeyData("Burning Kuudra Key", 582000, 16));
		keys.add(new KeyData("Fiery Kuudra Key", 1164000, 40));
		keys.add(new KeyData("Infernal Kuudra Key", 2328000, 80));
		return keys;
	}

	private static void modMessage(String message) {
		Utils.addChatMessage("§eAuto Vesuvius §7» §r" + message);
	}

	private enum Action {
		VESUVIUS,
		CHEST,
		IDLE
	}

	private enum ChestType {
		PAID("Paid Chest"),
		FREE("Free Chest"),
		NONE("Chest");

		private final String displayName;

		ChestType(String displayName) {
			this.displayName = displayName;
		}

		private static ChestType fromTitle(String title) {
			String lower = title == null ? "" : title.toLowerCase(Locale.ROOT);
			if (lower.contains("paid chest")) {
				return PAID;
			}
			if (lower.contains("free chest")) {
				return FREE;
			}
			return NONE;
		}
	}

	private enum KuudraTier {
		BASIC("Basic"),
		HOT("Hot"),
		BURNING("Burning"),
		FIERY("Fiery"),
		INFERNAL("Infernal"),
		UNKNOWN("Unknown");

		private final String displayName;

		KuudraTier(String displayName) {
			this.displayName = displayName;
		}
	}

	private static class ChestInfo {
		private ChestType type = ChestType.NONE;
		private KuudraTier tier = KuudraTier.UNKNOWN;
		private double cost = 0.0;
		private double value = 0.0;
		private double profit = 0.0;
		private final List<ChestItem> items = new ArrayList<>();
		private final List<ChestDisplayItem> displayItems = new ArrayList<>();
	}

	private static class ChestItem {
		private final String id;
		private final int quantity;
		private final String displayName;

		private ChestItem(String id, int quantity, String displayName) {
			this.id = id;
			this.quantity = quantity;
			this.displayName = displayName;
		}
	}

	private static class ChestDisplayItem {
		private final String name;
		private final double price;

		private ChestDisplayItem(String name, double price) {
			this.name = name;
			this.price = price;
		}
	}

	private static class KeyData {
		private final String type;
		private final int coins;
		private final int quantity;

		private KeyData(String type, int coins, int quantity) {
			this.type = type;
			this.coins = coins;
			this.quantity = quantity;
		}
	}
}
