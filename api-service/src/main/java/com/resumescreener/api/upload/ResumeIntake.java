package com.resumescreener.api.upload;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Turns uploaded files into validated PDFs. A ZIP is expanded in memory; every entry is read through a
 * bounded loop because a ZIP header can lie about the uncompressed size.
 */
public final class ResumeIntake {

    public static final long MAX_PDF_BYTES = 10L * 1024 * 1024;
    public static final int MAX_ZIP_ENTRIES = 200;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    public record IncomingResume(String fileName, byte[] bytes, String sha256) {}

    public record RejectedFile(String fileName, String reason) {}

    public record IntakeResult(List<IncomingResume> accepted, List<RejectedFile> rejected) {}

    private ResumeIntake() {
    }

    public static IntakeResult expand(MultipartFile[] files) {
        List<IncomingResume> accepted = new ArrayList<>();
        List<RejectedFile> rejected = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = baseName(file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                byte[] head = in.readNBytes(4);
                if (Arrays.equals(head, ZIP_MAGIC)) {
                    expandZip(name, file, accepted, rejected);
                } else {
                    // MultipartFile size is what the container stored, so it is trustworthy here
                    if (file.getSize() > MAX_PDF_BYTES) {
                        rejected.add(new RejectedFile(name, "Larger than 10 MB"));
                        continue;
                    }
                    addIfPdf(name, file.getBytes(), accepted, rejected);
                }
            } catch (IOException e) {
                rejected.add(new RejectedFile(name, "Could not read the file"));
            }
        }
        return new IntakeResult(accepted, rejected);
    }

    private static void expandZip(String zipName, MultipartFile file, List<IncomingResume> accepted,
                                  List<RejectedFile> rejected) throws IOException {
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            boolean sawAny = false;
            while ((entry = zip.getNextEntry()) != null) {
                sawAny = true;
                if (entry.isDirectory()) {
                    continue;
                }
                String name = baseName(entry.getName());
                if (++entries > MAX_ZIP_ENTRIES) {
                    rejected.add(new RejectedFile(zipName, "ZIP holds more than 200 files; the rest were skipped"));
                    return;
                }
                byte[] bytes = readBounded(zip, MAX_PDF_BYTES);
                if (bytes == null) {
                    rejected.add(new RejectedFile(name, "Larger than 10 MB"));
                    continue;
                }
                addIfPdf(name, bytes, accepted, rejected);
            }
            if (!sawAny) {
                rejected.add(new RejectedFile(zipName, "Could not open ZIP"));
            }
        } catch (ZipException e) {
            rejected.add(new RejectedFile(zipName, "Could not open ZIP"));
        }
    }

    /** Reads at most {@code limit} bytes; returns null when the stream holds more. */
    private static byte[] readBounded(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > limit) {
                return null;
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void addIfPdf(String name, byte[] bytes, List<IncomingResume> accepted, List<RejectedFile> rejected) {
        if (bytes.length < PDF_MAGIC.length || !Arrays.equals(Arrays.copyOf(bytes, PDF_MAGIC.length), PDF_MAGIC)) {
            rejected.add(new RejectedFile(name, "Not a PDF"));
            return;
        }
        accepted.add(new IncomingResume(name, bytes, sha256(bytes)));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static String baseName(String name) {
        if (name == null || name.isBlank()) {
            return "resume.pdf";
        }
        String normalized = name.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
}
