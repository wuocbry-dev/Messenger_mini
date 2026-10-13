package com.team503.messenger.call.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.call.dto.CallHistoryResponse;
import com.team503.messenger.call.dto.CallResponse;
import com.team503.messenger.call.dto.CallTokenResponse;
import com.team503.messenger.call.dto.StartCallRequest;
import com.team503.messenger.call.service.CallService;
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
public class CallController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping
    CallResponse startCall(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody StartCallRequest request
    ) {
        return callService.startCall(
                currentUser.id(), request.conversationId(), request.calleeId(), request.callTypeOrDefault());
    }

    @PostMapping("/{callId}/accept")
    CallResponse acceptCall(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long callId) {
        return callService.acceptCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/reject")
    CallResponse rejectCall(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long callId) {
        return callService.rejectCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/cancel")
    CallResponse cancelCall(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long callId) {
        return callService.cancelCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/end")
    CallResponse endCall(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long callId) {
        return callService.endCall(currentUser.id(), callId);
    }

    @PostMapping("/{callId}/token")
    CallTokenResponse issueToken(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long callId) {
        return callService.issueToken(currentUser.id(), callId);
    }

    @GetMapping("/{callId}")
    CallResponse getCall(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long callId) {
        return callService.getCall(currentUser.id(), callId);
    }

    @GetMapping
    CallHistoryResponse listHistory(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return callService.listHistory(currentUser.id(), Math.max(page, 0), clamp(size));
    }

    @GetMapping("/conversations/{conversationId}")
    CallHistoryResponse listConversationHistory(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return callService.listConversationHistory(currentUser.id(), conversationId, Math.max(page, 0), clamp(size));
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
