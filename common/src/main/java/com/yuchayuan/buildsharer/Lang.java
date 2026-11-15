package com.yuchayuan.buildsharer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Lang {

    private static final String DEFAULT = "en_us";
    private static final String PATH = "/assets/" + BuildSharer.MOD_ID + "/lang/";
    private static final String PREFIX = BuildSharer.MOD_ID + ".";
    private static final Map<String, Map<String, String>> LOADED = new ConcurrentHashMap<>();
    private static final Method WYNNCHAYUAN = wynnChaYuan();

    private Lang() {}

    public static MutableComponent t(String key, Object... args) {
        String pattern = load(language()).get(PREFIX + key);
        if (pattern == null) {
            pattern = load(DEFAULT).getOrDefault(PREFIX + key, PREFIX + key);
        }
        MutableComponent out = Component.empty();
        int at = 0;
        int start = 0;
        for (int i = pattern.indexOf("%s"); i >= 0; i = pattern.indexOf("%s", start)) {
            out.append(pattern.substring(start, i));
            if (at < args.length) {
                Object arg = args[at++];
                out.append(arg instanceof Component component ? component : Component.literal(String.valueOf(arg)));
            }
            start = i + 2;
        }
        return out.append(pattern.substring(start));
    }

    static String language() {
        if (WYNNCHAYUAN == null) {
            return DEFAULT;
        }
        try {
            Object pinned = WYNNCHAYUAN.invoke(null);
            if (pinned instanceof String code && !code.isBlank()) {
                return code.toLowerCase(Locale.ROOT);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return DEFAULT;
        }
        return Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
    }

    private static Method wynnChaYuan() {
        try {
            return Class.forName("com.wynnchayuan.client.T").getMethod("pinnedLanguage");
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Map<String, String> load(String lang) {
        return LOADED.computeIfAbsent(lang, Lang::read);
    }

    private static Map<String, String> read(String lang) {
        try (InputStream in = Lang.class.getResourceAsStream(PATH + lang + ".json")) {
            if (in == null) {
                return Map.of();
            }
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                out.put(entry.getKey(), entry.getValue().getAsString());
            }
            return Map.copyOf(out);
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }
}
