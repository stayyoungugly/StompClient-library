package com.stayyoungugly.stomplibrary.util

import com.stayyoungugly.stomplibrary.model.StompMessage

interface PathMatcher {
    fun matches(path: String, msg: StompMessage): Boolean
}
