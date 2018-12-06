# gs-plugin
Dockstore Google Cloud Storage file provisioning plugin
## Usage

The gs plugin is capable of download, upload, and can set metadata on uploaded objects.

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

$ dockstore tool launch --entry  quay.io/briandoconnor/dockstore-tool-md5sum  --json test.gs.json





Note that metadata is Base64 encoded in the JSON and creates metadata tags on the uploaded file.
Also input and output paths do not necessarily exist and are for example only.

## Configuration

This plugin gets configuration information from the following structure in ~/.dockstore/config

```
[dockstore-file-gs-plugin]
endpoint = <endpoint>
```
