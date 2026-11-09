package me.fengming.translatorvault;

import me.fengming.vaultpatcher_asm.VaultPatcher;
import me.fengming.vaultpatcher_asm.plugin.VaultPatcherPlugin;

import java.nio.file.Path;

/**
 * Vault Patcher extension entrypoint.
 *
 * <p>Install as {@code vaultpatcher/plugins/translatorvault-1.0.0.jar}.
 * Vault Patcher reads the {@code VaultPatcherPlugin} manifest attribute and calls
 * {@link #start(Path)}. Modules are already loaded when {@link #end()} runs, so
 * this is the point where the export can see every pair.</p>
 */
public class TranslatorVaultPlugin implements VaultPatcherPlugin {
    private Path mcPath;
    private TranslatorVaultConfig config;

    @Override
    public String getName() {
        return "translatorvault";
    }

    @Override
    public void start(Path mcPath) {
        this.mcPath = mcPath;
        this.config = TranslatorVaultConfig.load(mcPath);
        VaultPatcher.LOGGER.info("TranslatorVault: extension started ({}).", config.isEnable() ? "enabled" : "disabled");
    }

    @Override
    public void onLoadConfig(Phase phase) {
        // The extension has its own config; re-read after Vault Patcher finished
        // its config phase in case another plugin wrote the file.
        if (phase == Phase.AFTER && mcPath != null) {
            this.config = TranslatorVaultConfig.load(mcPath);
        }
    }

    @Override
    public void end() {
        if (config == null && mcPath != null) {
            config = TranslatorVaultConfig.load(mcPath);
        }
        if (config == null || !config.isEnable()) return;
        LangFileGenerator.generate(mcPath, config);
    }
}
