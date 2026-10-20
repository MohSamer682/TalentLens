package com.resumescreener.api.reschedule;

import com.resumescreener.common.reschedule.RescheduleStatus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RescheduleStatusTest {

    private static final List<RescheduleStatus> ANSWERS = List.of(RescheduleStatus.ACCEPTED, RescheduleStatus.DECLINED, RescheduleStatus.WITHDRAWN);

    @Test
    void transitionsOnlyFromPending() {
        for (RescheduleStatus from : RescheduleStatus.values()) {
            for (RescheduleStatus to : ANSWERS) {
                if (from == RescheduleStatus.PENDING) {
                    assertThat(from.moveTo(to)).isEqualTo(to);
                } else {
                    assertThatThrownBy(() -> from.moveTo(to)).as(from + " -> " + to).isInstanceOf(IllegalStateException.class);
                }
            }
        }
    }

    @Test
    void nothingMovesBackToPending() {
        assertThatThrownBy(() -> RescheduleStatus.PENDING.moveTo(RescheduleStatus.PENDING)).isInstanceOf(IllegalStateException.class);
    }
}
