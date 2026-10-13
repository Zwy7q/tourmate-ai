package com.tourmate.refund;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RefundService {
    private final Map<String, RefundConfirmation> pending = new ConcurrentHashMap<>();

    public RefundConfirmation requestConfirmation() {
        var confirmation = new RefundConfirmation(UUID.randomUUID().toString(), Instant.now().plus(Duration.ofMinutes(5)));
        pending.put(confirmation.token(), confirmation);
        return confirmation;
    }

    public boolean confirm(String token) {
        var confirmation = pending.remove(token);
        return confirmation != null && confirmation.expiresAt().isAfter(Instant.now());
    }
}
