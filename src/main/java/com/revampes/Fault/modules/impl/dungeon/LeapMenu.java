package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.events.impl.KeyEvent;
import com.revampes.Fault.events.impl.MouseButtonEvent;
import com.revampes.Fault.events.impl.ReceiveMessageEvent;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.events.impl.SlotClickEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.InputSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.KeyAction;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LeapMenu extends Module {
    private static final int BOX_WIDTH = 200;
    private static final int BOX_HEIGHT = 75;
    private static final long CLICK_REGISTER_TIMEOUT_MS = 2500L;

    private static final Pattern LEAP_ANNOUNCE_REGEX = Pattern.compile("You have teleported to (\\w{1,16})!");

    private int pendingLeapSyncId = -1;
    private long pendingLeapSentAtMs = 0L;

    private final SelectSetting sorting = new SelectSetting(
        "Sorting",
        0,
        new String[]{"Odin Sorting", "A-Z Class", "A-Z Name", "Custom sorting", "No Sorting"}
    );
    private final ButtonSetting onRelease = new ButtonSetting("On Key Release", false);
    private final ButtonSetting onlyClass = new ButtonSetting("Only Classes", false);
    private final ButtonSetting colorStyle = new ButtonSetting("Color Style", false);
    private final ColorSetting backgroundColor = new ColorSetting("Background Color", new Color(38, 38, 38, 191));
    private final SliderSetting scale = new SliderSetting("Render Scale", 1.0, 0.1, 2.0, 0.1);

    private final SelectSetting keybindMode = new SelectSetting("Mode", 0, new String[]{"Corners", "Class"});

    private final InputSetting topLeftKeybind = new InputSetting("Top Left Key", 18, "NONE", "ex: R");
    private final InputSetting topRightKeybind = new InputSetting("Top Right Key", 18, "NONE", "ex: T");
    private final InputSetting bottomLeftKeybind = new InputSetting("Bottom Left Key", 18, "NONE", "ex: F");
    private final InputSetting bottomRightKeybind = new InputSetting("Bottom Right Key", 18, "NONE", "ex: G");

    private final InputSetting archerKeybind = new InputSetting("Archer Key", 18, "NONE", "ex: R");
    private final InputSetting berserkerKeybind = new InputSetting("Berserker Key", 18, "NONE", "ex: T");
    private final InputSetting healerKeybind = new InputSetting("Healer Key", 18, "NONE", "ex: F");
    private final InputSetting mageKeybind = new InputSetting("Mage Key", 18, "NONE", "ex: G");
    private final InputSetting tankKeybind = new InputSetting("Tank Key", 18, "NONE", "ex: H");

    private final InputSetting customSortingOrder = new InputSetting(
        "Custom Order",
        128,
        "",
        "comma separated names, ex: Alice,Bob"
    );

    private final ButtonSetting leapAnnounce = new ButtonSetting("Leap Announce", false);

    public LeapMenu() {
        super("LeapMenu", "Custom Spirit Leap menu with sorting and keybinds.", category.Dungeon);

        this.registerSetting(sorting);
        this.registerSetting(onRelease);
        this.registerSetting(onlyClass);
        this.registerSetting(colorStyle);
        this.registerSetting(backgroundColor);
        this.registerSetting(scale);

        this.registerSetting(keybindMode);

        this.registerSetting(topLeftKeybind);
        this.registerSetting(topRightKeybind);
        this.registerSetting(bottomLeftKeybind);
        this.registerSetting(bottomRightKeybind);

        this.registerSetting(archerKeybind);
        this.registerSetting(berserkerKeybind);
        this.registerSetting(healerKeybind);
        this.registerSetting(mageKeybind);
        this.registerSetting(tankKeybind);

        this.registerSetting(customSortingOrder);
        this.registerSetting(leapAnnounce);

        backgroundColor.setVisibilityCondition(() -> !colorStyle.isToggled());
        customSortingOrder.setVisibilityCondition(() -> (int) sorting.getValue() == 3);

        topLeftKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 0);
        topRightKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 0);
        bottomLeftKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 0);
        bottomRightKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 0);

        archerKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 1);
        berserkerKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 1);
        healerKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 1);
        mageKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 1);
        tankKeybind.setVisibilityCondition(() -> (int) keybindMode.getValue() == 1);
    }

    @Override
    public void onDisable() {
        clearPendingLeap();
    }

    @EventHandler
    public void onRenderScreen(RenderScreenEvent event) {
        HandledScreen<?> screen = currentLeapScreen();
        if (screen == null || screen != event.screen) {
            return;
        }

        List<LeapEntry> teammates = getDisplayedLeapEntries(screen);
        if (teammates.isEmpty()) {
            return;
        }

        int halfW = event.context.getScaledWindowWidth() / 2;
        int halfH = event.context.getScaledWindowHeight() / 2;

        for (int i = 0; i < 4; i++) {
            LeapEntry player = teammates.get(i);
            if (player.isEmpty()) {
                continue;
            }

            int col = i % 2;
            int row = i / 2;

            int nearX = col == 0 ? halfW - 24 : halfW + 24;
            int nearY = row == 0 ? halfH - 24 : halfH + 24;

            int scaledWidth = Math.max(32, (int) Math.round(BOX_WIDTH * scale.getInput()));
            int scaledHeight = Math.max(24, (int) Math.round(BOX_HEIGHT * scale.getInput()));

            int x = col == 0 ? nearX - scaledWidth : nearX;
            int y = row == 0 ? nearY - scaledHeight : nearY;

            boolean hovered = (col == 0 ? event.mouseX < halfW : event.mouseX >= halfW)
                && (row == 0 ? event.mouseY < halfH : event.mouseY >= halfH);

            int boxColor = colorStyle.isToggled() ? getClassColor(player.classType(), player.dead()) : backgroundColor.getRGB();
            if (hovered) {
                boxColor = lighten(boxColor, 0.12f);
            }

            event.context.fill(x, y, x + scaledWidth, y + scaledHeight, boxColor);

            int face = Math.max(10, (int) Math.round(scaledHeight * 0.76));
            int faceX = x + Math.max(4, (int) Math.round(9 * scale.getInput()));
            int faceY = y + Math.max(4, (int) Math.round(9 * scale.getInput()));
            drawPlayerFace(event, player.name(), faceX, faceY, face);

            String primary = onlyClass.isToggled() ? classDisplayName(player.classType()) : player.name();
            String secondary;
            if (player.dead()) {
                secondary = "DEAD";
            } else if (onlyClass.isToggled()) {
                secondary = "Lvl " + player.classLevel();
            } else {
                secondary = classDisplayName(player.classType()) + (player.classLevel() > 0 ? " " + player.classLevel() : "");
            }

            int textX = x + Math.max(8, (int) Math.round((15 + face) * scale.getInput()));
            int textY1 = y + Math.max(4, (int) Math.round((BOX_HEIGHT / 2.5) * scale.getInput()));
            int textY2 = y + Math.max(8, (int) Math.round((BOX_HEIGHT / 1.7) * scale.getInput()));

            int classColor = getClassTextColor(player.classType(), player.dead());
            int primaryColor = onlyClass.isToggled() ? classColor : 0xFFFFFFFF;
            int secondaryColor = player.dead() ? 0xFFFF5555 : (onlyClass.isToggled() ? 0xFFFFFFFF : classColor);

            event.context.drawText(mc.textRenderer, primary, textX, textY1, primaryColor, true);
            event.context.drawText(mc.textRenderer, secondary, textX, textY2, secondaryColor, true);
        }
    }

    @EventHandler
    public void onSlotClick(SlotClickEvent event) {
        HandledScreen<?> screen = currentLeapScreen();
        if (screen == null) {
            return;
        }

        // Fallback path: if left-click reaches slot click handling, still trigger leap.
        if (!onRelease.isToggled() && event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int mouseX = getMouseXScaled();
            int mouseY = getMouseYScaled();
            int quadrant = getQuadrant(mouseX, mouseY, screen.width, screen.height);
            List<LeapEntry> teammates = getDisplayedLeapEntries(screen);
            LeapEntry target = teammates.get(quadrant);
            triggerLeap(target, screen);
        }

        // Prevent normal inventory pickup interactions while custom leap menu is active.
        event.cancel();
    }

    @EventHandler
    public void onMouseButton(MouseButtonEvent event) {
        HandledScreen<?> screen = currentLeapScreen();
        if (screen == null) {
            return;
        }

        if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }

        if (onRelease.isToggled()) {
            if (event.action == KeyAction.Press) {
                event.cancel();
                return;
            }
            if (event.action != KeyAction.Release) {
                return;
            }
        } else if (event.action != KeyAction.Press) {
            return;
        }

        event.cancel();
        int mouseX = getMouseXScaled();
        int mouseY = getMouseYScaled();
        int quadrant = getQuadrant(mouseX, mouseY, screen.width, screen.height);

        List<LeapEntry> teammates = getDisplayedLeapEntries(screen);
        LeapEntry target = teammates.get(quadrant);
        triggerLeap(target, screen);
    }

    @EventHandler
    public void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press) {
            return;
        }

        HandledScreen<?> screen = currentLeapScreen();
        if (screen == null) {
            return;
        }

        List<LeapEntry> teammates = getDisplayedLeapEntries(screen);
        if (teammates.isEmpty()) {
            return;
        }

        LeapEntry target = null;
        if ((int) keybindMode.getValue() == 0) {
            int[] keybinds = new int[]{
                parseKeyCode(topLeftKeybind),
                parseKeyCode(topRightKeybind),
                parseKeyCode(bottomLeftKeybind),
                parseKeyCode(bottomRightKeybind)
            };

            for (int i = 0; i < keybinds.length; i++) {
                if (keybinds[i] != GLFW.GLFW_KEY_UNKNOWN && keybinds[i] == event.key) {
                    target = teammates.get(i);
                    break;
                }
            }
        } else {
            Map<DungeonUtils.DungeonClassType, Integer> classKeys = new HashMap<>();
            classKeys.put(DungeonUtils.DungeonClassType.ARCHER, parseKeyCode(archerKeybind));
            classKeys.put(DungeonUtils.DungeonClassType.BERSERK, parseKeyCode(berserkerKeybind));
            classKeys.put(DungeonUtils.DungeonClassType.HEALER, parseKeyCode(healerKeybind));
            classKeys.put(DungeonUtils.DungeonClassType.MAGE, parseKeyCode(mageKeybind));
            classKeys.put(DungeonUtils.DungeonClassType.TANK, parseKeyCode(tankKeybind));

            DungeonUtils.DungeonClassType matchedClass = null;
            for (Map.Entry<DungeonUtils.DungeonClassType, Integer> entry : classKeys.entrySet()) {
                if (entry.getValue() != GLFW.GLFW_KEY_UNKNOWN && entry.getValue() == event.key) {
                    matchedClass = entry.getKey();
                    break;
                }
            }

            if (matchedClass != null) {
                for (LeapEntry teammate : teammates) {
                    if (!teammate.isEmpty() && teammate.classType() == matchedClass) {
                        target = teammate;
                        break;
                    }
                }
            }
        }

        if (target == null) {
            return;
        }

        event.cancel();
        triggerLeap(target, screen);
    }

    @EventHandler
    public void onReceiveMessage(ReceiveMessageEvent event) {
        if (!leapAnnounce.isToggled() || !DungeonUtils.isInDungeon()) {
            // Still clear pending on server leap confirmation even when announce is disabled.
            String plain = Utils.stripColor(event.getMessage().getString());
            if (LEAP_ANNOUNCE_REGEX.matcher(plain).find()) {
                clearPendingLeap();
            }
            return;
        }

        String plain = Utils.stripColor(event.getMessage().getString());
        Matcher matcher = LEAP_ANNOUNCE_REGEX.matcher(plain);
        if (!matcher.find()) {
            return;
        }

        clearPendingLeap();

        String name = matcher.group(1);
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatCommand("pc Leaped to " + name + "!");
        }
    }

    private HandledScreen<?> currentLeapScreen() {
        HandledScreen<?> screen = getOpenLeapContainerByTitle();
        if (screen == null) {
            clearPendingLeap();
            return null;
        }

        if (isPendingLeap(screen)) {
            return screen;
        }

        List<LeapEntry> entries = collectLeapEntries(screen);
        for (LeapEntry entry : entries) {
            if (!entry.isEmpty()) {
                return screen;
            }
        }
        return null;
    }

    private HandledScreen<?> getOpenLeapContainerByTitle() {
        if (!isEnabled() || mc == null || mc.currentScreen == null) {
            return null;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            return null;
        }
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler)) {
            return null;
        }

        String title = Utils.stripColor(screen.getTitle().getString()).trim();
        if (!title.equalsIgnoreCase("Spirit Leap") && !title.equalsIgnoreCase("Teleport to Player")) {
            return null;
        }

        return screen;
    }

    public boolean shouldHideOriginalContainer(HandledScreen<?> screen) {
        if (screen == null) {
            return false;
        }
        return currentLeapScreen() == screen;
    }

    private List<LeapEntry> getDisplayedLeapEntries(HandledScreen<?> screen) {
        List<LeapEntry> players = collectLeapEntries(screen);

        int mode = (int) sorting.getValue();
        if (mode == 0) {
            return odinSorting(players);
        }

        if (mode == 1) {
            players.sort(Comparator
                .comparing((LeapEntry p) -> p.classType().name())
                .thenComparing(LeapEntry::name, String.CASE_INSENSITIVE_ORDER));
        } else if (mode == 2) {
            players.sort(Comparator.comparing(LeapEntry::name, String.CASE_INSENSITIVE_ORDER));
        } else if (mode == 3) {
            players = applyCustomSorting(players, customSortingOrder.getValue());
        }

        return toFourEntries(players);
    }

    private List<LeapEntry> collectLeapEntries(HandledScreen<?> screen) {
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
            return toFourEntries(Collections.emptyList());
        }

        Map<String, DungeonUtils.DungeonTeammate> teammateLookup = DungeonUtils.getDungeonTeammateLookup();
        List<LeapEntry> players = new ArrayList<>();

        int max = Math.min(handler.slots.size(), 16);
        for (int slotIndex = 11; slotIndex < max; slotIndex++) {
            ItemStack stack = handler.slots.get(slotIndex).getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String stackName = normalizeName(stack.getName().getString());
            if (stackName.isBlank()) {
                continue;
            }

            DungeonUtils.DungeonTeammate teammate = teammateLookup.get(stackName.toLowerCase(Locale.ROOT));
            DungeonUtils.DungeonClassType classType = teammate == null ? DungeonUtils.DungeonClassType.UNKNOWN : teammate.classType();
            int classLevel = teammate == null ? 0 : teammate.classLevel();
            boolean dead = teammate != null && teammate.dead();

            players.add(new LeapEntry(stackName, classType, classLevel, dead, slotIndex));
        }

        return players;
    }

    private List<LeapEntry> odinSorting(List<LeapEntry> players) {
        List<LeapEntry> sortedByPriority = new ArrayList<>(players);
        sortedByPriority.sort(Comparator.comparingInt(p -> classPriority(p.classType())));

        LeapEntry[] result = new LeapEntry[4];
        List<LeapEntry> secondRound = new ArrayList<>();

        for (LeapEntry player : sortedByPriority) {
            int quadrant = defaultQuadrant(player.classType());
            if (quadrant >= 0 && quadrant < 4 && result[quadrant] == null) {
                result[quadrant] = player;
            } else {
                secondRound.add(player);
            }
        }

        for (int i = 0; i < result.length; i++) {
            if (result[i] == null && !secondRound.isEmpty()) {
                result[i] = secondRound.remove(0);
            }
        }

        List<LeapEntry> out = new ArrayList<>(4);
        for (LeapEntry entry : result) {
            out.add(entry == null ? LeapEntry.emptyEntry() : entry);
        }
        return out;
    }

    private List<LeapEntry> applyCustomSorting(List<LeapEntry> players, String rawOrder) {
        List<String> order = parseCustomOrder(rawOrder);
        if (order.isEmpty()) {
            return toFourEntries(players);
        }

        List<LeapEntry> sorted = new ArrayList<>(players);
        sorted.sort(Comparator
            .comparingInt((LeapEntry p) -> customOrderIndex(order, p.name()))
            .thenComparing(LeapEntry::name, String.CASE_INSENSITIVE_ORDER));
        return toFourEntries(sorted);
    }

    private List<String> parseCustomOrder(String rawOrder) {
        if (rawOrder == null || rawOrder.isBlank()) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        String[] split = rawOrder.split(",");
        for (String token : split) {
            String normalized = normalizeName(token);
            if (!normalized.isBlank()) {
                out.add(normalized.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private int customOrderIndex(List<String> order, String name) {
        String key = normalizeName(name).toLowerCase(Locale.ROOT);
        int index = order.indexOf(key);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private List<LeapEntry> toFourEntries(List<LeapEntry> source) {
        List<LeapEntry> out = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            if (i < source.size()) {
                out.add(source.get(i));
            } else {
                out.add(LeapEntry.emptyEntry());
            }
        }
        return out;
    }

    private void triggerLeap(LeapEntry player, HandledScreen<?> screen) {
        if (player == null || player.isEmpty()) {
            return;
        }

        if (player.dead()) {
            Utils.addChatMessage("This player is dead, can't leap.");
            return;
        }

        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
            return;
        }

        if (mc.interactionManager == null || mc.player == null) {
            return;
        }

        int slotIndex = player.slotIndex();
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) {
            return;
        }

        pendingLeapSyncId = handler.syncId;
        pendingLeapSentAtMs = System.currentTimeMillis();
        mc.interactionManager.clickSlot(handler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
        Utils.addChatMessage("Teleporting to " + player.name() + ".");
    }

    private boolean isPendingLeap(HandledScreen<?> screen) {
        if (screen == null || pendingLeapSyncId < 0 || pendingLeapSentAtMs <= 0L) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - pendingLeapSentAtMs;
        if (elapsed > CLICK_REGISTER_TIMEOUT_MS) {
            clearPendingLeap();
            return false;
        }

        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
            clearPendingLeap();
            return false;
        }

        if (handler.syncId != pendingLeapSyncId) {
            clearPendingLeap();
            return false;
        }

        return true;
    }

    private void clearPendingLeap() {
        pendingLeapSyncId = -1;
        pendingLeapSentAtMs = 0L;
    }

    private void drawPlayerFace(RenderScreenEvent event, String playerName, int x, int y, int size) {
        if (mc == null || mc.getNetworkHandler() == null || playerName == null || playerName.isBlank()) {
            return;
        }

        PlayerListEntry entry = null;
        for (PlayerListEntry playerEntry : mc.getNetworkHandler().getPlayerList()) {
            if (playerEntry == null || playerEntry.getProfile() == null || playerEntry.getProfile().name() == null) {
                continue;
            }
            if (playerEntry.getProfile().name().equalsIgnoreCase(playerName)) {
                entry = playerEntry;
                break;
            }
        }

        if (entry == null) {
            return;
        }

        try {
            SkinTextures textures = entry.getSkinTextures();
            if (textures != null) {
                PlayerSkinDrawer.draw(event.context, textures, x, y, size, -1);
            }
        } catch (Throwable ignored) {
        }
    }

    private int getQuadrant(int mouseX, int mouseY, int width, int height) {
        int q = mouseY >= height / 2 ? 2 : 0;
        if (mouseX >= width / 2) {
            q += 1;
        }
        return q;
    }

    private int getMouseXScaled() {
        return (int) Math.round(mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth());
    }

    private int getMouseYScaled() {
        return (int) Math.round(mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight());
    }

    private static int classPriority(DungeonUtils.DungeonClassType clazz) {
        return switch (clazz) {
            case HEALER -> 0;
            case MAGE -> 1;
            case BERSERK -> 2;
            case ARCHER -> 3;
            case TANK -> 4;
            case DEAD, UNKNOWN -> 5;
        };
    }

    private static int defaultQuadrant(DungeonUtils.DungeonClassType clazz) {
        return switch (clazz) {
            case HEALER -> 0;
            case MAGE -> 1;
            case BERSERK -> 2;
            case ARCHER -> 3;
            case TANK -> 3;
            case DEAD, UNKNOWN -> -1;
        };
    }

    private static String classDisplayName(DungeonUtils.DungeonClassType clazz) {
        return switch (clazz) {
            case ARCHER -> "Archer";
            case BERSERK -> "Berserker";
            case HEALER -> "Healer";
            case MAGE -> "Mage";
            case TANK -> "Tank";
            case DEAD -> "Dead";
            case UNKNOWN -> "Unknown";
        };
    }

    private static int getClassColor(DungeonUtils.DungeonClassType clazz, boolean dead) {
        if (dead) {
            return 0xAA7F1D1D;
        }

        return switch (clazz) {
            case ARCHER -> 0xAA2E8B57;
            case BERSERK -> 0xAA8B2E2E;
            case HEALER -> 0xAA8B6A2E;
            case MAGE -> 0xAA2E5A8B;
            case TANK -> 0xAA40603A;
            case DEAD, UNKNOWN -> 0xAA262626;
        };
    }

    private static int getClassTextColor(DungeonUtils.DungeonClassType clazz, boolean dead) {
        if (dead) {
            return 0xFFFF5555;
        }

        return switch (clazz) {
            case ARCHER -> 0xFFFFB347; // orange-yellow
            case MAGE -> 0xFF55AAFF; // blue
            case HEALER -> 0xFFFF66CC; // pink
            case TANK -> 0xFF55CC55; // green
            case BERSERK -> 0xFFFF5555; // red
            case DEAD, UNKNOWN -> 0xFFFFFFFF;
        };
    }

    private static int lighten(int argb, float amount) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        r = Math.min(255, (int) (r + (255 - r) * amount));
        g = Math.min(255, (int) (g + (255 - g) * amount));
        b = Math.min(255, (int) (b + (255 - b) * amount));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String stripped = Utils.stripColor(raw).trim();
        if (stripped.isBlank()) {
            return "";
        }

        return stripped.replaceAll("[^A-Za-z0-9_]", "");
    }

    private int parseKeyCode(InputSetting setting) {
        if (setting == null) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }

        String value = setting.getValue();
        if (value == null) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }

        String key = value.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty() || key.equals("NONE") || key.equals("UNKNOWN")) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }

        if (key.matches("-?\\d+")) {
            try {
                return Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                return GLFW.GLFW_KEY_UNKNOWN;
            }
        }

        if (key.length() == 1) {
            char ch = key.charAt(0);
            if (ch >= 'A' && ch <= 'Z') {
                return GLFW.GLFW_KEY_A + (ch - 'A');
            }
            if (ch >= '0' && ch <= '9') {
                return GLFW.GLFW_KEY_0 + (ch - '0');
            }
        }

        if (key.startsWith("F") && key.length() <= 3) {
            try {
                int fn = Integer.parseInt(key.substring(1));
                if (fn >= 1 && fn <= 25) {
                    return GLFW.GLFW_KEY_F1 + (fn - 1);
                }
            } catch (NumberFormatException ignored) {
                return GLFW.GLFW_KEY_UNKNOWN;
            }
        }

        return switch (key) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "LEFT", "LEFT_ARROW" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT", "RIGHT_ARROW" -> GLFW.GLFW_KEY_RIGHT;
            case "UP", "UP_ARROW" -> GLFW.GLFW_KEY_UP;
            case "DOWN", "DOWN_ARROW" -> GLFW.GLFW_KEY_DOWN;
            case "LSHIFT", "LEFT_SHIFT", "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RSHIFT", "RIGHT_SHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "LCTRL", "LEFT_CTRL", "CTRL", "CONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RCTRL", "RIGHT_CTRL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "LALT", "LEFT_ALT", "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "RALT", "RIGHT_ALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private record LeapEntry(String name, DungeonUtils.DungeonClassType classType, int classLevel, boolean dead, int slotIndex) {
        private static final LeapEntry EMPTY = new LeapEntry("", DungeonUtils.DungeonClassType.UNKNOWN, 0, false, -1);

        private static LeapEntry emptyEntry() {
            return EMPTY;
        }

        private boolean isEmpty() {
            return slotIndex < 0 || name == null || name.isBlank();
        }
    }
}
