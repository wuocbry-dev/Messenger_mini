package com.team503.messenger.file.controller;

import com.team503.messenger.auth.security.AuthenticatedUser;
import com.team503.messenger.file.dto.FileResponse;
import com.team503.messenger.file.dto.UploadFileRequest;
import com.team503.messenger.file.dto.UploadFileResponse;
import com.team503.messenger.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files", description = "Cloudflare R2-backed file metadata and presigned upload/download URLs.")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    @Operation(
            summary = "Prepare a file upload",
            description = "Returns a presigned R2 PUT URL and the file metadata. The client uploads the binary directly to R2."
    )
    UploadFileResponse prepareUpload(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UploadFileRequest request
    ) {
        return fileService.prepareUpload(currentUser.id(), request);
    }

    @GetMapping("/{fileId}/download")
    @Operation(
            summary = "Get a presigned download URL for an uploaded file",
            description = "The URL is short-lived; do not persist it."
    )
    FileResponse getFile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long fileId
    ) {
        return fileService.getFile(currentUser.id(), fileId);
    }
}
