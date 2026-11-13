package com.retailshop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityApiTest {
    @Autowired MockMvc mvc;

    @Test
    void loginReturnsTokenAndTokenGrantsAccess() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/analytics/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierCannotSeeFinancials() throws Exception {
        mvc.perform(get("/api/analytics/profit-loss")).andExpect(status().isForbidden());
        mvc.perform(get("/api/payments")).andExpect(status().isForbidden());
        mvc.perform(get("/api/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.todaySales").exists())
                .andExpect(jsonPath("$.kpis.monthGrossProfit").doesNotExist())
                .andExpect(jsonPath("$.kpis.accountsPayable").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanSeeFinancialsButNotAdjustStock() throws Exception {
        mvc.perform(get("/api/analytics/profit-loss")).andExpect(status().isOk());
        mvc.perform(post("/api/inventory/adjustments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"warehouseId\":1,\"quantityChange\":5,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
