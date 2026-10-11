package com.mosu.app.data

/**
 * One-shot, runtime data migrations that reconcile storage left behind by
 * earlier versions of the app.
 *
 * Every step is idempotent and does nothing (cheaply) when there is nothing to
 * migrate, so this can run unconditionally on each launch. Keeping the steps
 * here keeps [MainActivity][com.mosu.app.MainActivity] focused on wiring the UI.
 */
object DataMigrations {

    suspend fun run(
        settingsManager: SettingsManager,
        tokenManager: TokenManager,
        accountManager: AccountManager
    ) {
        migrateSessionAndCredentials(settingsManager, tokenManager)
        accountManager.migrateLegacyUserInfo()
    }

    private suspend fun migrateSessionAndCredentials(
        settingsManager: SettingsManager,
        tokenManager: TokenManager
    ) {
        // Fold any multi-account session (and its bundled credentials) into the
        // single canonical set of keys.
        tokenManager.migrateFromMultiAccountStorage()

        // Re-home OAuth credentials that used to live in Settings, unless the
        // multi-account migration already produced a set.
        val legacyCredentials = settingsManager.drainLegacyCredentials() ?: return
        if (!tokenManager.getCredentials().clientId.isBlank()) return
        tokenManager.saveCredentials(legacyCredentials.first, legacyCredentials.second)
    }
}
