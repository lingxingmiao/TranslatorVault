package me.fengming.translatorvault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.fengming.vaultpatcher_asm.VaultPatcher;
import me.fengming.vaultpatcher_asm.config.Pairs;
import me.fengming.vaultpatcher_asm.config.TranslationInfo;
import me.fengming.vaultpatcher_asm.core.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads Vault Patcher module JSON files embedded in mod jars.
 *
 * <p>This is what makes a TranslatorMinecraft-generated jar self-contained: the
 * translator writes {@code vaultpatcher/modules/<modid>.json} into the output
 * jar, and TranslatorVault registers those pairs before Vault Patcher builds its
 * transformers.</p>
 */
final class EmbeddedModuleLoader {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private EmbeddedModuleLoader() {
    }

    static void load(Path mcPath) {
        if (!LOADED.compareAndSet(false, true)) return;
        Path mods = mcPath.resolve("mods");
        if (!Files.isDirectory(mods)) return;

        int pairs = 0;
        int jars = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : stream) {
                try {
                    int loaded = loadJar(jar);
                    if (loaded > 0) {
                        pairs += loaded;
                        jars++;
                    }
                } catch (Throwable t) {
                    VaultPatcher.LOGGER.warn("TranslatorVault: cannot read embedded modules from {}: {}",
                            jar.getFileName(), t.toString());
                }
            }
        } catch (IOException e) {
            VaultPatcher.LOGGER.warn("TranslatorVault: cannot scan mods directory: {}", e.toString());
        }
        if (pairs > 0) {
            VaultPatcher.LOGGER.info("TranslatorVault: loaded {} embedded pair(s) from {} mod jar(s).", pairs, jars);
        }
    }

    private static int loadJar(Path jar) throws IOException {
        int count = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".json")) continue;
                if (!name.startsWith("vaultpatcher/modules/")
                        && !name.startsWith("translatorvault/modules/")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    count += parse(read(in));
                } catch (Throwable t) {
                    VaultPatcher.LOGGER.warn("TranslatorVault: invalid embedded module {} in {}: {}",
                            name, jar.getFileName(), t.toString());
                }
            }
        }
        return count;
    }

    private static int parse(String text) {
        JsonElement root = new JsonParser().parse(text);
        if (!root.isJsonArray()) return 0;
        JsonArray array = root.getAsJsonArray();
        if (array.size() < 2) return 0;

        JsonObject info = array.get(0).getAsJsonObject();
        if (info.has("dynamic") && info.get("dynamic").getAsBoolean()) return 0;
        if (info.has("i18n") && info.get("i18n").getAsBoolean()) return 0;

        int count = 0;
        for (int i = 1; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            List<String> targets = readTargets(entry);
            if (targets.isEmpty()) continue;

            JsonArray pairsJson = entry.has("pairs") ? entry.getAsJsonArray("pairs")
                    : entry.has("p") ? entry.getAsJsonArray("p") : null;
            if (pairsJson == null) continue;

            Pairs pairs = new Pairs();
            for (JsonElement pairElement : pairsJson) {
                if (!pairElement.isJsonObject()) continue;
                JsonObject pair = pairElement.getAsJsonObject();
                String key = string(pair, "k", "key");
                String value = string(pair, "v", "value");
                if (key == null || key.isEmpty() || value == null || value.isEmpty()) continue;
                pairs.getMap().put(key, value);
            }
            if (pairs.getMap().isEmpty()) continue;

            for (String target : targets) {
                if (target == null || target.trim().isEmpty()) continue;
                String normalized = target.trim().replace('/', '.');
                TranslationInfo.Mutable mutable = new TranslationInfo.Mutable(normalized);
                mutable.setPairs(pairs);
                Utils.addTranslationInfo(normalized, mutable.toImmutable());
                count += pairs.getMap().size();
            }
        }
        return count;
    }

    private static List<String> readTargets(JsonObject entry) {
        List<String> result = new ArrayList<>();
        for (String name : new String[]{"target_class", "target_classes", "t", "s"}) {
            if (!entry.has(name)) continue;
            JsonElement element = entry.get(name);
            if (element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) result.add(item.getAsString());
                }
            } else if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            }
        }
        return result;
    }

    private static String string(JsonObject object, String first, String second) {
        JsonElement element = object.has(first) ? object.get(first)
                : object.has(second) ? object.get(second) : null;
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
