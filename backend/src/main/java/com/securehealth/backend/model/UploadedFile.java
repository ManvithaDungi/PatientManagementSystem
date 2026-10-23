package com.securehealth.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity tracking who uploaded each encrypted file stored by {@link com.securehealth.backend.service.FileStorageService}.
 * <p>
 * This is the ownership record used to authorize downloads: a file may otherwise
 * never be linked to a clinical record (e.g. a lab result file uploaded but not yet
 * attached), so the uploader is the only reliable owner until it is linked.
 * </p>
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "uploaded_files")
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String filename;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private Login uploadedBy;

    @Column(updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
