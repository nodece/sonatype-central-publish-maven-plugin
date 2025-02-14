/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.github.nodece.sonatype.central.publish.plugin;

import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.RepositorySystemSession;

@Slf4j
@Data
public class ZipBundle {
    private static final Map<String, Function<InputStream, byte[]>> checkSumAlgorithms = new HashMap<>();

    static {
        checkSumAlgorithms.put("md5", is -> {
            try {
                return md5Hex(is).getBytes(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        checkSumAlgorithms.put("sha1", is -> {
            try {
                return sha1Hex(is).getBytes(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void createChecksumFile(File file) throws IOException {
        if (file == null || file.isDirectory()) {
            return;
        }
        String fileName = file.getName();
        if (fileName.endsWith(".md5") || fileName.endsWith(".sha1") || fileName.endsWith(".asc")) {
            return;
        }
        Path filePath = file.toPath();
        for (Entry<String, Function<InputStream, byte[]>> entry : checkSumAlgorithms.entrySet()) {
            Path checksumFilePath = Paths.get(file + "." + entry.getKey());
            try (InputStream is = Files.newInputStream(filePath)) {
                Files.write(checksumFilePath, entry.getValue().apply(is));
            }
        }
    }

    public static void install(RepositorySystemSession repositorySystemSession, Path path) throws IOException {
        File file = path.toFile();
        try (ZipFile zipFile = new ZipFile(file)) {
            File localRepoDir = repositorySystemSession.getLocalRepository().getBasedir();
            log.info("Creating checksum files for all files in the local repository: {}", localRepoDir);
            Iterator<File> it = FileUtils.iterateFiles(localRepoDir, null, true);
            while (it.hasNext()) {
                File f = it.next();
                createChecksumFile(f);
            }
            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setIncludeRootFolder(false);
            zipParameters.setCompressionLevel(CompressionLevel.ULTRA);
            zipParameters.setExcludeFileFilter(n -> n.getName().startsWith("maven-metadata-local.xml"));
            zipFile.addFolder(localRepoDir, zipParameters);
            log.debug("Added {} to {}", localRepoDir, file);
        }
    }
}
