package com.mrredhood.nexus.core.settings

import android.content.Context

/** Encrypted-at-rest credentials used by Git transports and GitHub-backed operations. */
class GitCredentialStore(context: Context) {
    private val store = ApiKeyStore(context.applicationContext)

    fun setGithubToken(value: String) = store.put("github", value)
    fun githubToken(): String? = store.get("github")
    fun hasGithubToken(): Boolean = store.has("github")
    fun clearGithubToken() = store.remove("github")

    fun setHttpsUsername(value: String) = store.put("git-https-username", value)
    fun httpsUsername(): String? = store.get("git-https-username")
    fun setHttpsPassword(value: String) = store.put("git-https-password", value)
    fun httpsPassword(): String? = store.get("git-https-password")

    fun setSshPrivateKey(value: String) = store.put("git-ssh-private-key", value)
    fun sshPrivateKey(): String? = store.get("git-ssh-private-key")
    fun setSshPassphrase(value: String) = store.put("git-ssh-passphrase", value)
    fun sshPassphrase(): String? = store.get("git-ssh-passphrase")
    fun setKnownHosts(value: String) = store.put("git-ssh-known-hosts", value)
    fun knownHosts(): String? = store.get("git-ssh-known-hosts")

    fun clearHttps() {
        store.remove("git-https-username")
        store.remove("git-https-password")
    }

    fun clearSsh() {
        store.remove("git-ssh-private-key")
        store.remove("git-ssh-passphrase")
        store.remove("git-ssh-known-hosts")
    }
}
