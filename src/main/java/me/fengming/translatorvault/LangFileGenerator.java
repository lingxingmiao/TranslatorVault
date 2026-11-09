package me.fengming.translatorvault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.fengming.vaultpatcher_asm.VaultPatcher;
import me.fengming.vaultpatcher_asm.config.Pair;
import me.fengming.vaultpatcher_asm.config.Pairs;
import me.fengming.vaultpatcher_asm.config.TranslationInfo;
import me.fengming.vaultpatcher_asm.core.utils.Utils;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the pairs already loaded by Vault Patcher modules into a normal
 * Minecraft language file. No network, no API, no translation service.
 */
final class LangFileGenerator {
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private LangFileGenerator() {
    }

    static void generate(Path mcPath, TranslatorVaultConfig config) {
        if (!config.isEnable()) return;
        try {
            List<Pairs> pairsList = collectPairs();
            if (pairsList.isEmpty()) {
                VaultPatcher.LOGGER.info("TranslatorVault: no Vault Patcher module pairs found, skipped.");
                return;
            }

            Map<String, String> merged = merge(pairsList);
            if (merged.isEmpty()) {
                VaultPatcher.LOGGER.warn("TranslatorVault: modules loaded but no usable pairs were found.");
                return;
            }

            String mcVersion = VaultPatcher.mcVersion;
            String code = McLangFormat.normalizeCode(config.getTargetLanguage(), mcVersion);
            Path exportPath = exportPath(mcPath, config, code, mcVersion);
            int written = write(exportPath, merged, config);
            VaultPatcher.LOGGER.info("TranslatorVault: wrote {} entries to {} ({}).",
                    written, display(exportPath), McLangFormat.describe(mcVersion));
        } catch (Throwable t) {
            // A language-file export must never crash the game.
            VaultPatcher.LOGGER.warn("TranslatorVault: failed: {}", t.toString());
            t.printStackTrace();
        }
    }

    private static List<Pairs> collectPairs() {
        List<Pairs> result = new ArrayList<>();
        Set<Pairs> seen = Collections.newSetFromMap(new IdentityHashMap<Pairs, Boolean>());
        for (Set<TranslationInfo> infos : Utils.translationInfoMap.values()) {
            if (infos == null) continue;
            for (TranslationInfo info : infos) {
                Pairs pairs = info.getPairs();
                if (pairs != null && seen.add(pairs)) result.add(pairs);
            }
        }
        if (Utils.dynTranslationInfos != null) {
            for (TranslationInfo info : Utils.dynTranslationInfos) {
                Pairs pairs = info.getPairs();
                if (pairs != null && seen.add(pairs)) result.add(pairs);
            }
        }
        return result;
    }

    private static Map<String, String> merge(List<Pairs> pairsList) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (Pairs pairs : pairsList) {
            Map<String, String> map = pairs.getMap();
            if (map != null) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    putMerged(merged, entry.getKey(), entry.getValue());
                }
            }
            Set<Pair<String, String>> set = pairs.getSet();
            if (set != null) {
                for (Pair<String, String> pair : set) {
                    putMerged(merged, pair.first, pair.second);
                }
            }
        }
        return merged;
    }

    private static void putMerged(Map<String, String> merged, String source, String target) {
        if (McLangFormat.isBlank(source)) return;
        if (target == null) target = "";
        // Dynamic modules use a leading '@' as a partial-match marker.
        if (target.startsWith("@") && !target.startsWith("@@")) {
            target = target.substring(1);
        }
        merged.put(source, target);
    }

    private static int write(Path exportPath, Map<String, String> merged, TranslatorVaultConfig config) throws IOException {
        Map<String, String> output = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            String value = entry.getValue();
            if (McLangFormat.isBlank(value)) continue;
            if (!config.isIncludeUnchanged() && value.equals(entry.getKey())) continue;
            output.put(entry.getKey(), value);
        }
        if (output.isEmpty()) {
            VaultPatcher.LOGGER.warn("TranslatorVault: all values are identical to their source; enable include_unchanged to keep them.");
            return 0;
        }

        Files.createDirectories(exportPath.getParent());
        if (McLangFormat.isLegacy(VaultPatcher.mcVersion)) {
            writeLang(exportPath, output);
        } else {
            writeJson(exportPath, output);
        }
        return output.size();
    }

    private static Path exportPath(Path mcPath, TranslatorVaultConfig config, String code, String mcVersion) {
        String dir = McLangFormat.isBlank(config.getOutputDir()) ? "vaultpatcher/lang" : config.getOutputDir();
        Path base = new java.io.File(dir).isAbsolute()
                ? new java.io.File(dir).toPath()
                : mcPath.resolve(dir);
        return base.resolve(code + McLangFormat.extension(mcVersion));
    }

    private static void writeJson(Path path, Map<String, String> data) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            PRETTY_GSON.toJson(data, writer);
        }
    }

    private static void writeLang(Path path, Map<String, String> data) throws IOException {
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String key : keys) {
                writer.write(escape(key));
                writer.write('=');
                writer.write(escape(data.get(key)));
                writer.write('\n');
            }
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String display(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
