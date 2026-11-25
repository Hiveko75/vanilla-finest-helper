package io.github.hiveko.vanillafinest.helper.setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Look page's data: the pack's resource pack profiles (Packed Packs' {@code *.profile.json} files, in
 * Packed Packs' order) and the shaders offered for each profile ({@code look.json}).
 */
final class Look {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");

    /** A resource pack profile: file name (id), display name, description lines, packs top first. */
    record Profile(String id, String name, String description, List<String> packIds) {}

    /** A shader choice: id (for the texts), the shader pack file ("" = no shaders). */
    record Shader(String id, String file) {
        boolean none() {
            return file.isEmpty();
        }
    }

    private Look() {}

    private static Path packedPacks() {
        return FabricLoader.getInstance().getConfigDir().resolve("packed_packs");
    }

    // ---------------------------------------------------------------- profiles

    static List<Profile> profiles() {
        Path dir = packedPacks().resolve("profiles").resolve("resourcepacks");
        Map<String, Profile> byId = new LinkedHashMap<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.profile.json")) {
                for (Path f : files) {
                    Profile p = readProfile(f);
                    if (p != null) byId.put(p.id(), p);
                }
            } catch (IOException e) {
                LOGGER.warn("Setup screen: cannot list {} ({})", dir, e.toString());
            }
        }
        List<Profile> ordered = new ArrayList<>();
        for (String id : profileOrder()) {
            Profile p = byId.remove(id);
            if (p != null) ordered.add(p);
        }
        List<Profile> rest = new ArrayList<>(byId.values());
        rest.sort(Comparator.comparing(Profile::name, String.CASE_INSENSITIVE_ORDER));
        ordered.addAll(rest);
        return ordered;
    }

    private static Profile readProfile(Path file) {
        String fileName = file.getFileName().toString();
        String id = fileName.substring(0, fileName.length() - ".profile.json".length());
        JsonObject json = readJson(file);
        if (json == null) return null;
        String name = json.has("name") ? json.get("name").getAsString() : id;
        String description = json.has("description") ? json.get("description").getAsString() : "";
        List<String> packs = new ArrayList<>();
        JsonElement ids = json.get("packIds");
        if (ids != null && ids.isJsonArray()) for (JsonElement e : ids.getAsJsonArray()) packs.add(e.getAsString());
        return new Profile(id, name, description, packs);
    }

    private static List<String> profileOrder() {
        List<String> out = new ArrayList<>();
        JsonObject config = readJson(packedPacks().resolve("config.json"));
        if (config == null || !config.has("resourcepacks")) return out;
        JsonElement order = config.getAsJsonObject("resourcepacks").get("profileOrder");
        if (order != null && order.isJsonArray()) for (JsonElement e : order.getAsJsonArray()) out.add(e.getAsString());
        return out;
    }

    private static String lastViewed() {
        JsonObject config = readJson(packedPacks().resolve("config.json"));
        if (config == null || !config.has("resourcepacks")) return null;
        JsonElement last = config.getAsJsonObject("resourcepacks").get("lastViewedProfile");
        return last != null && last.isJsonPrimitive() ? last.getAsString() : null;
    }

    /** The profile whose packs are the ones selected now; else Packed Packs' last viewed one; else the first. */
    static Profile current(List<Profile> profiles) {
        if (profiles.isEmpty()) return null;
        PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
        Set<String> selected = new HashSet<>();
        for (Pack p : repo.getSelectedPacks()) if (!p.isRequired()) selected.add(p.getId());
        for (Profile p : profiles) {
            if (selectable(repo, p.packIds()).equals(selected)) return p;
        }
        String last = lastViewed();
        for (Profile p : profiles) if (p.id().equals(last)) return p;
        return profiles.get(0);
    }

    private static Set<String> selectable(PackRepository repo, List<String> ids) {
        Set<String> out = new HashSet<>();
        for (String id : ids) {
            Pack pack = repo.getPack(id);
            if (pack != null && !pack.isRequired()) out.add(id);
        }
        return out;
    }

    /** Switches to the profile's resource packs (and reloads the resources), like Packed Packs does. */
    static void applyProfile(Profile profile) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        List<String> bottomFirst = new ArrayList<>();
        for (String id : profile.packIds()) if (repo.isAvailable(id)) bottomFirst.add(0, id);
        repo.setSelected(bottomFirst);
        mc.options.updateResourcePacks(repo); // saves options.txt and reloads when the list changed
        setLastViewed(profile);
    }

    /** Packed Packs shows the same profile next time it opens. */
    private static void setLastViewed(Profile profile) {
        if (!Integrations.loaded("packed_packs")) return;
        try {
            Class<?> manager = Class.forName("io.github.fishstiz.packed_packs.config.ProfileManager");
            Object resources = manager.getMethod("get", PackType.class).invoke(null, PackType.CLIENT_RESOURCES);
            Object p = manager.getMethod("get", String.class).invoke(resources, profile.id());
            if (p != null) manager.getMethod("setLastViewed", p.getClass()).invoke(resources, p);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.info("Setup screen: Packed Packs' last viewed profile not changed ({})", e.toString());
        }
    }

    // ---------------------------------------------------------------- shaders

    /**
     * The shaders offered for a profile: look.json's first "profiles" rule whose "match" fits the profile's
     * file name ("*" = any), keeping only the shader packs that are in the shaderpacks folder ("none" always).
     */
    static List<Shader> shadersFor(Profile profile, JsonObject look) {
        List<Shader> out = new ArrayList<>();
        JsonObject files = look.has("shaders") && look.get("shaders").isJsonObject() ? look.getAsJsonObject("shaders") : new JsonObject();
        JsonArray rules = look.has("profiles") && look.get("profiles").isJsonArray() ? look.getAsJsonArray("profiles") : new JsonArray();
        for (JsonElement r : rules) {
            if (!r.isJsonObject()) continue;
            JsonObject rule = r.getAsJsonObject();
            String match = rule.has("match") ? rule.get("match").getAsString() : "*";
            if (profile != null && !matches(match, profile.id())) continue;
            for (JsonElement s : rule.getAsJsonArray("shaders")) {
                String id = s.getAsString();
                if (id.equals("none")) {
                    out.add(new Shader("none", ""));
                    continue;
                }
                String pattern = files.has(id) ? files.get(id).getAsString() : null;
                String file = pattern == null ? null : findShader(pattern);
                if (file != null) out.add(new Shader(id, file));
            }
            break;
        }
        if (out.isEmpty()) out.add(new Shader("none", ""));
        return out;
    }

    /** The shader choice that is active now, or null when it is none of the offered ones. */
    static Shader currentShader(List<Shader> offered) {
        boolean on = Integrations.Iris.enabled();
        String pack = Integrations.Iris.pack();
        for (Shader s : offered) {
            if (s.none() ? !on : on && s.file().equals(pack)) return s;
        }
        return null;
    }

    /** The newest file in the shaderpacks folder matching the pattern ("ComplementaryReimagined_*.zip"). */
    private static String findShader(String pattern) {
        Path dir = Integrations.Iris.folder();
        if (!Files.isDirectory(dir)) return null;
        String best = null;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                if (matches(pattern, name) && (best == null || name.compareToIgnoreCase(best) > 0)) best = name;
            }
        } catch (IOException e) {
            return null;
        }
        return best;
    }

    /** Simple wildcards: * any text, ? one character; case-insensitive. */
    static boolean matches(String pattern, String name) {
        StringBuilder regex = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
        }
        return java.util.regex.Pattern.compile(regex.toString(), java.util.regex.Pattern.CASE_INSENSITIVE).matcher(name).matches();
    }

    private static JsonObject readJson(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }
}
