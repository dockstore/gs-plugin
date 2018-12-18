[![Build Status](https://travis-ci.org/dockstore/gs-plugin.svg?branch=master)](https://travis-ci.org/dockstore/gs-plugin)
[![Coverage Status](https://coveralls.io/repos/github/dockstore/gs-plugin/badge.svg?branch=master)](https://coveralls.io/github/dockstore/gs-plugin?branch=master)

# gs-plugin
Dockstore Google Cloud Storage file provisioning plugin

The gs-plugin enables download, upload, and setting metadata on uploaded objects for
Google Cloud Storage (GCS) on the Google Cloud Platform (GCP) through the Google Cloud Storage Client library 

To allow gs-plugin to run the Google Cloud Storage Client library you must setup authentication;
follow the instructions for setting up authentication as described in 
https://cloud.google.com/storage/docs/reference/libraries. 

Once authentication is setup the gs-plugin can download and upload files
when it detects a path pointing to a GCS location, e.g. gs://bucket/path_to_file

Find out more about metadata here: 
https://cloud.google.com/storage/docs/gsutil/addlhelp/WorkingWithObjectMetadata#custom-metadata

### Note
The above instructions describe setting up a service account for authentication; 
however the gs-plugin can also obtain authentication through a user account
as described here https://cloud.google.com/sdk/docs/authorizing#types_of_accounts. 

To optionally setup user account authentication you can install the Google Cloud SDK and use
the gcloud utility from the command line. Instructions for SDK installation and other topics 
can be found here: https://cloud.google.com/sdk/docs/how-to


## Usage

Here is an example input JSON file to a tool:
```
$ cat test.gs.json
{
  "input_file": {
        "class": "File",
        "path": "gs://genomics-public-data/references/GRCh38/chr1.fa.gz"
          },
    "output_file": {
        "class": "File",
        "metadata": "eyJvbmUiOiJ3b24iLCJ0d28iOiJ0d28ifQ==",
        "path": "gs://my_bucket/chr1_md5sum.zip"
    }
}
```
Note that metadata is Base64 encoded JSON and creates metadata tags on the uploaded file.
E.g to encode metadata: 
```
$ echo -n '{"one":"won","two":"two"}' | base64
$ eyJvbmUiOiJ3b24iLCJ0d28iOiJ0d28ifQ==
```
Also input and output paths do not necessarily exist and are for example only.

Here is an example command line to launch the tool:
```
$ dockstore tool launch --entry  quay.io/briandoconnor/dockstore-tool-md5sum  --json test.gs.json
```

## Releases

This section describes creating a release of the Google Cloud Storage file provisioning plugin.

### Prerequisites

[Install](https://datasift.github.io/gitflow/TheHubFlowTools.html) Hubflow. After it is installed, run `git hubflow init` in the 
root of your copy of the repo.

### Create the Release

We will be creating a 0.0.4 release as an example.

1. `git hf release start <version number>` -- Creates a new branch based off develop. With `0.0.4` as the version number, the new
branch will be release/0.0.4.
2. `mvn release:prepare` -- Prompts you to:
    1. `What is the release version for "gs-plugin"?` -- Enter the same version from step 1, e.g., `0.0.4`
    2. `What is SCM release tag or label for "gs-plugin"` -- Same as previous question, e.g., `0.0.4`
    3. `What is the new development version for "gs-plugin"?` -- This will default to your previous answer + 1. You will
    probably want to accept the default, which this example would be `0.0.5-SNAPSHOT`.

This will:

1. Do a commit to the pom.xml setting the version to 0.0.4 in the release/0.0.4 branch
2. Tag the head of the release/0.0.4 branch with a `0.0.4` Git tag.
3. Do a maven build
4. Do another commit setting the version in the pom.xml to 0.0.5-SNAPSHOT

Then do `git hf release finish 0.0.4`, which will
* Merge release/0.0.4 into develop
* Merge develop into master
* Delete the release/0.0.4 branch, locally and in the origin
* Push the develop and master branches, as well as the `0.0.4` tag.

#### Create a GitHub release

1. In your browser, go to https://github.com/dockstore/gs-plugin/releases
2. You will see `0.0.4` listed, but it is *not* a GitHub release, it is a only tag. All GitHub releases have Git tags, but not all Git tags
are  GitHub releases, even though the GitHub UI Releases tab doesn't clearly make that distinction. See 
 [this issue](https://github.com/bcit-ci/CodeIgniter/issues/3421).
3. Create a GitHub 0.0.4 release
    1. Click `Draft (or Create) a new release`.
    2. Specify the tag, `0.0.4`
    3. Attach the zip file from your local target directory, which will have the version number in it, e.g., 
    gs-plugin-0.0.4.zip, to the binaries section of the page.
    4. Enter a title and a description.
    5. Click `Publish Release`

