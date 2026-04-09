package com.revampes.Fault.modules.impl.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.revampes.Fault.utility.Utils;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CroesusPriceService {
    private static final String LOWEST_BINS_URL = "https://api.odtheking.com/lb/lowestbins";
    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";

    private static final Map<String, Double> binCache = new ConcurrentHashMap<>();
    private static final Map<String, Double> bazaarCache = new ConcurrentHashMap<>();
    private static final Map<String, String> itemNameToId = new ConcurrentHashMap<>();

    private static volatile long lastFetchedAt = 0L;
    private static volatile boolean updating = false;

    private CroesusPriceService() {
    }

    public static boolean hasFreshData(long maxAgeMs) {
        return !binCache.isEmpty() && System.currentTimeMillis() - lastFetchedAt <= maxAgeMs;
    }

    public static boolean isUpdating() {
        return updating;
    }

    public static double getSellPrice(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0.0;
        }

        Double bazaarPrice = bazaarCache.get(itemId);
        if (bazaarPrice != null && bazaarPrice > 0.0) {
            return bazaarPrice;
        }

        Double binPrice = binCache.get(itemId);
        if (binPrice != null && binPrice > 0.0) {
            return binPrice;
        }

        return 0.0;
    }

    public static String findItemIdByName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        String normalized = normalizeName(itemName);
        if (normalized.isBlank()) {
            return null;
        }

        String direct = itemNameToId.get(normalized);
        if (direct != null) {
            return direct;
        }

        String withoutArticles = normalized
            .replace(" a ", " ")
            .replace(" an ", " ")
            .replace(" the ", " ")
            .trim();

        return itemNameToId.get(withoutArticles);
    }

    public static void updateAsync(Runnable onComplete) {
        synchronized (CroesusPriceService.class) {
            if (updating) {
                return;
            }
            updating = true;
        }

        Thread thread = new Thread(() -> {
            try {
                refreshNow();
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                synchronized (CroesusPriceService.class) {
                    updating = false;
                }

                if (onComplete != null) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        client.execute(onComplete);
                    } else {
                        onComplete.run();
                    }
                }
            }
        }, "AutoCroesus-PriceUpdater");

        thread.setDaemon(true);
        thread.start();
    }

    private static void refreshNow() {
        Map<String, Double> bins = fetchLowestBins();
        Map<String, Double> bazaar = fetchBazaar();
        Map<String, String> itemNames = fetchItemNames();

        if (!bins.isEmpty()) {
            binCache.clear();
            binCache.putAll(bins);
        }

        if (!bazaar.isEmpty()) {
            bazaarCache.clear();
            bazaarCache.putAll(bazaar);
        }

        if (!itemNames.isEmpty()) {
            itemNameToId.clear();
            itemNameToId.putAll(itemNames);
        }

        if (!bins.isEmpty()) {
            lastFetchedAt = System.currentTimeMillis();
        }
    }

    private static Map<String, Double> fetchLowestBins() {
        Map<String, Double> prices = new HashMap<>();

        JsonObject root = readJsonObject(LOWEST_BINS_URL);
        if (root == null) {
            return prices;
        }

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonElement element = entry.getValue();
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                prices.put(entry.getKey(), element.getAsDouble());
            }
        }

        return prices;
    }

    private static Map<String, Double> fetchBazaar() {
        Map<String, Double> prices = new HashMap<>();

        JsonObject root = readJsonObject(BAZAAR_URL);
        if (root == null || !root.has("products")) {
            return prices;
        }

        JsonObject products = root.getAsJsonObject("products");
        for (Map.Entry<String, JsonElement> productEntry : products.entrySet()) {
            JsonElement productElement = productEntry.getValue();
            if (productElement == null || !productElement.isJsonObject()) {
                continue;
            }

            JsonObject product = productElement.getAsJsonObject();
            if (!product.has("quick_status")) {
                continue;
            }

            JsonObject quickStatus = product.getAsJsonObject("quick_status");
            if (quickStatus.has("sellPrice") && quickStatus.get("sellPrice").isJsonPrimitive()) {
                prices.put(productEntry.getKey(), quickStatus.get("sellPrice").getAsDouble());
            }
        }

        return prices;
    }

    private static Map<String, String> fetchItemNames() {
        Map<String, String> names = new HashMap<>();

        JsonObject root = readJsonObject(ITEMS_URL);
        if (root == null || !root.has("items")) {
            return names;
        }

        JsonArray items = root.getAsJsonArray("items");
        for (JsonElement element : items) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }

            JsonObject item = element.getAsJsonObject();
            if (!item.has("id") || !item.has("name")) {
                continue;
            }

            String id = item.get("id").getAsString();
            String name = item.get("name").getAsString();
            addItemAlias(names, name, id);
        }

        return names;
    }

    private static void addItemAlias(Map<String, String> map, String rawName, String id) {
        if (rawName == null || rawName.isBlank() || id == null || id.isBlank()) {
            return;
        }

        String normalized = normalizeName(rawName);
        if (!normalized.isBlank()) {
            map.putIfAbsent(normalized, id);
        }

        String noApostrophes = normalizeName(rawName.replace("'", ""));
        if (!noApostrophes.isBlank()) {
            map.putIfAbsent(noApostrophes, id);
        }
    }

    private static JsonObject readJsonObject(String endpoint) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", "Frosty-AutoCroesus/1.0");

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

            if (stream == null) {
                return null;
            }

            String response = readString(stream);
            if (response == null || response.isBlank()) {
                return null;
            }

            JsonElement root = JsonParser.parseString(response);
            if (!root.isJsonObject()) {
                return null;
            }

            return root.getAsJsonObject();
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readString(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private static String normalizeName(String rawName) {
        String stripped = Utils.stripColor(rawName == null ? "" : rawName);
        String normalized = stripped
            .toLowerCase(Locale.ROOT)
            .replace('\u00A0', ' ')
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return normalized;
    }
}