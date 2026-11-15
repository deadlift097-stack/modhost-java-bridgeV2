package com.modhost.api;

public interface EventHandler {
    /** @param data free-form payload, usually a small JSON string (may be empty) */
    void handle(String name, String data);
}
