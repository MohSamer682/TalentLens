package com.resumescreener.api.session;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.dto.CreateSessionRequest;
import com.resumescreener.common.session.WeightsDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionWeightsTest {

    private final SessionService service = new SessionService(null, null, null, null, null);

    private CreateSessionRequest withWeights(double t, double e, double ed, double s, double c) {
        CreateSessionRequest r = new CreateSessionRequest();
        r.setTitle("Backend engineer");
        r.setWeights(new WeightsDto(t, e, ed, s, c));
        return r;
    }

    @Test
    void createRejectsWeightsThatDoNotTotal100() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.createSession(withWeights(30, 25, 15, 20, 20), "r@example.com"));
        assertEquals("Weights must total 100", ex.getMessage());
    }

    @Test
    void updateRejectsWeightsThatDoNotTotal100() {
        assertThrows(ApiException.class,
                () -> service.updateSession("s1", withWeights(10, 10, 10, 10, 10), "r@example.com"));
    }
}
