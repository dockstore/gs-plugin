# gs-plugin
Dockstore Google Cloud Storage file provisioning plugin

The gs-plugin enables download, upload, and setting metadata on uploaded objects for
Google Cloud Storage (GCS) on the Google Cloud Platform (GCP).

To access GCS from a local machine you will need to install the Google Cloud SDK;
Instructions for installation and other topics can be found here: https://cloud.google.com/sdk/docs/how-to

In order to utilize GCS you must usually authorize Google Cloud SDK tools:
https://cloud.google.com/sdk/docs/authorizing

Once this is done the gs-plugin, which utilizes the Google Cloud SDK tools, can download and upload files
when it detects a path pointing to a GCS location, e.g. gs://bucket/path_to_file

## Usage

Here is an example input JSON file to a tool:
```
$ cat test.gs.json
{
  "input_file": {
        "class": "File",
        "path": "gs://oicr.temp/bamstats_report.zip"
          },
    "output_file": {
        "class": "File",
        "metadata": "eyJvbmUiOiJ3b24iLCJ0d28iOiJ0d28ifQ==",
        "path": "gs://oicr.temp/bamstats_report.zip"
    }
}
```
Note that metadata is Base64 encoded in the JSON and creates metadata tags on the uploaded file.
Also input and output paths do not necessarily exist and are for example only.

Here is an example command line to launch the tool:
```
$ dockstore tool launch --entry  quay.io/briandoconnor/dockstore-tool-md5sum  --json test.gs.json
```




