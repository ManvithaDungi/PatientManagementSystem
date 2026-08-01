package com.securehealth.backend.controller;

import com.securehealth.backend.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller for handling file uploads and downloads.
 * <p>
 * This controller provides endpoints for uploading files (which are encrypted at rest)
 * and retrieving/decrypting them.
 * </p>
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * Upload a file (image or document). The file is encrypted at rest using AES-256-GCM.
     * Returns the unique filename for linking to medical records.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, Authentication auth) {
        try {
            String filename = fileStorageService.storeFile(file, auth.getName());
            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded and encrypted successfully.",
                    "filename", filename
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Retrieve and decrypt a stored file by its filename. Only the uploader, the
     * owning patient, a provider with active consent, or an admin may do so.
     */
    @GetMapping("/{filename}")
    public ResponseEntity<?> getFile(@PathVariable String filename, Authentication auth) {
        try {
            String role = currentRole(auth);
            byte[] fileData = fileStorageService.loadFile(filename, auth.getName(), role);
            String extension = fileStorageService.getOriginalExtension(filename);

            MediaType mediaType = getMediaType(extension);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename.replace(".enc", "") + "\"")
                    .body(fileData);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Unable to retrieve file" : e.getMessage();
            if (message.startsWith("403")) {
                return ResponseEntity.status(403).body(Map.of("message", message));
            }
            if (message.startsWith("404")) {
                return ResponseEntity.status(404).body(Map.of("message", message));
            }
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }

    private String currentRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("UNKNOWN");
    }

    private MediaType getMediaType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
