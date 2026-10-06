/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SequenceWriter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Javalin {@link JsonMapper} implementation backed by Jackson 3.
 * Javalin ships its own {@code JavalinJackson} adapter but that one talks
 * to the classic {@code com.fasterxml.jackson} tree; we keep the entire
 * project on Jackson 3 ({@code tools.jackson}) so this adapter is the
 * only place we bridge the two.
 *
 * <p>Ported verbatim from ember's api/Jackson3Mapper.
 */
@SuppressWarnings("NullableProblems")
public class Jackson3Mapper implements JsonMapper {
    private final ObjectMapper mapper;

    public Jackson3Mapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> T fromJsonString(String json, Type targetType) {
        return mapper.readValue(json, mapper.getTypeFactory().constructType(targetType));
    }

    @Override
    public <T> T fromJsonStream(InputStream json, Type targetType) {
        return mapper.readValue(json, mapper.getTypeFactory().constructType(targetType));
    }

    @Override
    public String toJsonString(Object obj, Type type) {
        return switch (obj) {
            case String s -> s;
            case Object o -> mapper.writeValueAsString(o);
        };
    }

    @Override
    public InputStream toJsonStream(Object obj, Type type) {
        if (Objects.requireNonNull(obj) instanceof String s) {
            return new ByteArrayInputStream(s.getBytes());
        }
        try {
            return new ByteArrayInputStream(mapper.writeValueAsBytes(obj));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeToOutputStream(Stream<?> stream, OutputStream outputStream) {
        try (SequenceWriter sequenceWriter = mapper.writer().writeValuesAsArray(outputStream)) {
            stream.forEach(sequenceWriter::write);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
