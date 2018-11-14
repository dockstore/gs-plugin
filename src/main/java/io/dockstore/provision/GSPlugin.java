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
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

/*
        private AmazonS3 getAmazonS3Client() {
            AmazonS3 s3Client = new AmazonS3Client(new ClientConfiguration().withSignerOverride("S3Signer"));
            if (config.containsKey(S3_ENDPOINT)) {
                final String endpoint = config.get(S3_ENDPOINT);
                System.err.println("found custom S3 endpoint, setting to " + endpoint);
                s3Client.setEndpoint(endpoint);
                s3Client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build());
            }
            return s3Client;
        }
*/
        private Storage getGoogleGSClient() {
            Storage gsClient = StorageOptions.getDefaultInstance().getService();
            return gsClient;
        }

        public Set<String> schemesHandled() {
            return new HashSet<>(Lists.newArrayList("gs"));
        }

        public boolean downloadFrom(String sourcePath, Path destination) {
            System.out.println("destination: " + destination.toString());
            System.out.println("source path:" + sourcePath);

            Storage gsClient = getGoogleGSClient();
            String trimmedPath = sourcePath.replace("gs://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            System.out.println("split path list is" + splitPathList.toString());
            String bucketName = splitPathList.remove(0);
            // Get specific file from specified bucket
            System.out.println("split path list after removing bucket" + splitPathList.toString());

            String blobName = String.join(File.separator, splitPathList);

            System.out.println("bucket name:" + bucketName);
            System.out.println("trimmed path:" + trimmedPath);
            System.out.println("blob name:" + blobName);

            BlobId blob_id = BlobId.of(bucketName, blobName);
            System.out.println("blob id is:" + blob_id.toString());

            Blob blob = null;
            try {
                blob = gsClient.get(blob_id);
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
                }
                return true;
            }
            else {
                System.out.println("blob was null !!!!!!!!");
                return false;
            }
        }

/*
        public boolean downloadFrom(String sourcePath, Path destination) {
            AmazonS3 s3Client = getAmazonS3Client();
            TransferManager tx = TransferManagerBuilder.standard().withS3Client(s3Client).build();
            String trimmedPath = sourcePath.replace("s3://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);

            S3Object object = s3Client.getObject(new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList)));
            try {
                GetObjectRequest request = new GetObjectRequest(bucketName, Joiner.on("/").join(splitPathList));
                request.setGeneralProgressListener(getProgressListener(object.getObjectMetadata().getContentLength()));
                Download download = tx.download(request, destination.toFile());
                download.waitForCompletion();
                Transfer.TransferState state = download.getState();
                return state == Transfer.TransferState.Completed;
            } catch (SdkBaseException e) {
                throw new RuntimeException("Could not provision input files from S3", e);
            } catch (InterruptedException e) {
                System.err.println("Upload to S3 failed " + e.getMessage());
                throw new RuntimeException(e);
            } finally {
                tx.shutdownNow(true);
            }
        }
*/


        public boolean uploadTo(String destPath, Path sourceFile, Optional<String> metadata) {
            long inputSize = sourceFile.toFile().length();
            Storage gsClient = getGoogleGSClient();

            String trimmedPath = destPath.replace("gs://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);
            //bId blobId = BlobId.of("bucket", "blob_name");
            //BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
            //Blob blob = storage.create(blobInfo, "Hello, Cloud Storage!".getBytes(UTF_8));

            String blobName = String.join(File.separator, splitPathList);

            BlobId blobId = BlobId.of(bucketName, blobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();

            //PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, Joiner.on("/").join(splitPathList), sourceFile.toFile());
            if (metadata.isPresent()) {
                //ObjectMetadata md = new ObjectMetadata();
                //md.setContentLength(inputSize);

                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>() {
                }.getType();
                try {

                    Map<String, String> map = gson.fromJson(metadata.get(), type);
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        System.out.println("Loading " + entry.getKey() + "->" + entry.getValue());
                        //md.getUserMetadata().put(entry.getKey(), entry.getValue());
                    }
                } catch (com.google.gson.JsonSyntaxException ex) {
                    //md.getUserMetadata().put("encoded_metadata", metadata.get());
                }
                //putObjectRequest.setMetadata(md);
            }
            //putObjectRequest.setGeneralProgressListener(getProgressListener(inputSize));

            //how to get putObjectRequest into blob???????????
            byte[] fileContent = {0};
            try {
                fileContent = Files.readAllBytes(sourceFile);
            } catch (IOException e) {
                System.err.println("File read bytes exception:" + e.getMessage());
            }
            try {
                Blob blob = gsClient.create(blobInfo, fileContent);
            } catch (StorageException e) {
                System.err.println("Blob upload exception:" + e.getMessage());
                return false;
            }
            return true;
        }
/*
        public boolean uploadTo(String destPath, Path sourceFile, Optional<String> metadata) {
            long inputSize = sourceFile.toFile().length();
            AmazonS3 s3Client = getAmazonS3Client();
            TransferManager tx = TransferManagerBuilder.standard().withS3Client(s3Client).build();

            String trimmedPath = destPath.replace("s3://", "");
            List<String> splitPathList = Lists.newArrayList(trimmedPath.split("/"));
            String bucketName = splitPathList.remove(0);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, Joiner.on("/").join(splitPathList), sourceFile.toFile());
            if (metadata.isPresent()) {
                ObjectMetadata md = new ObjectMetadata();
                md.setContentLength(inputSize);

                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, String>>() {
                }.getType();
                try {

                    Map<String, String> map = gson.fromJson(metadata.get(), type);
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        System.out.println("Loading " + entry.getKey() + "->" + entry.getValue());
                        md.getUserMetadata().put(entry.getKey(), entry.getValue());
                    }
                } catch (com.google.gson.JsonSyntaxException ex) {
                    md.getUserMetadata().put("encoded_metadata", metadata.get());
                }
                putObjectRequest.setMetadata(md);
            }

            putObjectRequest.setGeneralProgressListener(getProgressListener(inputSize));
            try {
                Upload upload = tx.upload(putObjectRequest);
                upload.waitForUploadResult();
                Transfer.TransferState state = upload.getState();
                return state == Transfer.TransferState.Completed;
            } catch (InterruptedException e) {
                System.err.println("Upload to S3 failed " + e.getMessage());
                throw new RuntimeException(e);
            } finally {
                tx.shutdownNow(true);
            }
        }

*/
    }
}

