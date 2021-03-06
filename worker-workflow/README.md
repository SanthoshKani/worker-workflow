# worker-workflow

## Summary

The Workflow Worker is used to load a script (from file) representing a workflow that can be executed against a document to send the document to another worker to have an action performed on it.
The workflow scripts and their settings have to be saved in the [src/main/docker/workflows](../worker-workflow-container/src/main/docker/workflows) folder.
You can find a sample workflow which is in the yaml format in [here](../worker-workflow/src/test/resources/workflow-worker-test/sample-workflow.yaml).

## Implementation

The Workflow Worker behaviour for a task message is as follows;

- Retrieve the workflow referenced by document in task message.
- Resolves customData sources to obtain their values and provide them to the workflow. For example, the customData field source 'projectId' can be resolved by the worker and provided to the workflow.
- Evaluate the document against the workflow script to mark the document with the next suitable action to perform on the document e.g. send to Language Detection Worker, and modify the response options on the document to direct it to the appropriate queue.
- Store the transformed workflow script on the document as a script to be executed by Document Workers during post processing. The intention is that once the next worker has completed its action it will evaluate the document against the workflow again and determine the next action to execute, sending the document to that next worker until all actions on the workflow are completed.

## Configuration

The configuration properties for use with this worker can be seen in the container for the worker, [here](../worker-workflow-container).

## Health Check

The worker health check verifies that the worker can communicate with the configured processing API by calling the health check method on the API.

## Failure Modes

The main places where the worker can fail are;

- Configuration errors: these will manifest on startup. Check the logs for clues, and double check your configuration settings.

## Workflow

The workflow file would have 2 parts called Arguments and Actions. 
Further information on the fields on workflow can be found [here](../worker-workflow-container/src/main/docker/workflows/readme.md)


