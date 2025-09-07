package ru.javaboys.huntyhr.service.impl;

import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;
import ru.javaboys.huntyhr.service.DocParseService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class DocParseServiceImpl implements DocParseService {
    private final FileStorageLocator fileStorageLocator;

    public DocParseServiceImpl(FileStorageLocator fileStorageLocator) {
        this.fileStorageLocator = fileStorageLocator;
    }

    @Override
    public String parseToText(FileRef fileRef) {
        FileStorage fs = fileStorageLocator.getByName(fileRef.getStorageName());
        try (InputStream is = fs.openStream(fileRef)) {
            return parseToText(is, fileRef.getFileName());
        } catch (Exception e) {
            log.error("Failed to open stream for {}", fileRef, e);
            throw new RuntimeException("Unable to open file content", e);
        }
    }

    @Override
    public String parseToText(InputStream is, @Nullable String originalName) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();

            if (originalName != null) {
                metadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY, originalName);
            }

            ParseContext context = new ParseContext();
            parser.parse(is, handler, metadata, context);
            return handler.toString();
        } catch (IOException | TikaException | SAXException e) {
            log.error("Failed to parse stream (name={})", originalName, e);
            throw new RuntimeException("Unable to parse document to text", e);
        }
    }
}
