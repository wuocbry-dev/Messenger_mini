package com.team503.messenger.call.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CallTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Test
    void ringingHasInitialState() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "call-1-abc", NOW);

        assertThat(call.getStatus()).isEqualTo(Call.Status.RINGING);
        assertThat(call.getAnsweredAt()).isNull();
        assertThat(call.getEndedAt()).isNull();
        assertThat(call.getCallerId()).isEqualTo(1L);
        assertThat(call.getCalleeId()).isEqualTo(2L);
        assertThat(call.getCallType()).isEqualTo(Call.CallType.VOICE);
    }

    @Test
    void ringingRejectsSelfCall() {
        assertThatThrownBy(() -> Call.ringing(1L, 1L, 1L, Call.CallType.VOICE, "ch", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptTransitionsRingingToAccepted() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "ch", NOW);
        call.markAccepted(NOW.plusSeconds(2));
        assertThat(call.getStatus()).isEqualTo(Call.Status.ACCEPTED);
        assertThat(call.getAnsweredAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void rejectTransitionsRingingToRejected() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "ch", NOW);
        call.markRejected(NOW.plusSeconds(2));
        assertThat(call.getStatus()).isEqualTo(Call.Status.REJECTED);
        assertThat(call.getEndedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(call.getEndedBy()).isEqualTo(2L);
        assertThat(call.getEndReason()).isEqualTo(Call.EndReason.REJECTED_BY_CALLEE.name());
    }

    @Test
    void cancelTransitionsRingingToCancelled() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "ch", NOW);
        call.markCancelled(NOW.plusSeconds(2));
        assertThat(call.getStatus()).isEqualTo(Call.Status.CANCELLED);
        assertThat(call.getEndedBy()).isEqualTo(1L);
    }

    @Test
    void endRequiresAcceptedState() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "ch", NOW);
        // Cannot end a non-ACCEPTED call.
        assertThatThrownBy(() -> call.markEnded(1L, Call.EndReason.ENDED_BY_CALLER, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void endTransitionsAcceptedToEnded() {
        Call call = Call.ringing(1L, 1L, 2L, Call.CallType.VOICE, "ch", NOW);
        call.markAccepted(NOW.plusSeconds(1));
        call.markEnded(2L, Call.EndReason.ENDED_BY_CALLEE, NOW.plusSeconds(30));
        assertThat(call.getStatus()).isEqualTo(Call.Status.ENDED);
        assertThat(call.getEndedBy()).isEqualTo(2L);
        assertThat(call.getEndReason()).isEqualTo(Call.EndReason.ENDED_BY_CALLEE.name());
    }

    @Test
    void terminalStatesAreTerminal() {
        assertThat(Call.Status.ENDED.isTerminal()).isTrue();
        assertThat(Call.Status.REJECTED.isTerminal()).isTrue();
        assertThat(Call.Status.CANCELLED.isTerminal()).isTrue();
        assertThat(Call.Status.MISSED.isTerminal()).isTrue();
        assertThat(Call.Status.FAILED.isTerminal()).isTrue();
        assertThat(Call.Status.RINGING.isTerminal()).isFalse();
        assertThat(Call.Status.ACCEPTED.isTerminal()).isFalse();
    }
}
