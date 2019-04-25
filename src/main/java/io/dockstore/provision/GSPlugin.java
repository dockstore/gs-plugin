/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.provision;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import com.google.common.net.MediaType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.RuntimeMode;

import static java.lang.Math.max;
import static java.lang.Math.min;

// Imports the Google Cloud client library

/**
 * @author wshands
 */
public class GSPlugin extends Plugin {

    public GSPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        // for testing the development mode
        if (RuntimeMode.DEVELOPMENT.equals(wrapper.getRuntimeMode())) {
            System.out.println(StringUtils.upperCase("GSPlugin development mode"));
        }
    }

    @Override
    public void stop() {
        System.out.println("GSPlugin.stop()");
    }

    @Extension
    public static class GSProvision implements ProvisionInterface {

        private static final int MAX_FILE_SIZE_FOR_SINGLE_WRITE = 1_000_000;
        private static final int MAX_BUFFER_SIZE = 100 * 1024 * 1024;
        private static final int MIN_BUFFER_SIZE = 64 * 1024;
        private static final double PERCENT_OF_HEAP_SPACE_TO_USE = .20;

        private static final String DEFAULT_CONTENT_TYPE = MediaType.OCTET_STREAM.toString();
        private Map<String, String> config;

        public void setConfiguration(Map<String, String> map) {
            this.config = map;
        }

        private Storage getGoogleGSClient() {
            return StorageOptions.getDefaultInstance().getService();
        }

        private List<String> getSplitPathList(String path) {
            String trimmedPath = path.replace("gs://", "");
            return Lists.newArrayList(trimmedPath.split("/"));
        }

        protected String getBucketName(String path) {
            List<String> splitPathList = getSplitPathList(path);
            return splitPathList.remove(0);
        }

        // Get the path to the source file minus the scheme and bucket name
        protected String getBlobName(String path) {
            List<String> splitPathList = getSplitPathList(path);
            // Remove the bucket name from the path
            List<String> splitPathListNoBucket = splitPathList.subList(1, splitPathList.size());
            return String.join(File.separator, splitPathListNoBucket);
        }

        /**
         * Determines the size of the buffer to use when reading and writing
         * data to upload or download from GCS storage. We try to use a large
         * buffer to increase the performance, i.e. download or upload speed,
         * to and from cloud storage. The Google cloud storage library
         * performance is not as good when accessing gs:// URLS as when http://
         * URLs are used when the buffer is less than about 50MB in our tests.
         * See issue https://github.com/googleapis/google-cloud-java/issues/3929
         *
         * @return The buffer size to use when uploading or downloading data
         */
        private int getBufferSizeToUse() {
            // Determine how much heap space is available for our buffer
            long heapAvailable = Runtime.getRuntime().freeMemory();
            // We will use a percentage of available heap bytes for the buffer
            long heapBytesToUse = (long)(heapAvailable * PERCENT_OF_HEAP_SPACE_TO_USE);
            // Limit the size of the buffer to a maximum
            int maxBufferSizeToUse =  (int)min(heapBytesToUse, MAX_BUFFER_SIZE);
            // but use a minimum size buffer; we assume the minimum is reasonable
            int bufferSizeToUse =  max(maxBufferSizeToUse, MIN_BUFFER_SIZE);
            return bufferSizeToUse;
        }

        public Set<String> schemesHandled() {
            return new HashSet<>(Lists.newArrayList("gs"));
        }

        public boolean downloadFrom(String sourcePath, Path destination) {
            Storage gsClient = getGoogleGSClient();

            String bucketName = getBucketName(sourcePath);
            String blobName = getBlobName(sourcePath);

            Blob blobMetadata;
            try {
                blobMetadata = gsClient.get(bucketName, blobName, Storage.BlobGetOption.fields(Storage.BlobField.values()));
            } catch (StorageException e) {
                System.err.println(
                        "Could not get metadata for download source file " + sourcePath + " Storage exception:" + e.getMessage());
                return false;
            }

            if (blobMetadata == null) {
                System.err.println("Could not get metadata for download source file " + sourcePath + " Please check the file path.");
                return false;
            }

            long inputSize = blobMetadata.getSize();

            Blob blob;
            try {
                blob = gsClient.get(BlobId.of(bucketName, blobName));
            } catch (StorageException e) {
                System.err.println("Could not get info for dowload source file " + sourcePath + " Storage exception:" + e.getMessage());
                return false;
            }

            // The destination path does not exist when a WDL workflow
            // or tool is run so make sure the directories in the path exist
            try {
                Files.createDirectories(destination.getParent());
            } catch (IOException e) {
                System.err.println(
                        "Could not create download destination file " + destination.toString() + " IO exception:" + e.getMessage());
                return false;
            }

            if (blob.getSize() < MAX_FILE_SIZE_FOR_SINGLE_WRITE) {
                // Blob is small read all its content in one request
                byte[] content;
                try {
                    content = blob.getContent();
                } catch (StorageException e) {
                    System.err.println("Could read download source file " + sourcePath + " Storage exception:" + e.getMessage());
                    return false;
                }

                try (FileOutputStream fileContent = new FileOutputStream(destination.toFile());
                        PrintStream writeTo = new PrintStream(fileContent)) {
                    writeTo.write(content);
                } catch (FileNotFoundException e) {
                    System.err.println("Could not open downoad destination file " + destination.toString() + " File not found exception:" +
                            e.getMessage());
                    return false;
                } catch (IOException e) {
                    System.err.println(
                            "Could not write download destination file " + destination.toString() + " IO exception:" + e.getMessage());
                    return false;
                }

            } else {
                // When Blob size is big or unknown use the blob's channel reader.
                try (OutputStream targetFileOutputStream = Files.newOutputStream(destination);
                            ReadChannel reader = blob.reader()) {

                    WritableByteChannel channel = Channels.newChannel(targetFileOutputStream);
                    int downloadBufferSize = getBufferSizeToUse();
                    ByteBuffer bytes = ByteBuffer.allocate(downloadBufferSize);
                    try {
                        ProgressPrinter printer = new ProgressPrinter();
                        int limit;
                        long runningTotal = 0;
                        while ((limit = reader.read(bytes)) > 0) {
                            try {
                                bytes.flip();
                                channel.write(bytes);
                                bytes.clear();
                                runningTotal = runningTotal + limit;
                                printer.handleProgress(runningTotal, inputSize);
                            } catch (IOException ex) {
                                System.err.println(
                                        "Could not write download destination file " + destination.toString() + " IO Exception " +
                                                ex.getMessage());
                                return false;
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Could not read download source file " + sourcePath + " IO exception:" + e.getMessage());
                        return false;
                    }
                } catch (StorageException e) {
                    System.err.println(
                            "Could not access download destination file " + sourcePath + " StorageException:" + e.getMessage());
                    return false;
                } catch (Exception e) {
                    System.err.println("Could not download file " + sourcePath + " Exception:" + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
            return true;
        }

        public boolean uploadTo(String destPath, Path sourceFile, Optional<String> metadata) {
            long inputSize = sourceFile.toFile().length();

            String contentType;
            try {
                contentType = Files.probeContentType(sourceFile);
            } catch (IOException e) {
                contentType = DEFAULT_CONTENT_TYPE;
                System.err.println(
                        "Could not get content type of upload source file " + sourceFile.toString() + " Using default content type "
                                + DEFAULT_CONTENT_TYPE + " IO exception:" + e.getMessage());
            }

            // Bug on Mac where Files.probeContentType returns null
            if (contentType == null)
                contentType = DEFAULT_CONTENT_TYPE;

            Storage gsClient = getGoogleGSClient();

            String bucketName = getBucketName(destPath);
            String blobName = getBlobName(destPath);

            BlobId blobId = BlobId.of(bucketName, blobName);
            BlobInfo blobInfo;

            if (metadata.isPresent()) {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>() {
                }.getType();

                try {
                    Map<String, String> map = gson.fromJson(metadata.get(), type);
                    blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).setMetadata(map).build();
                } catch (com.google.gson.JsonSyntaxException ex) {
                    System.err.println(
                            "Could not set metadata for upload destination file " + destPath +
                                    " File will not be uploaded. Metadata syntax exception:" + ex.getMessage());
                    return false;
                }
            } else {
                blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();
            }


            if (inputSize > MAX_FILE_SIZE_FOR_SINGLE_WRITE) {
                // When content is not available or large (1MB or more) it is recommended
                // to write it in chunks via the blob's channel writer.
                try (WriteChannel writer = gsClient.writer(blobInfo)) {
                    int uploadBufferSize = getBufferSizeToUse();
                    byte[] buffer = new byte[uploadBufferSize];
                    try (InputStream input = Files.newInputStream(sourceFile)) {
                        ProgressPrinter printer = new ProgressPrinter();
                        int limit;
                        long runningTotal = 0;
                        while ((limit = input.read(buffer)) >= 0) {
                            try {
                                writer.write(ByteBuffer.wrap(buffer, 0, limit));
                                runningTotal = runningTotal + limit;
                                printer.handleProgress(runningTotal, inputSize);
                            } catch (IOException ex) {
                                System.err.println("Could not write upload destination file " + destPath + " IO Exception " +
                                        ex.getMessage());
                                return false;
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Could not read upload source file " + sourceFile.toString() + " to upload. IO exception:" +
                                e.getMessage());
                        return false;
                    }
                } catch (StorageException e) {
                    System.err.println(
                            "Could not access upload destination file " + destPath + " StorageException:" + e.getMessage());
                    return false;
                } catch (Exception e) {
                    System.err.println("Could not upload file " + sourceFile.toString() + " to " + destPath +
                            " Exception:" + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            } else {
                byte[] bytes;
                try {
                    bytes = Files.readAllBytes(sourceFile);
                } catch (IOException e) {
                    System.err.println(
                            "Could not read upload source file " + sourceFile.toString() + " to upload. IO exception:" + e.getMessage());
                    return false;
                }
                // create the blob in one request.
                try {
                    gsClient.create(blobInfo, bytes);
                } catch (StorageException e) {
                    System.err.println(
                            "Could not write upload destination file " + destPath + " Storage exception:" + e.getMessage());
                    return false;
                }
            }
            return true;
        }
    }
}

