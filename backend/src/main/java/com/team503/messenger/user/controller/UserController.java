package com.team503.messenger.user.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.user.dto.UpdateProfileRequest;
import com.team503.messenger.user.dto.UserResponse;
import com.team503.messenger.user.dto.UserSearchResult;
import com.team503.messenger.user.service.UserService;
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
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getCurrentUser(currentUser.id());
    }

    @PatchMapping("/me/profile")
    UserResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateCurrentUserProfile(currentUser.id(), request);
    }

    /**
     * Search active users by username or display name.
     * Used by the New Conversation modal.
     */
    @GetMapping("/search")
    List<UserSearchResult> searchUsers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam("q") String query
    ) {
        return userService.searchUsers(query);
    }
}
