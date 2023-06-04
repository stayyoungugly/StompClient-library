package com.stayyoungugly.stomplibrary.model.enum

enum class HeaderType(val text: String) {
    VERSION("accept-version"),
    HEART_BEAT("heart-beat"),
    DESTINATION("destination"),
    SUBSCRIPTION("subscription"),
    CONTENT_TYPE("content-type"),
    MESSAGE_ID("message-id"),
    ID("id"),
    ACK("ack"),
    HOST("host"),
    LOGIN("login"),
    PASSCODE("passcode"),
    TRANSACTION("transaction"),
    RECEIPT("receipt"),
    MESSAGE("message"),
    RECEIPT_ID("receipt_id"),
    SESSION("session"),
    SERVER("server"),
    NONE("")
}
