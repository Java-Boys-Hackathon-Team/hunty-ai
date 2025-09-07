package ru.javaboys.huntyhr.service;

import io.jmix.core.FileRef;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.InputStream;

public interface DocParseService {
    String parseToText(FileRef fileRef);
    String parseToText(InputStream is, @Nullable String originalName);
}
