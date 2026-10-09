package com.arcadesignpro.auroravpn.service

/**
 * The last lines a libusque.so process printed, for two things the managers need
 * after it exits: a reason to show the user ("Failed to start WARP: ..."), and
 * whether the binary rejected a flag it does not know.
 *
 * The resilience flags (--idle-timeout, --stall-timeout, ...) are newer than some
 * libusque.so builds. One that does not know a flag exits at once with
 * `unknown flag: --stall-timeout`; rather than leave WARP down for good, the
 * managers start it again without those flags ([withoutNewerFlags]).
 */
class UsqueOutput(private val capacity: Int = DEFAULT_CAPACITY) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun clear() = lines.clear()

    /** True when the binary refused one of its arguments. */
    @Synchronized
    fun rejectedAFlag(): Boolean = lines.any { it.contains(UNKNOWN_FLAG, ignoreCase = true) }

    /** The most recent line that reads like an error, or "" if none did. */
    @Synchronized
    fun lastError(): String =
        lines.lastOrNull { line -> ERROR_MARKERS.any { line.contains(it, ignoreCase = true) } }
            ?.trim()
            ?.take(MAX_REASON_LEN)
            .orEmpty()

    companion object {
        private const val DEFAULT_CAPACITY = 40
        private const val MAX_REASON_LEN = 160
        private const val UNKNOWN_FLAG = "unknown flag"
        private val ERROR_MARKERS = listOf(
            UNKNOWN_FLAG, "error", "failed", "panic", "fatal", "not loaded", "denied", "refused",
        )

        // Flags newer than the oldest libusque.so the app may still meet, and
        // whether each takes a separate value.
        private val NEWER_FLAGS = mapOf(
            "--idle-timeout" to true,
            "--stall-timeout" to true,
            "--exit-stall-timeout" to true,
            "--connect-timeout" to true,
            "--probe-addr" to true,
            "--watch-network" to false,
        )

        /** [args] without the flags an older libusque.so does not know. */
        fun withoutNewerFlags(args: List<String>): List<String> {
            val out = mutableListOf<String>()
            var skipValue = false
            for (arg in args) {
                if (skipValue) {
                    skipValue = false
                    continue
                }
                val takesValue = NEWER_FLAGS[arg.substringBefore('=')]
                if (takesValue == null) {
                    out += arg
                } else if (takesValue && '=' !in arg) {
                    skipValue = true
                }
            }
            return out
        }
    }
}
