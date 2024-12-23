package org.jboss.pnc.konfluxtooling.logging;

import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;

import org.jboss.pnc.bifrost.upload.BifrostLogUploader;
import org.jboss.pnc.bifrost.upload.LogMetadata;
import org.jboss.pnc.bifrost.upload.TagOption;

import io.quarkus.logging.Log;
import picocli.CommandLine;

@CommandLine.Command(name = "upload-log")
public class UploadLogCommand implements Runnable {

    private static final int DEFAULT_MAX_RETRIES = 4;

    private static final int DEFAULT_DELAY_SECONDS = 60;

    @CommandLine.Option(names = "--file", required = true)
    String logFile;

    @CommandLine.Option(names = "--bifrost-url", required = true)
    String bifrostURL;

    @CommandLine.Option(names = "--max-retries")
    int maxRetries = DEFAULT_MAX_RETRIES;

    @CommandLine.Option(names = "--delay-seconds")
    int delaySeconds = DEFAULT_DELAY_SECONDS;

    @CommandLine.Option(names = "--process-context", required = true)
    String processContext;

    @CommandLine.Option(names = "--process-context-variant", required = true)
    String processContextVariant;

    @CommandLine.Option(names = "--tmp", required = true)
    String tmp;

    @CommandLine.Option(names = "--request-context", required = true)
    String requestContext;

    public void run() {
        try {
            var logFilePath = Path.of(logFile);
            var file = logFilePath.toFile();
            if (!file.exists()) {
                throw new RuntimeException(String.format(
                        "No log file found at %s. Has the build been correctly done?", logFilePath));
            }
            var md5 = getMD5(logFilePath);
            uploadLogsToBifrost(file, md5);
        } catch (Exception e) {
            Log.error("Upload log failed", e);
            throw new RuntimeException(e);
        }
    }

    private String getMD5(Path logFilePath) throws Exception {
        byte[] data = Files.readAllBytes(logFilePath);
        byte[] hash = MessageDigest.getInstance("MD5").digest(data);
        return new BigInteger(1, hash).toString(16);
    }

    private void uploadLogsToBifrost(File logFile, String md5) {
        BifrostLogUploader logUploader = new BifrostLogUploader(URI.create(bifrostURL),
                maxRetries,
                delaySeconds,
                () -> System.getProperty("ACCESS_TOKEN"));

        LogMetadata logMetadata = LogMetadata.builder()
                .tag(TagOption.BUILD_LOG)
                .endTime(OffsetDateTime.now())
                .loggerName("org.jboss.pnc._userlog_.build-agent")
                .processContext(processContext)
                .processContextVariant(processContextVariant)
                .tmp(tmp)
                .requestContext(requestContext)
                .build();

        logUploader.uploadFile(logFile, logMetadata, md5);
    }
}
