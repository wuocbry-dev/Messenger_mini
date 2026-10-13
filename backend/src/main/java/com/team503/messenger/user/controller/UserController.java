package com.team503.messenger.user.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.user.dto.UpdateProfileRequest;
import com.team503.messenger.user.dto.UserResponse;
import com.team503.messenger.user.dto.UserSearchResult;
import com.team503.messenger.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile, lookup and search.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user profile")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getCurrentUser(currentUser.id());
    }

    @PatchMapping("/me/profile")
    @Operation(summary = "Update the current user's display name, email or status")
    UserResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateCurrentUserProfile(currentUser.id(), request);
    }

    /**
     * Get all active users (for user directory).
     */
    @GetMapping
    @Operation(summary = "List all active users in the directory")
    List<UserSearchResult> getAllUsers(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getAllActiveUsers(currentUser.id());
    }

    /**
     * Search active users by username or display name.
     * Used by the New Conversation modal.
     */
    @GetMapping("/search")
    @Operation(summary = "Search users by username or display name prefix")
    List<UserSearchResult> searchUsers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam("q") String query
    ) {
        return userService.searchUsers(query);
    }
}
