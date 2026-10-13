package com.team503.messenger.call.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CallTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 13, 12, 0);
    private static final LocalDateTime T1 = T0.plusSeconds(30);

    private static Call ringing() {
        return Call.ringing(1L, 10L, 20L, Call.CallType.VOICE, "call-1-abc", T0);
    }

    @Test
    void startsRinging() {
        Call call = ringing();
        assertThat(call.getStatus()).isEqualTo(Call.Status.RINGING);
        assertThat(call.getStartedAt()).isEqualTo(T0);
    }

    @Test
    void rejectsSelfCall() {
        assertThatThrownBy(() -> Call.ringing(1L, 10L, 10L, Call.CallType.VOICE, "c", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptThenEndByCallee() {
        Call call = ringing();
        call.markAccepted(T1);
        call.markEnded(20L, T1.plusSeconds(60));

        assertThat(call.getStatus()).isEqualTo(Call.Status.ENDED);
        assertThat(call.getAnsweredAt()).isEqualTo(T1);
        assertThat(call.getEndedBy()).isEqualTo(20L);
        assertThat(call.getEndReason()).isEqualTo(Call.EndReason.ENDED_BY_CALLEE);
    }

    @Test
    void endByCallerUsesCallerReason() {
        Call call = ringing();
        call.markAccepted(T1);
        call.markEnded(10L, T1);
        assertThat(call.getEndReason()).isEqualTo(Call.EndReason.ENDED_BY_CALLER);
    }

    @Test
    void rejectCancelAndMissedRecordReasons() {
        Call rejected = ringing();
        rejected.markRejected(20L, T1);
        assertThat(rejected.getEndReason()).isEqualTo(Call.EndReason.REJECTED_BY_CALLEE);
        assertThat(rejected.getEndedBy()).isEqualTo(20L);

        Call cancelled = ringing();
        cancelled.markCancelled(T1);
        assertThat(cancelled.getEndReason()).isEqualTo(Call.EndReason.CANCELLED_BY_CALLER);
        assertThat(cancelled.getEndedBy()).isEqualTo(10L);

        Call missed = ringing();
        missed.markMissed(T1);
        assertThat(missed.getStatus()).isEqualTo(Call.Status.MISSED);
        assertThat(missed.getEndedBy()).isNull();
    }

    @Test
    void terminalStatesCannotTransition() {
        Call call = ringing();
        call.markRejected(20L, T1);

        assertThatThrownBy(() -> call.markAccepted(T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> call.markCancelled(T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> call.markEnded(10L, T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void endRequiresAccepted() {
        assertThatThrownBy(() -> ringing().markEnded(10L, T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedIsIgnoredOnceTerminal() {
        Call call = ringing();
        call.markCancelled(T1);
        call.markFailed(T1.plusSeconds(1));
        assertThat(call.getStatus()).isEqualTo(Call.Status.CANCELLED);
    }
}
