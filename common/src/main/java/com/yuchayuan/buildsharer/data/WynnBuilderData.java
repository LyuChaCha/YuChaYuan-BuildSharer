package com.yuchayuan.buildsharer.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuchayuan.buildsharer.craft.CraftData;
import com.yuchayuan.buildsharer.encode.AbilityTree;
import com.yuchayuan.buildsharer.encode.BuildEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WynnBuilderData {

    public record AspectInfo(int id, int tiers) {}

    public static final List<String> FILES = List.of(
            "encoding_consts.json", "items.json", "atree.json", "aspects.json", "recipes.json", "ingreds.json");
    public static final String MAPPINGS = "translate_mappings.json";

    private final String version;
    private final JsonObject constants;
    private final BuildEncoder encoder;
    private final Map<String, Integer> items = new HashMap<>();
    private final Map<String, Integer> itemsLoose = new HashMap<>();
    private final Map<String, AbilityTree> trees = new HashMap<>();
    private final Map<String, Map<String, AspectInfo>> aspects = new HashMap<>();
    private final Map<String, String> stats = new HashMap<>();
    private final CraftData crafting;

    public WynnBuilderData(String version, int versionId, Map<String, String> files) {
        this.version = version;
        this.constants = object(files, "encoding_consts.json");
        this.encoder = new BuildEncoder(constants, versionId);
        readItems(object(files, "items.json"));
        readTrees(object(files, "atree.json"));
        readAspects(object(files, "aspects.json"));
        readStats(object(files, MAPPINGS));
        this.crafting = new CraftData(object(files, "recipes.json"), JsonParser.parseString(files.get("ingreds.json")));
    }

    public String version() {
        return version;
    }

    public JsonObject constants() {
        return constants;
    }

    public BuildEncoder encoder() {
        return encoder;
    }

    public CraftData crafting() {
        return crafting;
    }

    public Optional<Integer> item(String name) {
        Integer id = items.get(name);
        if (id == null) {
            id = itemsLoose.get(loose(name));
        }
        return Optional.ofNullable(id);
    }

    public Optional<AbilityTree> tree(String className) {
        return Optional.ofNullable(trees.get(className));
    }

    public Optional<AspectInfo> aspect(String className, String name) {
        Map<String, AspectInfo> byName = aspects.get(className);
        return byName == null ? Optional.empty() : Optional.ofNullable(byName.get(loose(name)));
    }

    public Optional<String> stat(String apiName) {
        return Optional.ofNullable(stats.get(apiName));
    }

    public static List<String> versionNames(String loadItemJs) {
        int at = loadItemJs.indexOf("wynn_version_names");
        int open = at < 0 ? -1 : loadItemJs.indexOf('[', at);
        int close = open < 0 ? -1 : loadItemJs.indexOf(']', open);
        if (close < 0) {
            throw new IllegalStateException("wynn_version_names not found");
        }
        List<String> names = new ArrayList<>();
        for (String part : loadItemJs.substring(open + 1, close).split(",")) {
            String name = part.strip().replace("'", "").replace("\"", "");
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("wynn_version_names is empty");
        }
        return names;
    }

    private void readItems(JsonObject root) {
        for (JsonElement element : root.getAsJsonArray("items")) {
            JsonObject item = element.getAsJsonObject();
            if (!item.has("id")) {
                continue;
            }
            int id = item.get("id").getAsInt();
            String name = item.get("name").getAsString();
            String display = item.has("displayName") ? item.get("displayName").getAsString() : name;
            items.putIfAbsent(display, id);
            itemsLoose.putIfAbsent(loose(display), id);
        }
        for (JsonElement element : root.getAsJsonArray("items")) {
            JsonObject item = element.getAsJsonObject();
            if (item.has("id")) {
                String name = item.get("name").getAsString();
                items.putIfAbsent(name, item.get("id").getAsInt());
                itemsLoose.putIfAbsent(loose(name), item.get("id").getAsInt());
            }
        }
    }

    private void readTrees(JsonObject root) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            List<AbilityTree.Node> nodes = new ArrayList<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                JsonObject node = element.getAsJsonObject();
                List<Integer> parents = new ArrayList<>();
                for (JsonElement parent : node.getAsJsonArray("parents")) {
                    parents.add(parent.getAsInt());
                }
                nodes.add(new AbilityTree.Node(node.get("id").getAsInt(),
                        node.get("display_name").getAsString(), parents));
            }
            if (!nodes.isEmpty()) {
                trees.put(entry.getKey(), new AbilityTree(nodes));
            }
        }
    }

    private void readAspects(JsonObject root) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            Map<String, AspectInfo> byName = new HashMap<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                JsonObject aspect = element.getAsJsonObject();
                JsonArray tiers = aspect.getAsJsonArray("tiers");
                byName.putIfAbsent(loose(aspect.get("displayName").getAsString()),
                        new AspectInfo(aspect.get("id").getAsInt(), tiers == null ? 1 : tiers.size()));
            }
            aspects.put(entry.getKey(), byName);
        }
    }

    private void readStats(JsonObject root) {
        JsonObject ids = root.getAsJsonObject("identifications");
        for (Map.Entry<String, JsonElement> entry : ids.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    private static JsonObject object(Map<String, String> files, String name) {
        String text = files.get(name);
        if (text == null) {
            throw new IllegalStateException("missing " + name);
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    static String loose(String name) {
        return name.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
