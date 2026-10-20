package com.resumescreener.api.portal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * {@code publicBaseUrl} is where candidates open the UI; access links point there. The token sits in the
 * fragment, which browsers never send to a server, so it stays out of proxy and access logs.
 */
@Validated
@ConfigurationProperties("app.portal")
public record PortalProperties(
        @NotNull @DefaultValue("7d") Duration inviteTtl,
        @NotNull @DefaultValue("http://localhost:5173") URI publicBaseUrl,
        @Min(1) @DefaultValue("30") int inviteRequestsPerMinute) {

    public String inviteUrl(String rawToken) {
        String base = publicBaseUrl.toString();
        return (base.endsWith("/") ? base : base + "/") + "invite#" + rawToken;
    }
}
