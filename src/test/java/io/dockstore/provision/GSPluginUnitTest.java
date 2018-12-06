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