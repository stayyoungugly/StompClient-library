package com.stayyoungugly.stomplibrary.matcher

import com.stayyoungugly.stomplibrary.model.StompMessage
import com.stayyoungugly.stomplibrary.model.enum.HeaderType

class RabbitMQMatcher : PathMatcher {

    override fun matches(path: String, msg: StompMessage): Boolean {
        val dest: String = msg.findHeader(HeaderType.DESTINATION.text) ?: return false
        val split = path.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val transformed = ArrayList<String>()
        for (s in split) {
            when (s) {
                "*" -> transformed.add("[^.]+")
                "#" -> transformed.add(".*")
                else -> transformed.add(s.replace("\\*".toRegex(), ".*"))
            }
        }
        val sb = StringBuilder()
        for (s in transformed) {
            if (sb.isNotEmpty()) sb.append("\\.")
            sb.append(s)
        }
        val join = sb.toString()
        return dest.matches(join.toRegex())
    }
}
