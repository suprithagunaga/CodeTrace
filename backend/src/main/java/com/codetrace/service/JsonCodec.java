package com.codetrace.service;

import com.codetrace.dto.AlgorithmConfigDto;
import com.codetrace.dto.ResultMetadataDto;
import com.codetrace.similarity.SimilarityConfig;
import com.codetrace.tokenizer.NormalizationMode;
import com.codetrace.tokenizer.NormalizationOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** Serializes the JSON documents stored in TEXT columns (configuration, metadata, warnings). */
@Component
public class JsonCodec {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public JsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + value.getClass().getSimpleName(), e);
        }
    }

    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public ResultMetadataDto readMetadata(String json) {
        try {
            return mapper.readValue(json, ResultMetadataDto.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return new ResultMetadataDto(0, 0, 0, 0, 0, 0, false, List.of("Stored metadata could not be read"));
        }
    }

    public AlgorithmConfigDto readConfig(String json) {
        try {
            return mapper.readValue(json, AlgorithmConfigDto.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return toDto(SimilarityConfig.defaults());
        }
    }

    public static AlgorithmConfigDto toDto(SimilarityConfig config) {
        return new AlgorithmConfigDto(config.kGramSize(), config.windowSize(), config.jaccardNgram(),
                config.normalization().mode().name(), config.normalization().normalizeNumbers(),
                AlgorithmConfigDto.COMBINED_RULE);
    }

    public static SimilarityConfig fromDto(AlgorithmConfigDto dto) {
        NormalizationMode mode;
        try {
            mode = NormalizationMode.valueOf(dto.normalizationMode());
        } catch (IllegalArgumentException | NullPointerException e) {
            mode = NormalizationMode.IDENTIFIERS;
        }
        return new SimilarityConfig(dto.kgramSize(), dto.windowSize(), dto.jaccardNgram(),
                new NormalizationOptions(mode, dto.normalizeNumbers()));
    }
}
