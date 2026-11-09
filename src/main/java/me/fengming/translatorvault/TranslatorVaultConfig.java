package me.fengming.translatorvault;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import me.fengming.vaultpatcher_asm.VaultPatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration of the language-file export. Deliberately has no translation
 * service settings: this extension only turns already-loaded Vault Patcher
 * pairs into a normal Minecraft language file.
 */
final class TranslatorVaultConfig {
    private boolean enable = true;
    private String targetLanguage = "zh_cn";
    private String outputDir = "vaultpatcher/lang";
    private boolean includeUnchanged = false;

    static Path configFile(Path mcPath) {
        return mcPath.resolve("config").resolve("vaultpatcher_asm").resolve("translatorvault.json");
    }

    static TranslatorVaultConfig load(Path mcPath) {
        TranslatorVaultConfig config = new TranslatorVaultConfig();
        Path file = configFile(mcPath);
        if (Files.notExists(file)) {
            try {
                Files.createDirectories(file.getParent());
                config.save(mcPath);
                VaultPatcher.LOGGER.info("TranslatorVault: created default config at {}", display(file));
            } catch (IOException e) {
                VaultPatcher.LOGGER.warn("TranslatorVault: cannot create default config: {}", e.toString());
            }
            return config;
        }
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            config.readJson(reader);
        } catch (Exception e) {
            VaultPatcher.LOGGER.warn("TranslatorVault: cannot read config {}, using defaults: {}", display(file), e.toString());
        }
        return config;
    }

    void save(Path mcPath) throws IOException {
        Path file = configFile(mcPath);
        Files.createDirectories(file.getParent());
        try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            writer.setIndent("  ");
            writeJson(writer);
        }
    }

    void readJson(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.peek() != JsonToken.END_OBJECT) {
            switch (reader.nextName()) {
                case "enable":
                    enable = reader.nextBoolean();
                    break;
                case "target_language":
                    targetLanguage = reader.nextString();
                    break;
                case "output_dir":
                    outputDir = reader.nextString();
                    break;
                case "include_unchanged":
                    includeUnchanged = reader.nextBoolean();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
    }

    void writeJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("enable").value(enable);
        writer.name("target_language").value(targetLanguage);
        writer.name("output_dir").value(outputDir);
        writer.name("include_unchanged").value(includeUnchanged);
        writer.endObject();
    }

    boolean isEnable() {
        return enable;
    }

    String getTargetLanguage() {
        return targetLanguage;
    }

    String getOutputDir() {
        return outputDir;
    }

    boolean isIncludeUnchanged() {
        return includeUnchanged;
    }

    private static String display(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
