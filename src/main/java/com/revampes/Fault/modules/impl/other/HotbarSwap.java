package com.revampes.Fault.modules.impl.other;

import com.revampes.Fault.events.impl.KeyEvent;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.RenderScreenEvent;
import com.revampes.Fault.modules.Module;
import com.aftertime.ratallofyou.utils.HotbarSwapUtils;
import com.revampes.Fault.utility.Utils;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HotbarSwap extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public static HotbarSwap INSTANCE;

    private final List<Hotbar> presets = new ArrayList<>();
    private final Map<String, Hotbar> msgTriggers = new HashMap<>();
    private final Map<Integer, Hotbar> keyTriggers = new HashMap<>();
    private final Set<Integer> recentlyInteracted = new HashSet<>();

    private int moveCD = 0;

    private final File presetsFile = new File(new File(mc.runDirectory, "config"), "Revampes/hotbar_presets.json");

    public HotbarSwap() {
        super("HotbarSwap", category.Other);
        INSTANCE = this;
        loadFromDisk();
        indexTriggers();
    }

    private boolean isModuleEnabled() { return isEnabled(); }

    @EventHandler
    public void onPreUpdate(PreUpdateEvent ev) {
        if (moveCD > 0) {
            HotbarSwapUtils.stopInputs();
            moveCD--; 
            if (moveCD == 0) HotbarSwapUtils.restartMovement();
        }
    }

    public boolean tryTriggerLocal(String text) {
        if (!isModuleEnabled()) return false;
        if (text == null) return false;
        String s = text.trim();
        if (s.isEmpty()) return false;
        Hotbar p = msgTriggers.get(s);
        if (p != null) { loadPreset(p.name); return true; }
        if (s.startsWith("/")) {
            String nameOnly = s.substring(1).trim();
            int sp = nameOnly.indexOf(' ');
            if (sp > 0) nameOnly = nameOnly.substring(0, sp);
            p = msgTriggers.get("/" + nameOnly);
            if (p != null) { loadPreset(p.name); return true; }
        }
        return false;
    }

    private void loadPreset(String presetName) {
        if (presetName == null || mc.player == null) return;
        if (moveCD > 0) return;

        Hotbar target = findPreset(presetName, true);
        if (target == null) { Utils.addChatMessage("Preset of " + presetName + " doesn't exist!"); return; }

        // Perform swaps sequentially per-slot, pausing player movement for `perItemDelayMs` per item.
        final int perItemDelayMs = 300; // 0.3 seconds per item as requested
        final int ticks = (perItemDelayMs + 49) / 50; // convert ms -> ticks (50ms per tick)

        recentlyInteracted.clear();
        for (int i = 0; i < 9; i++) {
            final int slotIndex = i;
            long baseDelay = (long) slotIndex * perItemDelayMs;
            schedule(() -> {
                try {
                    if (mc.player == null) return;
                    // stop inputs and set moveCD for this item's duration
                    HotbarSwapUtils.stopInputs();
                    moveCD = ticks;

                    // three-phase attempt for this slot (hotbar-only, allow main inventory, finalize)
                    recentlyInteracted.clear();
                    setSlot(slotIndex, target.slots.get(slotIndex), true);

                    schedule(() -> {
                        recentlyInteracted.clear();
                        setSlot(slotIndex, target.slots.get(slotIndex), false);
                    }, 50);

                    schedule(() -> {
                        recentlyInteracted.clear();
                        setSlot(slotIndex, target.slots.get(slotIndex), true);
                    }, 100);
                } catch (Throwable ignored) {}
            }, baseDelay);
        }
    }

    private Hotbar findPreset(String name, boolean fuzzy) {
        if (name == null) return null;
        String q = name.toLowerCase(Locale.ROOT);
        for (Hotbar p : presets) {
            String pn = p.name.toLowerCase(Locale.ROOT);
            if (!fuzzy && pn.equals(q)) return p;
            if (fuzzy && pn.contains(q)) return p;
        }
        return null;
    }

    private ItemStack getStackInInventorySlot(int invIndex) {
        if (mc.player == null) return ItemStack.EMPTY;
        if (invIndex < 0 || invIndex >= mc.player.getInventory().size()) return ItemStack.EMPTY;
        return mc.player.getInventory().getStack(invIndex);
    }

    private boolean compareUUID(HotbarItem hotbarItem, ItemStack stack) {
        if (hotbarItem.uuid == null) return false;
        String uuid = HotbarSwapUtils.getUUID(stack);
        if (uuid != null && hotbarItem.uuid.equals(uuid)) {
            String name = HotbarSwapUtils.getDisplayName(stack);
            return eqIgnoreCase(hotbarItem.name, name);
        }
        // fallback: compare alternative custom-data id parsed from stack.toString()
        String alt = Utils.getCustomDataIId(stack.toString());
        if (alt != null && !alt.isEmpty() && hotbarItem.uuid.equals(alt)) {
            String name = HotbarSwapUtils.getDisplayName(stack);
            return eqIgnoreCase(hotbarItem.name, name);
        }
        return false;
    }

    private boolean compareSkyblockID(HotbarItem hotbarItem, ItemStack stack) {
        String id = HotbarSwapUtils.getSkyblockID(stack);
        if (id == null || hotbarItem.id == null) return false;
        return hotbarItem.id.equalsIgnoreCase(id);
    }

    private int findEmptyMainSlot() {
        for (int i = 9; i < mc.player.getInventory().size(); i++) {
            if (recentlyInteracted.contains(i)) continue;
            if (getStackInInventorySlot(i).isEmpty()) return i;
        }
        return HotbarSwapUtils.NOT_FOUND;
    }

    private int findMatchingItem(HotbarItem hotbarItem, int targetHotbarSlot, boolean includeHotbar) {
        if (hotbarItem == null) return HotbarSwapUtils.NOT_FOUND;

        ItemStack inTarget = getStackInInventorySlot(targetHotbarSlot);
        if (!inTarget.isEmpty() && (compareUUID(hotbarItem, inTarget) || compareSkyblockID(hotbarItem, inTarget))) {
            return HotbarSwapUtils.NOT_FOUND;
        }

        boolean hasIdentifiers = hotbarItem.id != null || hotbarItem.uuid != null;
        boolean hasName = hotbarItem.name != null && !hotbarItem.name.equalsIgnoreCase("None");

        int start = includeHotbar ? 0 : 9;
        for (int i = start; i < mc.player.getInventory().size(); i++) {
            if (i == targetHotbarSlot) continue;
            if (recentlyInteracted.contains(i)) continue;
            ItemStack s = getStackInInventorySlot(i);
            if (s.isEmpty()) continue;
            boolean match = false;
            if (hasIdentifiers) match = compareUUID(hotbarItem, s) || compareSkyblockID(hotbarItem, s);
            if (!match && hasName) {
                String display = HotbarSwapUtils.getDisplayName(s);
                match = eqIgnoreCase(hotbarItem.name, display);
            }
            if (match) return i;
        }
        return HotbarSwapUtils.NOT_FOUND;
    }

    private void setSlot(int hotbarSlot, HotbarItem item, boolean hotbarSort) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;

        if (item == null) {
            clearSlot(hotbarSlot);
            return;
        }

        int itemSlot = findMatchingItem(item, hotbarSlot, hotbarSort);
        if (itemSlot == HotbarSwapUtils.NOT_FOUND) return;
        if (recentlyInteracted.contains(hotbarSlot)) return;
        if (hotbarSort && !(itemSlot >= 0 && itemSlot <= 8)) return;

        recentlyInteracted.add(itemSlot);

        int containerSlotId = getContainerSlotIdForInvIndex(itemSlot);
        if (containerSlotId == HotbarSwapUtils.NOT_FOUND) return;
        performHotbarSwapClick(containerSlotId, hotbarSlot);
    }

    private void clearSlot(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;
        ItemStack cur = getStackInInventorySlot(hotbarSlot);
        if (cur.isEmpty()) return;

        int emptyMain = findEmptyMainSlot();
        if (emptyMain == HotbarSwapUtils.NOT_FOUND) return;
        recentlyInteracted.add(emptyMain);

        int containerSlotId = getContainerSlotIdForInvIndex(emptyMain);
        if (containerSlotId == HotbarSwapUtils.NOT_FOUND) return;
        performHotbarSwapClick(containerSlotId, hotbarSlot);
    }

    private void performHotbarSwapClick(int containerSlotId, int targetHotbarIndex) {
        try {
            if (mc.player == null || mc.player.currentScreenHandler == null || mc.getNetworkHandler() == null) return;
            net.minecraft.screen.ScreenHandler handler = mc.player.currentScreenHandler;
            int packetRevision = handler.getRevision();
            net.minecraft.screen.sync.ItemStackHash emptyCursorHash = net.minecraft.screen.sync.ItemStackHash.fromItemStack(net.minecraft.item.ItemStack.EMPTY, component -> component.hashCode());
            net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket packet = new net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket(
                    handler.syncId,
                    packetRevision,
                    (short) containerSlotId,
                    (byte) targetHotbarIndex,
                    SlotActionType.SWAP,
                    it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(),
                    emptyCursorHash
            );
            mc.getNetworkHandler().sendPacket(packet);
        } catch (Throwable ignored) { }
    }

    private int getContainerSlotIdForInvIndex(int invIndex) {
        try {
            if (mc.player == null || mc.player.currentScreenHandler == null) return HotbarSwapUtils.NOT_FOUND;
            net.minecraft.screen.ScreenHandler handler = mc.player.currentScreenHandler;
            ItemStack target = mc.player.getInventory().getStack(invIndex);
            String targetUuid = HotbarSwapUtils.getUUID(target);
            String targetId = HotbarSwapUtils.getSkyblockID(target);
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot s = handler.getSlot(i);
                if (s == null) continue;
                if (s.inventory == mc.player.getInventory()) {
                    ItemStack sStack = s.getStack();
                    if (sStack.isEmpty() && target.isEmpty()) return i;
                    String sUuid = HotbarSwapUtils.getUUID(sStack);
                    String sId = HotbarSwapUtils.getSkyblockID(sStack);
                    if (targetUuid != null && targetUuid.equals(sUuid)) return i;
                    if (targetId != null && targetId.equalsIgnoreCase(sId)) return i;
                    // fallback: same item and same display name
                    if (!sStack.isEmpty() && !target.isEmpty() && sStack.getItem() == target.getItem() && HotbarSwapUtils.getDisplayName(sStack).equals(HotbarSwapUtils.getDisplayName(target))) return i;
                }
            }
        } catch (Throwable ignored) {}
        return HotbarSwapUtils.NOT_FOUND;
    }

    private void indexTriggers() {
        msgTriggers.clear();
        keyTriggers.clear();
        for (Hotbar p : presets) {
            if (p.message != null && !p.message.trim().isEmpty()) {
                String msg = p.message.trim();
                msgTriggers.put(msg, p);
                if (msg.startsWith("/")) {
                    String nameOnly = msg.substring(1).trim();
                    int sp = nameOnly.indexOf(' ');
                    if (sp > 0) nameOnly = nameOnly.substring(0, sp);
                    if (!nameOnly.isEmpty()) msgTriggers.put("/" + nameOnly, p);
                }
            }
            if (p.keyCode != null && p.keyCode > 0) keyTriggers.put(p.keyCode, p);
        }
    }

    private String generateDefaultName() {
        int i = 1;
        while (true) {
            String cand = "Preset " + i;
            if (findPreset(cand, false) == null) return cand;
            i++;
        }
    }

    public synchronized int addPresetFromCurrentHotbar() {
        String name = generateDefaultName();
        List<HotbarItem> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) list.add(new HotbarItem(getStackInInventorySlot(i)));
        Hotbar hb = new Hotbar(name, list, null, null, -1);
        presets.add(hb);
        saveToDisk(); indexTriggers();
        return presets.size() - 1;
    }

    public void saveCurrentHotbar(String name) {
        List<HotbarItem> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) list.add(new HotbarItem(getStackInInventorySlot(i)));
        Hotbar hb = new Hotbar(name, list);
        presets.add(hb);
        saveToDisk(); indexTriggers();
    }

    // Public wrapper so external callers (commands) can trigger a preset by name.
    public void loadPresetByName(String name) {
        loadPreset(name);
    }

    public void deletePreset(String name) {
        Hotbar target = findPreset(name, true);
        if (target == null) { Utils.addChatMessage("Couldn't find a preset matching your search :("); return; }
        presets.remove(target);
        saveToDisk(); indexTriggers();
        Utils.addChatMessage("Removed preset " + target.name);
    }

    private void printPreset(Hotbar p) {
        Utils.addChatMessage("§c------------------------------");
        Utils.addChatMessage("§a§lPreset Name:§r " + p.name);
        Utils.addChatMessage("§a§lTrigger Message:§r " + (p.message != null ? p.message : "None"));
        Utils.addChatMessage("§a§lTrigger Command:§r " + (p.command != null ? p.command : "None"));
        String keyStr = (p.keyCode != null && p.keyCode > 0) ? String.valueOf(p.keyCode) : "None";
        Utils.addChatMessage("§a§lKeybind:§r " + keyStr);
        for (int i = 0; i < p.slots.size(); i++) {
            String nm = p.slots.get(i).name != null ? p.slots.get(i).name : "None";
            Utils.addChatMessage("§bItem in slot §a" + (i + 1) + ":§r " + nm);
        }
        Utils.addChatMessage("§c------------------------------");
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) { if (i > from) sb.append(' '); sb.append(args[i]); }
        return sb.toString();
    }

    private boolean eqIgnoreCase(String a, String b) { if (a == null || b == null) return false; return a.equalsIgnoreCase(b); }

    private void schedule(Runnable r, long delayMs) {
        try { new Timer().schedule(new TimerTask() { @Override public void run() { mc.execute(r); } }, Math.max(0, delayMs)); } catch (Throwable ignored) {}
    }

    private void ensureFile() {
        try {
            File parent = presetsFile.getParentFile(); if (!parent.exists()) parent.mkdirs();
            if (!presetsFile.exists()) presetsFile.createNewFile();
        } catch (IOException ignored) { }
    }

    private void saveToDisk() {
        ensureFile();
        try {
            String json = serializePresets();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(presetsFile), StandardCharsets.UTF_8)) { w.write(json); }
        } catch (Exception ignored) { }
    }

    public synchronized java.util.List<Hotbar> getPresetsView() {
        return java.util.Collections.unmodifiableList(presets);
    }

    private void loadFromDisk() {
        presets.clear(); ensureFile();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(presetsFile), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            String json = sb.toString().trim(); if (json.isEmpty()) return; deserializePresets(json);
        } catch (Exception ignored) { }
    }

    private String serializePresets() {
        StringBuilder sb = new StringBuilder(); sb.append('{').append("\"presets\":[");
        for (int i = 0; i < presets.size(); i++) {
            Hotbar p = presets.get(i); if (i > 0) sb.append(',');
            sb.append('{'); sb.append("\"name\":\"").append(escape(p.name)).append("\",");
            sb.append("\"message\":").append(p.message == null ? "null" : ("\"" + escape(p.message) + "\""));
            sb.append(',').append("\"command\":").append(p.command == null ? "null" : ("\"" + escape(p.command) + "\""));
            sb.append(',').append("\"key\":").append(p.keyCode == null ? -1 : p.keyCode.intValue());
            sb.append(',').append("\"slots\":[");
            for (int j = 0; j < p.slots.size(); j++) {
                HotbarItem it = p.slots.get(j); if (j > 0) sb.append(',');
                sb.append('{').append("\"uuid\":").append(it.uuid == null ? "null" : ("\"" + escape(it.uuid) + "\""))
                        .append(',').append("\"id\":").append(it.id == null ? "null" : ("\"" + escape(it.id) + "\""))
                        .append(',').append("\"name\":").append(it.name == null ? "null" : ("\"" + escape(it.name) + "\""))
                        .append('}');
            }
            sb.append(']'); sb.append('}');
        }
        sb.append(']'); sb.append('}'); return sb.toString();
    }

    private void deserializePresets(String json) {
        try {
            presets.clear(); int arrStart = json.indexOf("\"presets\""); if (arrStart < 0) return; int bracket = json.indexOf('[', arrStart); int end = json.lastIndexOf(']'); if (bracket < 0 || end < 0 || end <= bracket) return; String arr = json.substring(bracket + 1, end).trim(); if (arr.isEmpty()) return; List<String> objs = splitTopLevel(arr);
            for (String obj : objs) {
                String name = extractNullableString(obj, "name"); String message = extractNullableString(obj, "message"); String command = extractNullableString(obj, "command"); Integer key = extractNullableInt(obj, "key"); if (key == null) key = -1;
                List<HotbarItem> slots = new ArrayList<>(); String slotsArr = extractArray(obj, "slots"); if (slotsArr != null && !slotsArr.isEmpty()) { List<String> slotObjs = splitTopLevel(slotsArr); for (String so : slotObjs) { String uuid = extractNullableString(so, "uuid"); String id = extractNullableString(so, "id"); String nm = extractNullableString(so, "name"); slots.add(new HotbarItem(uuid, id, nm)); } }
                if (name != null && slots.size() == 9) presets.add(new Hotbar(name, slots, message, command, key));
            }
        } catch (Throwable ignored) {}
        indexTriggers();
    }

    private Integer extractNullableInt(String obj, String key) { try { String needle = "\"" + key + "\":"; int idx = obj.indexOf(needle); if (idx < 0) return null; int valStart = idx + needle.length(); if (obj.startsWith("null", valStart)) return null; int i = valStart; StringBuilder sb = new StringBuilder(); while (i < obj.length()) { char c = obj.charAt(i); if ((c >= '0' && c <= '9') || c == '-') { sb.append(c); i++; continue; } break; } if (sb.length() == 0) return null; try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException nfe) { return null; } } catch (Throwable ignored) { return null; } }

    private String extractNullableString(String obj, String key) { try { String needle = "\"" + key + "\":"; int idx = obj.indexOf(needle); if (idx < 0) return null; int valStart = idx + needle.length(); if (obj.startsWith("null", valStart)) return null; int q1 = obj.indexOf('\"', valStart); if (q1 < 0) return null; int q2 = obj.indexOf('\"', q1 + 1); if (q2 < 0) return null; return unescape(obj.substring(q1 + 1, q2)); } catch (Throwable ignored) { return null; } }

    private String extractArray(String obj, String key) { try { String needle = "\"" + key + "\":"; int idx = obj.indexOf(needle); if (idx < 0) return null; int valStart = obj.indexOf('[', idx + needle.length()); if (valStart < 0) return null; int depth = 0; for (int i = valStart; i < obj.length(); i++) { char c = obj.charAt(i); if (c == '[') depth++; else if (c == ']') { depth--; if (depth == 0) return obj.substring(valStart + 1, i); } } return null; } catch (Throwable ignored) { return null; } }

    private List<String> splitTopLevel(String arr) { List<String> out = new ArrayList<>(); int depth = 0; int start = 0; for (int i = 0; i < arr.length(); i++) { char c = arr.charAt(i); if (c == '{' || c == '[') depth++; else if (c == '}' || c == ']') depth--; else if (c == ',' && depth == 0) { out.add(arr.substring(start, i).trim()); start = i + 1; } } if (start < arr.length()) out.add(arr.substring(start).trim()); return out; }

    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private String unescape(String s) { return s.replace("\\\"", "\"").replace("\\\\", "\\"); }

    public static class Hotbar { public String name; public List<HotbarItem> slots; public String message; public String command; public Integer keyCode; public Hotbar() {} public Hotbar(String name, List<HotbarItem> slots) { this.name = name; this.slots = slots; this.message = null; this.command = null; this.keyCode = -1; } public Hotbar(String name, List<HotbarItem> slots, String message) { this.name = name; this.slots = slots; this.message = message; this.command = null; this.keyCode = -1; } public Hotbar(String name, List<HotbarItem> slots, String message, String command, Integer keyCode) { this.name = name; this.slots = slots; this.message = message; this.command = command; this.keyCode = keyCode == null ? -1 : keyCode; } }

    public static class HotbarItem {
        public String uuid;
        public String id;
        public String name;

        public HotbarItem() {}

        public HotbarItem(ItemStack stack) {
            this.uuid = HotbarSwapUtils.getUUID(stack);
            this.id = HotbarSwapUtils.getSkyblockID(stack);
            this.name = stack != null ? HotbarSwapUtils.getDisplayName(stack) : "None";
            // fallback: if UUID missing, try parsing custom data id from stack string
            if ((this.uuid == null || this.uuid.isEmpty()) && stack != null) {
                String alt = Utils.getCustomDataIId(stack.toString());
                if (alt != null && !alt.isEmpty()) this.uuid = alt;
            }
        }

        public HotbarItem(String uuid, String id, String name) { this.uuid = uuid; this.id = id; this.name = name; }
    }
}
