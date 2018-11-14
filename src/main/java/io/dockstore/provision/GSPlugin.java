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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Imports the Google Cloud client library
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.RuntimeMode;

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


/*
    static ProgressListener getProgressListener(final long inputSize) {
        return new ProgressListener() {
            ProgressPrinter printer = new ProgressPrinter();
            long runningTotal = 0;
            @Override
            public void progressChanged(ProgressEvent progressEvent) {
                if (progressEvent.getEventType() == ProgressEventType.REQUEST_BYTE_TRANSFER_EVENT) {
                    runningTotal += progressEvent.getBytesTransferred();
                }
                printer.handleProgress(runningTotal, inputSize);
            }
        };
    }
*/

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

            //BlobId blob_id = BlobId.of(bucketName, blobName);
            Blob blob = null;
            try {
                //blob = gsClient.get(blob_id);
                blob = gsClient.get(BlobId.of(bucketName, blobName));
            } catch (StorageException e) {
                System.err.println("gsClient get download exception:" + e.getMessage());
                return false;
            }

            //Blob blob = gsClient.get(BlobId.of(bucketName, trimmedPath));
            // Download file to specified path
            if(blob != null) {
                try {
                    blob.downloadTo(destination);
                } catch (StorageException e) {
                    System.err.println("Blob download exception:" + e.getMessage());
                    throw new RuntimeException("Could not provision input files from Google Cloud Storage", e);
                }
                return true;
            }
            else {
                System.err.println("Download from GCS failed. Could not find GCS bucket or path. "
                        + "Please verify that the GCS bucket and path exist.");
                return false;
            }
        }

        public boolean uploadTo(String destPath, Path sourceFile, Optional<String> metadata) {
            long inputSize = sourceFile.toFile().length();
            Storage gsClient = getGoogleGSClient();

            String trimmedPath = destPath.replace("gs://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);
            String blobName = String.join(File.separator, splitPathList);

            BlobId blobId = BlobId.of(bucketName, blobName);
            // Initialize BlobInfo to a default object
            //BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
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
                    blobInfo = BlobInfo.newBuilder(blobId).setMetadata(map).build();
                } catch (com.google.gson.JsonSyntaxException ex) {
                    System.err.println("Could not load metadata. Metadata syntax exception. Uploading file without metadata:" + ex.getMessage());
                    blobInfo = BlobInfo.newBuilder(blobId).build();
                }
            }
            else {
                blobInfo = BlobInfo.newBuilder(blobId).build();
            }

            //putObjectRequest.setGeneralProgressListener(getProgressListener(inputSize));

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
        }
    }
}

