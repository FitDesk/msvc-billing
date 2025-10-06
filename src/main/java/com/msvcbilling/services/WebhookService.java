package com.msvcbilling.services;

import java.util.Map;

public interface WebhookService {
    boolean handleWebhook(Map<String, String> headers, String body);
}