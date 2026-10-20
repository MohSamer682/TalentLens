package com.resumescreener.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientErrorControllerTest {

    @Test
    void lineBreaksCannotForgeLogLinesAndLongTextIsCapped() {
        assertThat(ClientErrorController.clean("ok\r\nWARN fake line", 100)).doesNotContain("\n").doesNotContain("\r");
        assertThat(ClientErrorController.clean("x".repeat(50), 10)).isEqualTo("xxxxxxxxxx...");
        assertThat(ClientErrorController.clean(null, 10)).isNull();
    }
}
