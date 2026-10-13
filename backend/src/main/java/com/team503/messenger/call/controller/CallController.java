package com.team503.messenger.call.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.call.dto.CallHistoryResponse;
import com.team503.messenger.call.dto.CallResponse;
import com.team503.messenger.call.dto.CallTokenResponse;
import com.team503.messenger.call.dto.StartCallRequest;
import com.team503.messenger.call.service.CallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calls")
@Tag(name = "Calls", description = "1-to-1 voice call lifecycle, Agora token issuance, call history.")
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping
    @Operation(summary = "Start a new 1-to-1 voice call")
    CallResponse startCall(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody StartCallRequest request
    ) {
        return callService.startCall(currentUser.id(), request.conversationId(), request.calleeId());
    }

    @PostMapping("/{callId}/accept")
    @Operation(summary = "Accept an incoming call")
    CallResponse acceptCall(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long callId
    ) {
        return callService.acceptCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/reject")
    @Operation(summary = "Reject an incoming call")
    CallResponse rejectCall(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long callId
    ) {
        return callService.rejectCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/cancel")
    @Operation(summary = "Cancel an outgoing call that has not been answered yet")
    CallResponse cancelCall(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long callId
    ) {
        return callService.cancelCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/end")
    @Operation(summary = "End an active call")
    CallResponse endCall(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long callId
    ) {
        return callService.endCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/token")
    @Operation(
            summary = "Issue a short-lived Agora RTC token for this call",
            description = "Returns appId, channel name and token. The token expires in seconds (see expiresInSeconds)."
    )
    CallTokenResponse issueToken(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long callId
    ) {
        return callService.issueToken(currentUser.id(), callId);
    }

    @GetMapping("/{callId}")
    @Operation(summary = "Get a single call by id")
    CallResponse getCall(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long callId
    ) {
        return callService.getCall(currentUser.id(), callId);
    }

    @GetMapping
    @Operation(summary = "List the current user's call history (paginated)")
    CallHistoryResponse listHistory(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return callService.listHistory(currentUser.id(), page, size);
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "List call history for a specific conversation (paginated)")
    CallHistoryResponse listConversationHistory(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return callService.listConversationHistory(currentUser.id(), conversationId, page, size);
    }
}
