package com.revampes.Fault.modules.impl.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CroesusLoader {
    private static final Path CROESUS_DIR = FabricLoader.getInstance().getConfigDir().resolve("frosty").resolve("croesus");
    private static final Path WORTHLESS_FILE = CROESUS_DIR.resolve("worthless.json");
    private static final Path ALWAYS_BUY_FILE = CROESUS_DIR.resolve("always_buy.json");
    private static final Path RUN_LOG_FILE = CROESUS_DIR.resolve("run_log.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();
    private static final Type RUN_LOG_TYPE = new TypeToken<List<AutoCroesus.ChestInfo>>() {
    }.getType();

    private static List<String> worthless = new ArrayList<>(Arrays.asList(
        "DUNGEON_DISC_5",
        "DUNGEON_DISC_4",
        "DUNGEON_DISC_3",
        "DUNGEON_DISC_2",
        "DUNGEON_DISC_1",
        "MAXOR_THE_FISH",
        "STORM_THE_FISH",
        "GOLDOR_THE_FISH",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_3",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_4",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_5",
        "ENCHANTMENT_ULTIMATE_COMBO_1",
        "ENCHANTMENT_ULTIMATE_COMBO_2",
        "ENCHANTMENT_ULTIMATE_COMBO_3",
        "ENCHANTMENT_ULTIMATE_COMBO_4",
        "ENCHANTMENT_ULTIMATE_COMBO_5",
        "ENCHANTMENT_ULTIMATE_BANK_1",
        "ENCHANTMENT_ULTIMATE_BANK_2",
        "ENCHANTMENT_ULTIMATE_BANK_3",
        "ENCHANTMENT_ULTIMATE_BANK_4",
        "ENCHANTMENT_ULTIMATE_BANK_5",
        "ENCHANTMENT_ULTIMATE_JERRY_1",
        "ENCHANTMENT_ULTIMATE_JERRY_2",
        "ENCHANTMENT_ULTIMATE_JERRY_3",
        "ENCHANTMENT_ULTIMATE_JERRY_4",
        "ENCHANTMENT_ULTIMATE_JERRY_5",
        "ENCHANTMENT_FEATHER_FALLING_6",
        "ENCHANTMENT_FEATHER_FALLING_7",
        "ENCHANTMENT_FEATHER_FALLING_8",
        "ENCHANTMENT_FEATHER_FALLING_9",
        "ENCHANTMENT_FEATHER_FALLING_10",
        "ENCHANTMENT_INFINITE_QUIVER_6",
        "ENCHANTMENT_INFINITE_QUIVER_7",
        "ENCHANTMENT_INFINITE_QUIVER_8",
        "ENCHANTMENT_INFINITE_QUIVER_9",
        "ENCHANTMENT_INFINITE_QUIVER_10",
        "SPIRIT_SHORTBOW",
        "SPIRIT_BOW",
        "ITEM_SPIRIT_BOW",
        "WITHER_BOOTS",
        "WITHER_CHESTPLATE",
        "WITHER_LEGGINGS",
        "WITHER_HELMET",
        "WITHER_CLOAK",
        "AUTO_RECOMBOBULATOR",
        "MASTER_SKULL_TIER_5",
        "MASTER_SKULL_TIER_4",
        "SHADOW_ASSASSIN_BOOTS",
        "SHADOW_ASSASSIN_LEGGINGS",
        "SHADOW_ASSASSIN_CHESTPLATE",
        "SHADOW_ASSASSIN_HELMET",
        "WARPED_STONE"
    ));

    private static List<String> alwaysBuy = new ArrayList<>(Arrays.asList(
        "NECRON_HANDLE",
        "DARK_CLAYMORE",
        "FIRST_MASTER_STAR",
        "SECOND_MASTER_STAR",
        "THIRD_MASTER_STAR",
        "FOURTH_MASTER_STAR",
        "FIFTH_MASTER_STAR",
        "SHADOW_FURY",
        "SHADOW_WARP_SCROLL",
        "IMPLOSION_SCROLL",
        "WITHER_SHIELD_SCROLL",
        "DYE_LIVID"
    ));

    private static List<AutoCroesus.ChestInfo> runLog = new ArrayList<>();

    private CroesusLoader() {
    }

    public static void load() {
        worthless = readOrDefault(WORTHLESS_FILE, STRING_LIST_TYPE, worthless);
        alwaysBuy = readOrDefault(ALWAYS_BUY_FILE, STRING_LIST_TYPE, alwaysBuy);
        runLog = readOrDefault(RUN_LOG_FILE, RUN_LOG_TYPE, runLog);

        saveWorthless();
        saveAlwaysBuy();
        saveRunLog();
    }

    public static void saveWorthless() {
        writeSafe(WORTHLESS_FILE, worthless);
    }

    public static void saveAlwaysBuy() {
        writeSafe(ALWAYS_BUY_FILE, alwaysBuy);
    }

    public static boolean addRunLog(AutoCroesus.ChestInfo info) {
        if (info == null || info.items.isEmpty()) {
            return false;
        }

        runLog.add(info);
        saveRunLog();
        return true;
    }

    public static void saveRunLog() {
        writeSafe(RUN_LOG_FILE, runLog);
    }

    public static List<String> getWorthless() {
        return worthless;
    }

    public static List<String> getAlwaysBuy() {
        return alwaysBuy;
    }

    public static List<AutoCroesus.ChestInfo> getRunLog() {
        return runLog;
    }

    private static <T> T readOrDefault(Path path, Type type, T defaults) {
        try {
            Files.createDirectories(CROESUS_DIR);
            if (!Files.exists(path)) {
                return defaults;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                T parsed = GSON.fromJson(reader, type);
                return parsed != null ? parsed : defaults;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            return defaults;
        }
    }

    private static void writeSafe(Path path, Object value) {
        try {
            Files.createDirectories(CROESUS_DIR);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(value, writer);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}