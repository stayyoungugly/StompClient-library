package com.stayyoungugly.stomplibrary.matcher

import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.HeaderType

class BaseMatcher : PathMatcher {
    override fun matches(path: String, msg: StompMessage): Boolean {
        val dest: String? = msg.findHeader(HeaderType.DESTINATION.text)
        return if (dest == null) false else path == dest
    }
}
