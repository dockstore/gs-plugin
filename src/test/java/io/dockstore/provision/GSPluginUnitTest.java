package io.dockstore.provision;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;


public class GSPluginUnitTest {



    @InjectMocks GSPlugin.GSProvision GSProvision;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }


//    @Test
//    public void testDownloadFromValidPath() {
//        String targetPath = "gs://topmed_workflow_testing/topmed_aligner/input_files/NWD176325.0005.recab.cram";
//        Assert.assertTrue(GSProvision.downloadFrom(targetPath));
//    }
//
//    @Test
//    public void testDownloadFromBadGSPath() {
//        String targetPath = "gs://fake";
//        Assert.assertFalse(GSProvision.downloadFrom(targetPath));
//    }
//
//    @Test
//    public void testDownloadFromBadPath() {
//        String targetPath = "fake";
//        Assert.assertFalse(GSProvision.downloadFrom(targetPath));
//    }

    @Test
    public void testSchemesHandledGS() {
        Set<String> scheme = new HashSet<>(Collections.singletonList("gs"));
        Assert.assertEquals(scheme, GSProvision.schemesHandled());
    }

    @Test
    public void testSchemesHandledS3() {
        Set<String> scheme = new HashSet<>(Collections.singletonList("s3"));
        Assert.assertNotEquals(scheme, GSProvision.schemesHandled());
    }

    @Test
    public void testSchemesHandledFailedFake() {
        Set<String> scheme = new HashSet<>(Collections.singletonList("fake"));
        Assert.assertNotEquals(scheme, GSProvision.schemesHandled());
    }

    @Test
    public void testGetBucket() {
        String targetPath = "gs://fakeBucket/fakeBlob";
        Assert.assertEquals("fakeBucket",GSProvision.getBucketName(targetPath));
    }

    @Test
    public void testGetBucketLongBlob() {
        String targetPath = "gs://fakeBucket/fakeBlobPart1/fakeBlobPart2";
        Assert.assertEquals("fakeBucket",GSProvision.getBucketName(targetPath));
    }

    @Test
    public void testGetBlob() {
        String targetPath = "gs://fakeBucket/fakeBlob";
        Assert.assertEquals("fakeBlob",GSProvision.getBlobName(targetPath));
    }

    @Test
    public void testGetBlobLong() {
        String targetPath = "gs://fakeBucket/fakeBlobPart1/fakeBlobPart2";
        Assert.assertEquals("fakeBlobPart1/fakeBlobPart2",GSProvision.getBlobName(targetPath));
    }

}