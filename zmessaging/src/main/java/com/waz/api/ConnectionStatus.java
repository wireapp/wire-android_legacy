package com.waz.api;

public enum ConnectionStatus {
    UNCONNECTED("unconnected"),
    PENDING_FROM_USER("sent"),
    PENDING_FROM_OTHER("pending"),
    ACCEPTED("accepted"),
    BLOCKED("blocked"),
    IGNORED("ignored"),
    SELF("self"),
    CANCELLED("cancelled");

    public String code;

    ConnectionStatus(String code) {
        this.code = code;
    }
}
