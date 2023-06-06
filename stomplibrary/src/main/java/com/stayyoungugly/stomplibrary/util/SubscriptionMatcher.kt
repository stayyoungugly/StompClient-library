package com.stayyoungugly.stomplibrary.util

import com.stayyoungugly.stomplibrary.client.StompClient
import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.HeaderType

class SubscriptionMatcher(private val stompClient: StompClient) : PathMatcher {

    override fun matches(path: String, msg: StompMessage): Boolean {
        val pathSubscription: String = stompClient.getTopicId(path) ?: return false
        val subscription: String? = msg.findHeader(HeaderType.SUBSCRIPTION.text)
        return pathSubscription == subscription
    }
}
