package me.fengming.translatorvault;

import me.fengming.vaultpatcher_asm.VaultPatcher;
import me.fengming.vaultpatcher_asm.core.utils.Platform;

import java.util.Locale;

/**
 * Chooses the Minecraft language-file format from the running game version:
 * 1.6.2 - 1.12.2 use {@code .lang} and upper-case region codes, 1.13+ use JSON.
 */
final class McLangFormat {
    private McLangFormat() {
    }

    static boolean isLegacy(String mcVersion) {
        int[] version = parse(mcVersion);
        if (version == null) {
            return VaultPatcher.platform == Platform.Forge1_6;
        }
        return version[0] == 1 && version[1] >= 6 && version[1] <= 12;
    }

    static String extension(String mcVersion) {
        return isLegacy(mcVersion) ? ".lang" : ".json";
    }

    static String normalizeCode(String code, String mcVersion) {
        if (isBlank(code)) code = "en_us";
        code = code.trim().replace('-', '_');
        if (isLegacy(mcVersion)) {
            int split = code.indexOf('_');
            if (split < 0) return code.toUpperCase(Locale.ROOT);
            return code.substring(0, split).toLowerCase(Locale.ROOT)
                    + "_"
                    + code.substring(split + 1).toUpperCase(Locale.ROOT);
        }
        return code.toLowerCase(Locale.ROOT);
    }

    static String fileName(String code, String mcVersion) {
        return normalizeCode(code, mcVersion) + extension(mcVersion);
    }

    static int[] parse(String version) {
        if (isBlank(version)) return null;
        int i = 0;
        int length = version.length();
        while (i < length && !Character.isDigit(version.charAt(i))) i++;
        if (i >= length) return null;

        int major = 0;
        while (i < length && Character.isDigit(version.charAt(i))) {
            major = major * 10 + (version.charAt(i) - '0');
            i++;
        }
        if (i >= length || version.charAt(i) != '.') {
            return new int[]{major, 0};
        }
        i++;
        int minor = 0;
        while (i < length && Character.isDigit(version.charAt(i))) {
            minor = minor * 10 + (version.charAt(i) - '0');
            i++;
        }
        return new int[]{major, minor};
    }

    static String describe(String mcVersion) {
        return isLegacy(mcVersion)
                ? "legacy .lang (" + normalizeCode("zh_cn", mcVersion) + ")"
                : "modern .json (" + normalizeCode("zh_cn", mcVersion) + ")";
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
