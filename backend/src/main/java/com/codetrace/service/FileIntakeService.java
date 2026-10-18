package com.codetrace.service;

import com.codetrace.config.CodeTraceProperties;
import com.codetrace.exception.ApiValidationException;
import com.codetrace.exception.PayloadTooLargeException;
import com.codetrace.validation.FilenameSanitizer;
import com.codetrace.validation.SourceUploadValidator;
import com.codetrace.validation.ValidatedSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * File validation and intake: reads multipart uploads into memory and validates them.
 * Client-supplied MIME types are ignored; files are never stored on disk by the application.
 */
@Service
public class FileIntakeService {

    private final SourceUploadValidator validator;
    private final long maxFileBytes;

    public FileIntakeService(CodeTraceProperties properties) {
        CodeTraceProperties.Upload upload = properties.upload();
        this.validator = new SourceUploadValidator(upload.maxFiles(), upload.maxFileBytes(), upload.allowedExtensions());
        this.maxFileBytes = upload.maxFileBytes();
    }

    public List<ValidatedSource> intake(List<MultipartFile> files) {
        List<SourceUploadValidator.RawUpload> raw = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null) {
                    continue;
                }
                String name = FilenameSanitizer.sanitize(file.getOriginalFilename());
                if (file.getSize() > maxFileBytes) {
                    throw new PayloadTooLargeException("File '" + name + "' is " + file.getSize()
                            + " bytes; the maximum is " + maxFileBytes + " bytes");
                }
                try {
                    raw.add(new SourceUploadValidator.RawUpload(file.getOriginalFilename(), file.getBytes()));
                } catch (IOException e) {
                    throw new ApiValidationException("Could not read uploaded file '" + name + "'");
                }
            }
        }
        return validator.validate(raw);
    }
}
