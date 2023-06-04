package com.stayyoungugly.stomplibrary.model

import com.stayyoungugly.stomplibrary.model.enum.HeaderType

data class StompHeader(
    val headerType: HeaderType,
    val headerValue: String
)
