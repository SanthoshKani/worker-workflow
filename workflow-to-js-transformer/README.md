# workflow-to-js-transformer

## Summary

This Java library offers functionality to retrieve a specified workflow (via an external processing API) and transform the workflow including its actions to a JavaScript representation that can be executed against Document Worker documents, adding fields to indicate the actions that have executed, are executing, updating the queue that the document should be sent to and passing any post processing script that is set to execute.

## Action Types Supported

The types of action supported by the JavaScript workflow are detailed in this section.

### Chained Action Type

This action is used to send a document to a document worker with configured custom data values for that worker. The action will set the response options on the task of the document so that the task is forwarded to the worker specified by the action. Details of the properties that can be set in the settings of this action are listed below.

#### Usage

Actions of this type should have their type ID set to the action type with internal name 'ChainedActionType'.

#### Settings

##### customData

Used to send additional worker-specific data to the worker. This is optional. This should be a map of keys to raw values/objects that are understood by the worker the document is to be sent to.


###### Sources support

Properties under customData may pull their values from another source beyond a raw value passed.

*inlineJson*

A source of inlineJson may be specified to indicate that a value for a property has been passed as a JSON structure that should be
passed as a string representation in customData. When source is 'inlineJson' a 'data' property should also be set containing the
JSON object that should be set as the string value.

```
"customData": {
     "myProperty" : {
        "source" : "inlineJson",
        "data": {
            "extractMetadata": true,
            "extractContent": true
        }
     }
 }
```

Which would be output as;

```
"customData": {
     "myProperty" : '{"extractMetadata":true,"extractContent":true}'
```

*projectId*

A source of projectId may be specified to indicate that a value for a property should come from a `projectId` parameter that will
 be available during workflow transformation.

```
"customData": {
     "myProperty" : { "source" : "projectId"}
 }
```

Which given a projectId of "myProjectId" would be output after transformation as;

```
"customData": {
    "myProperty": 'myProjectId'
}
```

*taskSettings*

A source of taskSettings maybe specified to indicate that a the key specified can be used to look up the correct value on the input message's customdata. If the key is accompanied by a defaultValue then if the value is not present the default value will be used.

```
"customData": {
  "OPERATION_MODE": {
    "source": {
      "taskSettings": {
        "key": "ee.operationmode",
	"defaultValue": "DETECT"
        }
    }
  }
}
```

*repositorySettings*

A source of repositorySettings can be used to indicate that the worker should call out to get the effective repository configuration values for the key provided. 
Repository settings also specify where the repository id can be obtained on the document to make the request. The possible supported source values for the repository id are `CUSTOMDATA` or `FIELD`, the key then indicates the field name to check.

```
"customData": {
  "OPERATION_MODE": {
    "source": {
      "repositorySettings": {
	"key": "ee.grammarmap",
	"repositoryId": {
	  "source": "FIELD",
	  "key": "REPOSITORY_ID"
	}
      }
    }
  }
}
```

*sources*

Multiple source can also be specified for a customdata element. This then allows for per job customization of the settings by having one source take precedence over another when supplied.
For instance, if supplied with a source of taskSettings and a source of repositorySettings for the same customdata element the taskSettings will take precedence over the repositorySetting if it is supplied, otherwise the repositorySettings will take effect.  

```
"customData": {
  "ee.grammarMap": {
    "sources": {  
      "taskSettings": {
        "key": "ee.grammarmap"
      },
      "repositorySettings": {
	"key": "ee.grammarmap",
	"repositoryId": {
	  "source": "field",
	  "key": "REPOSITORY_ID"
	}
      }
    }
  }
}
```


##### queueName

The name of a queue to send the document to. Can be used to specify the queue to send the document to for this action. This is an optional property. If it is not specified then an environment variable made up of the specified `workerName` plus '.taskqueue' is checked for a value to use. If that is not set then a default queue name of `workerName` plus 'Input' is set for the action.

##### workerName

The name of the worker the document will be sent to. Some configuration details, such as the default queue names, will be looked up based on this name.

##### scripts

Customization scripts may be included on an action and will be loaded when the document is sent to another worker. Each script should have a name and the actual script to execute which may be specified using one of the following properties;
* script - an inline script
* storageRef - a storage reference that can be used to retrieve a script from storage
* url - a URL to a script

**Example**

```
"scripts": [
      {
        "name": "countSuspectedPii.js",
        "script": "// Count the suspected pii entities\nfunction onAfterProcessDocument(e) {\n  var piiEntityCount = e.document.getField(\"SUSPECTED_PII\").getValues().size();\n  e.document.getField(\"SUSPECTED_PII_ENTITY_COUNT\").set(piiEntityCount);\n}\n"
      }
    ]
```

The above script would be set on the task sent to a worker.

### FieldMapping Action Type

This action is used to rename the fields of a document, according to a configurable mapping of field names.

#### Usage

Actions of this type should have their type ID set to the action type with internal name 'FieldMappingActionType'.

#### Settings

##### mappings

Defines the mapping of field names that will be applied by the action. Each entry in the map specifies the current name of a field as its key and the desired new name for the field as its value. Swapping of field names is supported - in other words, the new name for a field may be the same as the current name of another field.

###### Examples

A simple renaming of two fields
```
{
  "mappings": 
   {
      "abc": "def",
      "pqr": "xyz"
   }
}
```

Renaming of three fields, two mapping to the same new field name
```
{
  "mappings":
  {
    "DRETITLE": "TITLE",
    "EXTENSION": "FILE_EXTENSION",
    "IMPORTMAGICEXTENSION": "FILE_EXTENSION"
  }
}
```

Swapping the names of fields
```
{
  "mappings": 
   {
      "abc": "def",
      "def": "pqr",
      "pqr": "xyz",
      "xyz": "abc"
   }
}
```

## Workflow Script Error Handling

The generated workflow script assumes that any failures are recorded on the document outside of the script execution. If an
action was marked for execution on a document and then failed it is the job of the code that failed to ensure that an
appropriate failure is added to the document. The script will mark such action as completed so that it can determine the next
action to try and execute on the document.
