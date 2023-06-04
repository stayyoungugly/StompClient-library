package com.stayyoungugly.stomplibrary.model

import com.stayyoungugly.stomplibrary.model.enum.HeaderType
import com.stayyoungugly.stomplibrary.model.enum.FrameType
import java.io.StringReader
import java.util.*
import java.util.regex.Pattern

class StompMessage(
    val stompFrame: String,
    val stompHeaders: List<StompHeader>?,
    val payload: String?
) {

    fun findHeader(key: String?): String? {
        if (stompHeaders == null) return null
        for (header in stompHeaders) {
            if (header.headerType.text == key) return header.headerValue
        }
        return null
    }

    fun compile(): String {
        return compile(false)
    }

    fun compile(legacyWhitespace: Boolean): String {
        val builder = StringBuilder()
        builder.append(stompFrame).append('\n')
        if (stompHeaders != null) {
            for (header in stompHeaders) {
                builder.append(header.headerType.text).append(':').append(header.headerValue)
                    .append('\n')
            }
        }

        builder.append('\n')

        if (payload != null) {
            builder.append(payload)
            if (legacyWhitespace) builder.append("\n\n")
        }

        builder.append(TERMINATE_MESSAGE_SYMBOL)
        return builder.toString()
    }


    companion object {
        const val TERMINATE_MESSAGE_SYMBOL = "\u0000"
        private val PATTERN_HEADER = Pattern.compile("([^:\\s]+)\\s*:\\s*([^:\\s]+)")

        fun from(data: String?): StompMessage {
            if (data == null || data.trim { it <= ' ' }.isEmpty()) {
                return StompMessage(FrameType.UNKNOWN.name, null, data)
            }
            val reader = Scanner(StringReader(data))
            reader.useDelimiter("\\n")
            val frame = reader.next()
            val headers: MutableList<StompHeader> = ArrayList<StompHeader>()

            while (reader.hasNext(PATTERN_HEADER)) {
                val matcher = PATTERN_HEADER.matcher(reader.next())
                matcher.find()
                var type = HeaderType.NONE
                for (header in HeaderType.values()) {
                    if (header.text == matcher.group(1)) type = header
                }
                val value = matcher.group(2)
                value?.let { StompHeader(type, it) }?.let { headers.add(it) }
            }

            reader.skip("\n\n")
            reader.useDelimiter(TERMINATE_MESSAGE_SYMBOL)
            val payload = if (reader.hasNext()) reader.next() else null
            return StompMessage(frame, headers, payload)
        }
    }
}
