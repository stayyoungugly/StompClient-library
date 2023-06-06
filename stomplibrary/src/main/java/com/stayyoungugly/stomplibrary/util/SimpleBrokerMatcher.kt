package com.stayyoungugly.stomplibrary.util

import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.HeaderType

class SimpleBrokerMatcher : PathMatcher {
    override fun matches(path: String, msg: StompMessage): Boolean {
        val dest: String? = msg.findHeader(HeaderType.DESTINATION.text)
        return if (dest == null) false else path == dest
    }
}
