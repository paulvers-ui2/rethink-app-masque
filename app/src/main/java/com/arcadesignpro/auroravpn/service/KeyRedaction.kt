package com.arcadesignpro.auroravpn.service

/**
 * The config editors (WARP config.json, the chain's two identities and wg0.conf) show
 * every field but the secrets: those read [HIDDEN], and saving text that still says
 * [HIDDEN] keeps the stored secret. So endpoints, MTU and the like stay editable while
 * the keys never appear on screen, in a screenshot or on the clipboard.
 */
object KeyRedaction {
    const val HIDDEN = "(hidden)"

    // usque config.json: the ECDSA identity key and the Cloudflare API token.
    private val JSON_SECRETS = listOf("private_key", "access_token")
    // wg-quick: the interface key and an optional peer pre-shared key.
    private val WG_SECRETS = listOf("PrivateKey", "PresharedKey")

    private fun jsonField(name: String) = Regex("(\"$name\"\\s*:\\s*\")([^\"]*)(\")")

    private fun wgField(name: String) =
        Regex("^(\\s*$name\\s*=\\s*)(\\S+)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /** [json] with the secret values replaced by [HIDDEN]. */
    fun hideJson(json: String): String =
        JSON_SECRETS.fold(json) { acc, f ->
            jsonField(f).replace(acc) { m -> if (m.groupValues[2].isEmpty()) m.value else m.groupValues[1] + HIDDEN + m.groupValues[3] }
        }

    /** [edited] with every [HIDDEN] secret put back from [stored]; null if one has nothing to come from. */
    fun restoreJson(edited: String, stored: String?): String? =
        restore(edited, stored, JSON_SECRETS, ::jsonField) { m, value -> m.groupValues[1] + value + m.groupValues[3] }

    /** [conf] with the secret keys replaced by [HIDDEN]. */
    fun hideWg(conf: String): String =
        WG_SECRETS.fold(conf) { acc, f -> wgField(f).replace(acc) { m -> m.groupValues[1] + HIDDEN } }

    /** [edited] with every [HIDDEN] key put back from [stored]; null if one has nothing to come from. */
    fun restoreWg(edited: String, stored: String?): String? =
        restore(edited, stored, WG_SECRETS, ::wgField) { m, value -> m.groupValues[1] + value }

    private fun restore(
        edited: String,
        stored: String?,
        fields: List<String>,
        pattern: (String) -> Regex,
        put: (MatchResult, String) -> String,
    ): String? {
        var out = edited
        for (f in fields) {
            val re = pattern(f)
            val hidden = re.findAll(out).any { it.groupValues[2] == HIDDEN }
            if (!hidden) continue
            val value = stored?.let { re.find(it)?.groupValues?.get(2) }
            if (value.isNullOrEmpty() || value == HIDDEN) return null
            out = re.replace(out) { m -> if (m.groupValues[2] == HIDDEN) put(m, value) else m.value }
        }
        return out
    }
}
