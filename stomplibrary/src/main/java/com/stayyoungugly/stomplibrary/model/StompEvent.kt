package com.stayyoungugly.stomplibrary.model

import com.stayyoungugly.stomplibrary.model.enum.EventType
import java.util.*

data class StompEvent(
    val eventType: EventType,
    val eventException: Exception? = null,
    val eventMessage: String? = null,
    var responseHeaders: Map<String, String> = LinkedHashMap<String, String>()
)
