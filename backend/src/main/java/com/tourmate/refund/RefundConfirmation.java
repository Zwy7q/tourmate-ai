package com.tourmate.refund;

import java.time.Instant;

public record RefundConfirmation(String token, Instant expiresAt) {
}
