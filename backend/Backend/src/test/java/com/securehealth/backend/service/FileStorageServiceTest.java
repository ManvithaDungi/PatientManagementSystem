package com.securehealth.backend.service;

import com.securehealth.backend.model.LabTest;
import com.securehealth.backend.model.Login;
import com.securehealth.backend.model.MedicalRecord;
import com.securehealth.backend.model.PatientProfile;
import com.securehealth.backend.model.UploadedFile;
import com.securehealth.backend.repository.LabTestRepository;
import com.securehealth.backend.repository.LoginRepository;
import com.securehealth.backend.repository.MedicalRecordRepository;
import com.securehealth.backend.repository.UploadedFileRepository;
import com.securehealth.backend.security.PatientAccessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileStorageServiceTest {

    // A genuine 32-byte AES-256 key. Note: the app.encryption.key fallback in application.properties
    // ("this is a 32 byte key for testing!") is actually 34 bytes and is NOT a valid AES key - see Phase 2.
    private static final String TEST_KEY_BASE64 = "6L72NvYRPUZTvn8XFA4kUnwOBTtnBZ8KfEkvATfeTqc=";

    @Mock private UploadedFileRepository uploadedFileRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private LabTestRepository labTestRepository;
    @Mock private LoginRepository loginRepository;
    @Mock private PatientAccessValidator patientAccessValidator;

    @InjectMocks
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    private Login uploader;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(fileStorageService, "encryptionKeyBase64", TEST_KEY_BASE64);

        uploader = new Login();
        uploader.setUserId(1L);
        uploader.setEmail("uploader@mail.com");
    }

    private MockMultipartFile samplePdf() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", "clinical data".getBytes());
    }

    @Test
    void storeFile_RecordsUploaderOwnership() throws Exception {
        when(loginRepository.findByEmail("uploader@mail.com")).thenReturn(Optional.of(uploader));

        String filename = fileStorageService.storeFile(samplePdf(), "uploader@mail.com");

        assertNotNull(filename);
        assertTrue(filename.endsWith(".pdf.enc"));

        ArgumentCaptor<UploadedFile> captor = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFileRepository).save(captor.capture());
        assertEquals(filename, captor.getValue().getFilename());
        assertEquals(uploader, captor.getValue().getUploadedBy());
    }

    @Test
    void loadFile_AsUploader_Succeeds() throws Exception {
        when(loginRepository.findByEmail("uploader@mail.com")).thenReturn(Optional.of(uploader));
        String filename = fileStorageService.storeFile(samplePdf(), "uploader@mail.com");

        UploadedFile record = new UploadedFile();
        record.setFilename(filename);
        record.setUploadedBy(uploader);
        when(uploadedFileRepository.findByFilename(filename)).thenReturn(Optional.of(record));

        byte[] result = fileStorageService.loadFile(filename, "uploader@mail.com", "PATIENT");

        assertArrayEquals("clinical data".getBytes(), result);
    }

    @Test
    void loadFile_AsAdmin_BypassesOwnershipCheck() throws Exception {
        when(loginRepository.findByEmail("uploader@mail.com")).thenReturn(Optional.of(uploader));
        String filename = fileStorageService.storeFile(samplePdf(), "uploader@mail.com");

        byte[] result = fileStorageService.loadFile(filename, "admin@mail.com", "ADMIN");

        assertArrayEquals("clinical data".getBytes(), result);
        // Admins bypass ownership resolution entirely - no need to look up the upload record.
        verify(uploadedFileRepository, never()).findByFilename(anyString());
    }

    @Test
    void loadFile_LinkedToMedicalRecord_DelegatesToPatientAccessValidator() throws Exception {
        when(loginRepository.findByEmail("uploader@mail.com")).thenReturn(Optional.of(uploader));
        String filename = fileStorageService.storeFile(samplePdf(), "uploader@mail.com");

        // A different requester than the uploader; ownership must come from the linked record.
        when(uploadedFileRepository.findByFilename(filename)).thenReturn(Optional.empty());

        PatientProfile patient = new PatientProfile();
        patient.setProfileId(42L);
        MedicalRecord record = new MedicalRecord();
        record.setPatient(patient);
        when(medicalRecordRepository.findByAttachmentUrl(filename)).thenReturn(Optional.of(record));

        // Validator grants access (e.g. requester is the owning patient, or a consented provider)
        doNothing().when(patientAccessValidator).validateAccess(42L, "DOCTOR", "dr.house@mail.com", "MEDICAL_RECORDS");

        byte[] result = fileStorageService.loadFile(filename, "dr.house@mail.com", "DOCTOR");

        assertArrayEquals("clinical data".getBytes(), result);
        verify(patientAccessValidator).validateAccess(42L, "DOCTOR", "dr.house@mail.com", "MEDICAL_RECORDS");
    }

    @Test
    void loadFile_LinkedToLabTest_ValidatorDenies_ThrowsForbidden() throws Exception {
        when(loginRepository.findByEmail("uploader@mail.com")).thenReturn(Optional.of(uploader));
        String filename = fileStorageService.storeFile(samplePdf(), "uploader@mail.com");

        when(uploadedFileRepository.findByFilename(filename)).thenReturn(Optional.empty());
        when(medicalRecordRepository.findByAttachmentUrl(filename)).thenReturn(Optional.empty());

        PatientProfile patient = new PatientProfile();
        patient.setProfileId(7L);
        LabTest labTest = new LabTest();
        labTest.setPatient(patient);
        when(labTestRepository.findByFileUrl(filename)).thenReturn(Optional.of(labTest));

        doThrow(new RuntimeException("403 Forbidden: No active patient consent on file for LAB_RESULTS"))
                .when(patientAccessValidator).validateAccess(7L, "LAB_TECHNICIAN", "tech@mail.com", "LAB_RESULTS");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fileStorageService.loadFile(filename, "tech@mail.com", "LAB_TECHNICIAN"));

        assertTrue(ex.getMessage().contains("403"));
    }

    @Test
    void loadFile_UnlinkedFile_NotUploaderNorAdmin_ThrowsForbidden() throws Exception {
        when(loginRepository.findByEmail("uploader@mail.com")).thenReturn(Optional.of(uploader));
        String filename = fileStorageService.storeFile(samplePdf(), "uploader@mail.com");

        when(uploadedFileRepository.findByFilename(filename)).thenReturn(Optional.empty());
        when(medicalRecordRepository.findByAttachmentUrl(filename)).thenReturn(Optional.empty());
        when(labTestRepository.findByFileUrl(filename)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fileStorageService.loadFile(filename, "stranger@mail.com", "PATIENT"));

        assertTrue(ex.getMessage().contains("403"));
    }

    @Test
    void loadFile_UnknownFilename_ThrowsNotFound() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fileStorageService.loadFile("does-not-exist.pdf.enc", "someone@mail.com", "PATIENT"));

        assertTrue(ex.getMessage().contains("404"));
    }
}
