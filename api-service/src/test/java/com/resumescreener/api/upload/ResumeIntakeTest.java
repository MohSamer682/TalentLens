package com.resumescreener.api.upload;

import com.resumescreener.api.upload.ResumeIntake.IntakeResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeIntakeTest {

    private static final int MB = 1024 * 1024;

    @Test
    void acceptsPdfByMagicBytes() {
        IntakeResult result = ResumeIntake.expand(files(pdf("jane.pdf", "%PDF-1.7 jane")));
        assertEquals(1, result.accepted().size());
        assertEquals("jane.pdf", result.accepted().get(0).fileName());
        assertEquals(64, result.accepted().get(0).sha256().length());
        assertTrue(result.rejected().isEmpty());
    }

    @Test
    void rejectsNonPdfEvenWithPdfContentType() {
        IntakeResult result = ResumeIntake.expand(files(pdf("fake.pdf", "hello")));
        assertEquals(0, result.accepted().size());
        assertEquals("Not a PDF", result.rejected().get(0).reason());
    }

    @Test
    void rejectsPdfOver10Mb() {
        byte[] big = new byte[10 * MB + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);
        IntakeResult result = ResumeIntake.expand(files(new MockMultipartFile("files", "big.pdf", "application/pdf", big)));
        assertEquals("Larger than 10 MB", result.rejected().get(0).reason());
    }

    @Test
    void expandsZipAndReportsNonPdfEntries() throws Exception {
        byte[] zip = zip(new String[]{"a.pdf", "dir/b.pdf", "notes.txt", "dir/"},
                new String[]{"%PDF-a", "%PDF-b", "notes", null});
        IntakeResult result = ResumeIntake.expand(files(new MockMultipartFile("files", "batch.zip", "application/zip", zip)));
        assertEquals(java.util.List.of("a.pdf", "b.pdf"), result.accepted().stream().map(ResumeIntake.IncomingResume::fileName).toList());
        assertEquals(1, result.rejected().size());
        assertEquals("notes.txt", result.rejected().get(0).fileName());
        assertEquals("Not a PDF", result.rejected().get(0).reason());
    }

    @Test
    void capsZipAt200Entries() throws Exception {
        String[] names = new String[201];
        String[] bodies = new String[201];
        for (int i = 0; i < 201; i++) {
            names[i] = "r" + i + ".pdf";
            bodies[i] = "%PDF-" + i;
        }
        IntakeResult result = ResumeIntake.expand(files(new MockMultipartFile("files", "many.zip", "application/zip", zip(names, bodies))));
        assertEquals(200, result.accepted().size());
        assertEquals("ZIP holds more than 200 files; the rest were skipped", result.rejected().get(0).reason());
    }

    @Test
    void rejectsOversizedZipEntryWithoutTrustingHeader() throws Exception {
        char[] filler = new char[11 * MB];
        Arrays.fill(filler, 'a');
        String body = "%PDF-" + new String(filler);
        IntakeResult result = ResumeIntake.expand(files(new MockMultipartFile("files", "huge.zip", "application/zip",
                zip(new String[]{"huge.pdf"}, new String[]{body}))));
        assertEquals(0, result.accepted().size());
        assertEquals("huge.pdf", result.rejected().get(0).fileName());
        assertEquals("Larger than 10 MB", result.rejected().get(0).reason());
    }

    @Test
    void rejectsCorruptZip() {
        byte[] corrupt = "PK\u0003\u0004garbage".getBytes(StandardCharsets.ISO_8859_1);
        IntakeResult result = ResumeIntake.expand(files(new MockMultipartFile("files", "broken.zip", "application/zip", corrupt)));
        assertEquals(0, result.accepted().size());
        assertEquals("Could not open ZIP", result.rejected().get(0).reason());
    }

    private static MultipartFile[] files(MultipartFile... files) {
        return files;
    }

    private static MockMultipartFile pdf(String name, String content) {
        return new MockMultipartFile("files", name, "application/pdf", content.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] zip(String[] names, String[] bodies) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < names.length; i++) {
                zip.putNextEntry(new ZipEntry(names[i]));
                if (bodies[i] != null) {
                    zip.write(bodies[i].getBytes(StandardCharsets.US_ASCII));
                }
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
