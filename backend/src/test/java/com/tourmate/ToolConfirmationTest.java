package com.tourmate;

import static org.assertj.core.api.Assertions.assertThat;

import com.tourmate.refund.RefundService;
import org.junit.jupiter.api.Test;

class ToolConfirmationTest {
    @Test
    void confirmationTokenIsSingleUse() {
        var service = new RefundService();
        var confirmation = service.requestConfirmation();
        assertThat(service.confirm(confirmation.token())).isTrue();
        assertThat(service.confirm(confirmation.token())).isFalse();
    }
}
