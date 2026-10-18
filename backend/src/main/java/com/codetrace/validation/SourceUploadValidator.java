package com.codetrace.validation;

import com.codetrace.dto.ApiError;
import com.codetrace.exception.ApiValidationException;
import com.codetrace.exception.PayloadTooLargeException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Framework-independent validation of uploaded source files. Uploaded bytes are treated as untrusted data:
 * they are decoded strictly as UTF-8 text and never written to disk, compiled or executed.
 */
public final class SourceUploadValidator {

    public static final int MIN_FILES = 2;

    /** Raw upload as received (client filename and bytes). */
    public record RawUpload(String filename, byte[] bytes) {
    }

    private final int maxFiles;
    private final long maxFileBytes;
    private final Set<String> allowedExtensions;

    public SourceUploadValidator(int maxFiles, long maxFileBytes, List<String> allowedExtensions) {
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        Set<String> ext = new HashSet<>();
        for (String e : allowedExtensions) {
            String normalized = e.toLowerCase(Locale.ROOT).strip();
            ext.add(normalized.startsWith(".") ? normalized : "." + normalized);
        }
        this.allowedExtensions = Set.copyOf(ext);
    }

    public List<ValidatedSource> validate(List<RawUpload> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new ApiValidationException("At least " + MIN_FILES + " source files are required for a pairwise comparison");
        }
        if (uploads.size() > maxFiles) {
            throw new ApiValidationException("Too many files: " + uploads.size() + " uploaded, the maximum is " + maxFiles);
        }
        List<ApiError.FieldError> problems = new ArrayList<>();
        List<ValidatedSource> valid = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();

        for (RawUpload upload : uploads) {
            String name = FilenameSanitizer.sanitize(upload.filename());
            byte[] bytes = upload.bytes() == null ? new byte[0] : upload.bytes();
            if (bytes.length > maxFileBytes) {
                throw new PayloadTooLargeException("File '" + name + "' is " + bytes.length
                        + " bytes; the maximum is " + maxFileBytes + " bytes");
            }
            if (!allowedExtensions.contains(FilenameSanitizer.extension(name))) {
                problems.add(new ApiError.FieldError(name, "Unsupported file type; allowed: " + String.join(", ", allowedExtensions)));
                continue;
            }
            if (bytes.length == 0) {
                problems.add(new ApiError.FieldError(name, "File is empty"));
                continue;
            }
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException e) {
                problems.add(new ApiError.FieldError(name, "File is not valid UTF-8 text"));
                continue;
            }
            if (text.indexOf('\0') >= 0) {
                problems.add(new ApiError.FieldError(name, "File appears to be binary (contains NUL characters)"));
                continue;
            }
            if (text.isBlank()) {
                problems.add(new ApiError.FieldError(name, "File contains only whitespace"));
                continue;
            }
            valid.add(new ValidatedSource(name, uniqueName(name, usedNames), bytes.length, sha256(bytes), text));
        }

        if (!problems.isEmpty()) {
            throw new ApiValidationException(problems.size() + " file(s) failed validation", problems);
        }
        if (valid.size() < MIN_FILES) {
            throw new ApiValidationException("At least " + MIN_FILES + " valid source files are required for a pairwise comparison");
        }
        return valid;
    }

    /** Duplicate filenames are allowed (e.g. two students' Main.java) and disambiguated as "Main (2).java". */
    static String uniqueName(String name, Set<String> used) {
        String candidate = name;
        int counter = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            candidate = base + " (" + counter++ + ")" + ext;
        }
        return candidate;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
