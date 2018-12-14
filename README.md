[![Build Status](https://travis-ci.org/dockstore/gs-plugin.svg?branch=gs-travis)](https://travis-ci.org/dockstore/gs-plugin)
[![Coverage Status](https://coveralls.io/repos/github/dockstore/gs-plugin/badge.svg?branch=gs-travis)](https://coveralls.io/github/dockstore/gs-plugin?branch=master)

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




