package com.securehealth.backend.service;

import com.securehealth.backend.model.LabTest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.MedicalRecord;
import com.securehealth.backend.model.UploadedFile;
import com.securehealth.backend.repository.LabTestRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.MedicalRecordRepository;
import com.securehealth.backend.repository.UploadedFileRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for storing and retrieving encrypted files.
 * Uses AES-256-GCM for encryption at rest.
 * The storage directory is configurable — point it to a local path or a mounted network/S3 volume.
 */
@Service
public class FileStorageService {

    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "txt", "csv"
    );

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.encryption.key}")
    private String encryptionKeyBase64;

    @Autowired private UploadedFileRepository uploadedFileRepository;
    @Autowired private MedicalRecordRepository medicalRecordRepository;
    @Autowired private LabTestRepository labTestRepository;
    @Autowired private LoginRepository loginRepository;
    @Autowired private PatientAccessValidator patientAccessValidator;

    /**
     * Stores an uploaded file with AES-256-GCM encryption at rest.
     * <p>
     * Validates the file extension against an allowlist and generates a 
     * unique filename for secure storage.
     * </p>
     *
     * @param file the {@link MultipartFile} to store
     * @param uploaderEmail the email of the authenticated user uploading the file
     * @return the unique filename generated for the stored file
     * @throws IOException if an I/O error occurs during storage
     * @throws RuntimeException if the file is empty or the type is not allowed
     */
    public String storeFile(MultipartFile file, String uploaderEmail) throws IOException {
        // 1. Validate
        if (file.isEmpty()) {
            throw new RuntimeException("Cannot upload empty file.");
        }

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new RuntimeException("File type not allowed: " + extension
                    + ". Allowed: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        Login uploader = loginRepository.findByEmail(uploaderEmail)
                .orElseThrow(() -> new RuntimeException("404: Uploader not found"));

        // 2. Generate unique filename
        String uniqueFilename = UUID.randomUUID() + "." + extension + ".enc";

        // 3. Ensure upload directory exists
        Path dirPath = Paths.get(uploadDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // 4. Encrypt and save
        try {
            byte[] plainBytes = file.getBytes();
            byte[] encryptedBytes = encrypt(plainBytes);
            Files.write(dirPath.resolve(uniqueFilename), encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt and store file: " + e.getMessage(), e);
        }

        // 5. Record ownership - this is the only record of who owns the file until
        // it's linked to a medical record or lab test.
        UploadedFile record = new UploadedFile();
        record.setFilename(uniqueFilename);
        record.setUploadedBy(uploader);
        uploadedFileRepository.save(record);

        return uniqueFilename;
    }

    /**
     * Retrieves and decrypts a stored file based on its filename, after verifying
     * the requester is authorized to see it (the uploader, the owning patient,
     * a provider with consent for the linked record, or an admin).
     *
     * @param filename the unique name of the encrypted file
     * @param requesterEmail the email of the authenticated requester
     * @param requesterRole the role of the authenticated requester
     * @return the decrypted byte array of the file content
     * @throws IOException if an I/O error occurs during retrieval
     * @throws RuntimeException if the file is not found, access is forbidden, or decryption fails
     */
    public byte[] loadFile(String filename, String requesterEmail, String requesterRole) throws IOException {
        Path filePath = Paths.get(uploadDir).resolve(filename);

        if (!Files.exists(filePath)) {
            throw new RuntimeException("404: File not found: " + filename);
        }

        ensureAccess(filename, requesterEmail, requesterRole);

        try {
            byte[] encryptedBytes = Files.readAllBytes(filePath);
            return decrypt(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt file: " + e.getMessage(), e);
        }
    }

    /**
     * Authorizes access to a stored file. A requester may access a file if they
     * uploaded it, are an admin, or the file is linked to a medical record/lab test
     * they're entitled to see (patient owner, or provider with active consent).
     * Denies access to files not yet linked to any record unless the requester is
     * the uploader or an admin.
     */
    private void ensureAccess(String filename, String requesterEmail, String requesterRole) {
        if ("ADMIN".equals(requesterRole)) {
            return;
        }

        Optional<UploadedFile> uploadRecord = uploadedFileRepository.findByFilename(filename);
        if (uploadRecord.isPresent() && uploadRecord.get().getUploadedBy().getEmail().equals(requesterEmail)) {
            return;
        }

        Optional<MedicalRecord> medicalRecord = medicalRecordRepository.findByAttachmentUrl(filename);
        if (medicalRecord.isPresent()) {
            patientAccessValidator.validateAccess(
                    medicalRecord.get().getPatient().getProfileId(), requesterRole, requesterEmail, "MEDICAL_RECORDS");
            return;
        }

        Optional<LabTest> labTest = labTestRepository.findByFileUrl(filename);
        if (labTest.isPresent()) {
            patientAccessValidator.validateAccess(
                    labTest.get().getPatient().getProfileId(), requesterRole, requesterEmail, "LAB_RESULTS");
            return;
        }

        throw new RuntimeException("403 Forbidden: You are not authorized to access this file");
    }

    /**
     * Returns the original file extension from an encrypted filename.
     * e.g., "uuid.pdf.enc" → "pdf"
     */
    public String getOriginalExtension(String encryptedFilename) {
        // Strip .enc suffix, then get the real extension
        String withoutEnc = encryptedFilename.replace(".enc", "");
        return getExtension(withoutEnc);
    }

    // --- Encryption ---

    private byte[] encrypt(byte[] data) throws Exception {
        SecretKey key = getKey();
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(data);

        // Prepend IV to ciphertext for storage: [IV | ciphertext]
        byte[] result = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);

        return result;
    }

    private byte[] decrypt(byte[] data) throws Exception {
        SecretKey key = getKey();

        // Extract IV from first 12 bytes
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);

        // Extract ciphertext
        byte[] cipherText = new byte[data.length - GCM_IV_LENGTH];
        System.arraycopy(data, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(AES_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        return cipher.doFinal(cipherText);
    }

    private SecretKey getKey() {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
