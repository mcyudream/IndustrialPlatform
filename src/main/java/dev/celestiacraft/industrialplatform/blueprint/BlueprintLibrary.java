package dev.celestiacraft.industrialplatform.blueprint;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.celestiacraft.industrialplatform.IndustrialPlatform;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads custom building blueprints from
 * <ul>
 *     <li>{@code config/industrial_platform/blueprints/*.json}</li>
 *     <li>{@code config/industrial_platform/blueprints/*.js}</li>
 *     <li>{@code kubejs/startup_scripts/industrial_platform/*.js}</li>
 * </ul>
 * Javascript files are evaluated with Nashorn (bundled with the Java 8 runtime
 * used by 1.12.2) and register presets through the global {@code blueprints}
 * binding, KubeJS style:
 *
 * <pre>
 * blueprints.register({
 *     name: "玻璃观景台",
 *     border: "minecraft:iron_block",
 *     fill: "minecraft:glass",
 *     intervalX: 20,
 *     intervalZ: 20
 * });
 * </pre>
 */
public final class BlueprintLibrary {

    private static final List<Blueprint> BLUEPRINTS = new ArrayList<Blueprint>();

    private BlueprintLibrary() {
    }

    public static List<Blueprint> all() {
        return Collections.unmodifiableList(BLUEPRINTS);
    }

    public static int load(File gameDir) {
        return load(gameDir, "unspecified");
    }

    public static synchronized int load(File gameDir, String source) {
        List<Blueprint> loaded = new ArrayList<Blueprint>();

        File jsonDir = new File(gameDir, "config/industrial_platform/blueprints");
        loadJsonFolder(jsonDir, loaded);
        loadJsFolder(jsonDir, loaded);
        loadJsFolder(new File(gameDir, "kubejs/startup_scripts/industrial_platform"), loaded);

        Collections.sort(loaded, (a, b) -> a.name.compareToIgnoreCase(b.name));

        BLUEPRINTS.clear();
        BLUEPRINTS.addAll(loaded);
        if (IndustrialPlatform.LOGGER != null) {
            IndustrialPlatform.LOGGER.info("Loaded {} platform blueprint(s) [{}]", BLUEPRINTS.size(), source);
        }
        return BLUEPRINTS.size();
    }

    // ------------------------------------------------------------------ JSON

    private static void loadJsonFolder(File folder, List<Blueprint> out) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".json")) {
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonElement element = new JsonParser().parse(reader);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                Map<String, Object> data = toPlainMap(obj);
                Object name = data.get("name");
                if (name == null) {
                    data.put("name", file.getName().replaceFirst("\\.json$", ""));
                }
                out.add(new Blueprint(String.valueOf(name), data));
            } catch (Exception e) {
                IndustrialPlatform.LOGGER.error("Failed to load blueprint {}", file.getName(), e);
            }
        }
    }

    private static Map<String, Object> toPlainMap(JsonObject obj) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isBoolean()) {
                    map.put(entry.getKey(), value.getAsBoolean());
                } else if (value.getAsJsonPrimitive().isNumber()) {
                    map.put(entry.getKey(), value.getAsNumber());
                } else {
                    map.put(entry.getKey(), value.getAsString());
                }
            } else if (value.isJsonObject()) {
                map.put(entry.getKey(), toPlainMap(value.getAsJsonObject()));
            }
        }
        return map;
    }

    // ------------------------------------------------------------------ Javascript

    private static void loadJsFolder(File folder, List<Blueprint> out) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        ScriptEngine engine = null;
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".js")) {
                continue;
            }
            if (engine == null) {
                engine = new ScriptEngineManager(null).getEngineByName("javascript");
                if (engine == null) {
                    IndustrialPlatform.LOGGER.warn("No javascript engine available, skipping .js blueprints");
                    return;
                }
            }
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                Bindings bindings = engine.createBindings();
                bindings.put("blueprints", new Api(out, file.getName()));
                engine.eval(reader, bindings);
            } catch (Exception e) {
                IndustrialPlatform.LOGGER.error("Failed to evaluate blueprint script {}", file.getName(), e);
            }
        }
    }

    /**
     * Binding exposed to scripts as the global {@code blueprints} object.
     */
    public static final class Api {

        private final List<Blueprint> out;
        private final String source;

        Api(List<Blueprint> out, String source) {
            this.out = out;
            this.source = source;
        }

        public void register(Object blueprint) {
            if (blueprint instanceof Map) {
                Map<String, Object> data = toPlain((Map<?, ?>) blueprint);
                Object name = data.get("name");
                if (name == null) {
                    data.put("name", source.replaceFirst("\\.js$", ""));
                }
                out.add(new Blueprint(String.valueOf(name), data));
            } else {
                IndustrialPlatform.LOGGER.warn("blueprints.register(...) expects an object literal");
            }
        }

        public void register(String name, Object blueprint) {
            if (blueprint instanceof Map) {
                Map<String, Object> data = toPlain((Map<?, ?>) blueprint);
                data.put("name", name);
                out.add(new Blueprint(name, data));
            }
        }

        private static Map<String, Object> toPlain(Map<?, ?> raw) {
            // Copy script mirrors (ScriptObjectMirror etc.) out of the engine into a plain map.
            Map<String, Object> plain = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map) {
                    value = toPlain((Map<?, ?>) value);
                }
                plain.put(String.valueOf(entry.getKey()), value);
            }
            return plain;
        }
    }
}
