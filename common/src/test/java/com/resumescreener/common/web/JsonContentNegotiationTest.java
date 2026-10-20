package com.resumescreener.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(JsonContentNegotiationTest.Config.class)
class JsonContentNegotiationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void browserAcceptHeaderStillGetsJsonInsteadOfAnEmpty406() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/ping").accept(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Configuration
    @EnableWebMvc
    @Import(JsonOnlyResponses.class)
    static class Config {
        @Bean
        Ping ping() {
            return new Ping();
        }
    }

    @RestController
    static class Ping {
        @GetMapping("/ping")
        Map<String, Object> ping() {
            return Map.of("success", true);
        }
    }
}
