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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.RuntimeMode;

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

        private static final String GS_ENDPOINT = "endpoint";
        private Map<String, String> config;

        public void setConfiguration(Map<String, String> map) {
            this.config = map;
        }

        private Storage getGoogleGSClient() {
            Storage gsClient = StorageOptions.getDefaultInstance().getService();
            return gsClient;
        }

        public Set<String> schemesHandled() {
            return new HashSet<>(Lists.newArrayList("gs"));
        }

        public boolean downloadFrom(String sourcePath, Path destination) {
            Storage gsClient = getGoogleGSClient();
            String trimmedPath = sourcePath.replace("gs://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);
            String blobName = String.join(File.separator, splitPathList);

            Blob blobMetadata = gsClient.get(bucketName, blobName, Storage.BlobGetOption.fields(Storage.BlobField.values()));
            long inputSize = blobMetadata.getSize();

            Blob blob = null;
            try {
                blob = gsClient.get(BlobId.of(bucketName, blobName));
            } catch (StorageException e) {
                System.err.println("gsClient get download exception:" + e.getMessage());
                return false;
            }

/*
            if (blob != null) {

                try {
                    blob.downloadTo(destination);
                } catch (StorageException e) {
                    System.err.println("Blob download exception:" + e.getMessage());
                    throw new RuntimeException("Could not provision input files from Google Cloud Storage", e);
                }
                return true;
            } else {
                System.err.println("Download from GCS failed. Could not find GCS bucket or path. "
                        + "Please verify that the GCS bucket and path exist.");
                return false;
            }
*/
            // The destination path does not exist when a WDL workflow
            // or tool is run so make sure the directories in the path exist
            try {
                Files.createDirectories(destination.getParent());
            }
            catch (IOException e) {
                System.err.println("Could not create destination path. IO exception:" + e.getMessage());
                return false;
            }

            ProgressPrinter printer = new ProgressPrinter();

            FileOutputStream fileContent = null;
            try {
                fileContent = new FileOutputStream((destination.toFile()));
            } catch (FileNotFoundException e) {
                System.err.println("File not found exception:" + e.getMessage());
                return false;
            }
            PrintStream writeTo = new PrintStream(fileContent);

            if (blob.getSize() < 1_000_000) {
                // Blob is small read all its content in one request
                byte[] content = blob.getContent();
                try {
                    writeTo.write(content);
                } catch (IOException e) {
                    System.err.println("Could not write download file. IO exception:" + e.getMessage());
                    return false;
                }

            } else {
                // When Blob size is big or unknown use the blob's channel reader.
                try (ReadChannel reader = blob.reader()) {
                    WritableByteChannel channel = Channels.newChannel(writeTo);
                    ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);
                    try {
                        int limit = 0;
                        long runningTotal = 0;
                        while ((limit = reader.read(bytes)) > 0) {
                            bytes.flip();
                            channel.write(bytes);
                            bytes.clear();
                            runningTotal = runningTotal + limit;
                            printer.handleProgress(runningTotal, inputSize);
                        }
                    } catch (IOException e) {
                        System.err.println("Could not download file. IO exception:" + e.getMessage());
                        return false;
                    }

                }
            }

            writeTo.close();
            return true;
        }

        public boolean uploadTo(String destPath, Path sourceFile, Optional<String> metadata) {
            long inputSize = sourceFile.toFile().length();

            String contentType = "application/octet-stream";
            try {
                contentType = Files.probeContentType(sourceFile);
            } catch (IOException e) {
                System.err.println("Could not get content type of file. Using default content type. IO exception:" + e.getMessage());
            }

            // Bug on Mac where Files.probeContentType returns null
            if (contentType == null)
                contentType = "application/octet-stream";

            Storage gsClient = getGoogleGSClient();

            String trimmedPath = destPath.replace("gs://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);
            String blobName = String.join(File.separator, splitPathList);

            BlobId blobId = BlobId.of(bucketName, blobName);
            BlobInfo blobInfo = null;


            // To test metadata uncomment the lines below
            //Map<String,String> myMap = new HashMap<String, String>();
            //myMap.put("goat", "bleat");
            //myMap.put("cow", "moo");
            //metadata = Optional.of(myMap.toString());

            if (metadata.isPresent()) {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>() {
                }.getType();

                try {
                    Map<String, String> map = gson.fromJson(metadata.get(), type);
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        System.out.println("Loading " + entry.getKey() + "->" + entry.getValue());
                    }
                    blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).setMetadata(map).build();
                } catch (com.google.gson.JsonSyntaxException ex) {
                    System.err.println(
                            "Could not load metadata. Metadata syntax exception. Uploading file without metadata:" + ex.getMessage());
                    blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();
                }
            } else {
                blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();
            }

/*
            byte[] fileContent = null;
            try {
                fileContent = Files.readAllBytes(sourceFile);
            } catch (IOException e) {
                System.err.println("Could not read all bytes from file. File read bytes exception:" + e.getMessage());
                return false;
            }

            //File sourceFileToUpload = new File(sourceFile.toString());
            //InputStream fileContent;
            //try {
            //    fileContent = new FileInputStream(sourceFileToUpload);
            //} catch (FileNotFoundException e) {
            //    System.err.println("File not found exception:" + e.getMessage());
            //    return false;
            //}

            try {
                Blob blob = gsClient.create(blobInfo, fileContent);
            } catch (StorageException e) {
                System.err.println("Could not upload file. GCS storage upload exception:" + e.getMessage());
                return false;
            }
            return true;
*/


            ProgressPrinter printer = new ProgressPrinter();

            if (inputSize > 1_000_000) {
                // When content is not available or large (1MB or more) it is recommended
                // to write it in chunks via the blob's channel writer.
                try {
                    WriteChannel writer = gsClient.writer(blobInfo);
                    byte[] buffer = new byte[1024];
                    try (InputStream input = Files.newInputStream(sourceFile)) {
                        int limit;
                        long runningTotal = 0;
                        while ((limit = input.read(buffer)) >= 0) {
                            try {
                                writer.write(ByteBuffer.wrap(buffer, 0, limit));
                                runningTotal = runningTotal + limit;
                                printer.handleProgress(runningTotal, inputSize);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                return false;
                            }
                        }
                    }

                } catch (IOException e) {
                    System.err.println("Could not upload file. IO exception:" + e.getMessage());
                    return false;
                }
            } else {
                byte[] bytes = null;
                try {
                    bytes = Files.readAllBytes(sourceFile);
                } catch (IOException e) {
                    System.err.println("Could not read file to upload. IO exception:" + e.getMessage());
                    return false;
                }
                // create the blob in one request.
                gsClient.create(blobInfo, bytes);
            }
            return true;
        }
    }
}

