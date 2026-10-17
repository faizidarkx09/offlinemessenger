package com.offlinemessenger.protocol

/**
 * JSON envelope carried INSIDE the encrypted MSG payload.
 * Replaces the old raw-UTF8 plaintext; decode() falls back to plain text
 * for packets from older builds.
 *
 * kinds: "chat" (text message), "react" (emoji reaction), "gsync" (group
 * membership sync), "read" (read receipt — marks sender's message READ).
 */
data class MessageEnvelope(
    val kind: String = "chat",
    val text: String = "",
    val replyTo: String? = null,
    val replyBody: String? = null,
    val replySender: String? = null,
    val forwarded: Boolean = false,
    /** Group id when this chat message belongs to a group. */
    val group: String? = null,
    val groupName: String? = null,
    /** react: target msgId + emoji + add(true)/remove(false). */
    val target: String? = null,
    val emoji: String? = null,
    val add: Boolean = true,
    /** gsync: full member list "id|name|keyB64|admin;..." */
    val members: String? = null,
) {
    fun encode(): ByteArray = buildString {
        append("{\"v\":1,\"kind\":\"").append(kind).append('"')
        append(",\"text\":\"").append(esc(text)).append('"')
        replyTo?.let { append(",\"replyTo\":\"").append(it).append('"') }
        replyBody?.let { append(",\"replyBody\":\"").append(esc(it)).append('"') }
        replySender?.let { append(",\"replySender\":\"").append(esc(it)).append('"') }
        if (forwarded) append(",\"fwd\":true")
        group?.let { append(",\"group\":\"").append(it).append('"') }
        groupName?.let { append(",\"groupName\":\"").append(esc(it)).append('"') }
        target?.let { append(",\"target\":\"").append(it).append('"') }
        emoji?.let { append(",\"emoji\":\"").append(esc(it)).append('"') }
        if (!add) append(",\"add\":false")
        members?.let { append(",\"members\":\"").append(esc(it)).append('"') }
        append('}')
    }.toByteArray(Charsets.UTF_8)

    companion object {
        private fun esc(s: String): String = s
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

        private fun unesc(s: String): String {
            val b = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        'n' -> b.append('\n'); 'r' -> b.append('\r')
                        't' -> b.append('\t'); '"' -> b.append('"')
                        '\\' -> b.append('\\'); else -> b.append(s[i + 1])
                    }
                    i += 2
                } else { b.append(c); i++ }
            }
            return b.toString()
        }

        /** Parses one flat JSON object. Never throws. */
        private fun parseObj(json: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            val s = json.trim()
            if (!s.startsWith("{")) return out
            var i = 1
            while (i < s.length) {
                while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
                if (i >= s.length || s[i] == '}') break
                if (s[i] != '"') break
                // key
                val kb = StringBuilder(); i++
                while (i < s.length) {
                    if (s[i] == '\\' && i + 1 < s.length) { kb.append(s[i]); kb.append(s[i + 1]); i += 2 }
                    else if (s[i] == '"') { i++; break } else { kb.append(s[i]); i++ }
                }
                while (i < s.length && (s[i].isWhitespace() || s[i] == ':')) i++
                // value
                val vb = StringBuilder()
                if (i < s.length && s[i] == '"') {
                    i++
                    while (i < s.length) {
                        if (s[i] == '\\' && i + 1 < s.length) { vb.append(s[i]); vb.append(s[i + 1]); i += 2 }
                        else if (s[i] == '"') { i++; break } else { vb.append(s[i]); i++ }
                    }
                    out[unesc(kb.toString())] = unesc(vb.toString())
                } else {
                    while (i < s.length && s[i] != ',' && s[i] != '}') { vb.append(s[i]); i++ }
                    out[unesc(kb.toString())] = vb.toString().trim()
                }
            }
            return out
        }

        fun decode(raw: ByteArray): MessageEnvelope {
            val s = runCatching { String(raw, Charsets.UTF_8) }.getOrNull() ?: return MessageEnvelope(text = "")
            val t = s.trim()
            if (!t.startsWith("{\"v\":1")) {
                // Legacy build: raw plaintext message.
                return MessageEnvelope(kind = "chat", text = s)
            }
            val m = parseObj(t)
            return MessageEnvelope(
                kind = m["kind"] ?: "chat",
                text = m["text"] ?: "",
                replyTo = m["replyTo"],
                replyBody = m["replyBody"],
                replySender = m["replySender"],
                forwarded = m["fwd"] == "true",
                group = m["group"],
                groupName = m["groupName"],
                target = m["target"],
                emoji = m["emoji"],
                add = m["add"] != "false",
                members = m["members"],
            )
        }
    }
}

/** Serializes group members for a gsync envelope. */
fun packMembers(members: List<Triple<String, String, String>>, admins: Set<String>): String =
    members.joinToString(";") { (id, name, key) ->
        "$id|${name.replace("|", "").replace(";", "")}|$key|${if (id in admins) 1 else 0}"
    }

/** Parses packMembers output. Returns list of (id, name, keyB64, isAdmin). */
fun unpackMembers(packed: String): List<GroupMemberSpec> =
    packed.split(';').mapNotNull { part ->
        val f = part.split('|')
        if (f.size >= 4) GroupMemberSpec(f[0], f[1], f[2], f[3] == "1") else null
    }

data class GroupMemberSpec(
    val deviceId: String,
    val displayName: String,
    val publicKeyB64: String,
    val isAdmin: Boolean,
)
