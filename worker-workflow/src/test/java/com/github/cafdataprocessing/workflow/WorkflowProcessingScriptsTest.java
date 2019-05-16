/*
 * Copyright 2015-2018 Micro Focus or one of its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cafdataprocessing.workflow;

import com.github.cafdataprocessing.workflow.cache.WorkflowSettingsCacheKey;
import com.github.cafdataprocessing.workflow.model.RepoConfigSource;
import com.github.cafdataprocessing.workflow.model.TenandIdConfigSource;
import com.github.cafdataprocessing.workflow.model.WorkflowSettings;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.hpe.caf.api.worker.WorkerException;
import com.hpe.caf.worker.document.DocumentWorkerFieldEncoding;
import com.hpe.caf.worker.document.DocumentWorkerFieldValue;
import com.hpe.caf.worker.document.exceptions.DocumentWorkerTransientException;
import com.hpe.caf.worker.document.model.Document;
import com.hpe.caf.worker.document.model.Script;
import com.hpe.caf.worker.document.testing.DocumentBuilder;
import com.microfocus.darwin.settings.client.ApiException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.script.ScriptException;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import org.junit.Assert;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class WorkflowProcessingScriptsTest
{
    private static final String TEST_SCRIPT = "function(){print('This is a test!')}";
    private static final String TEST_STORAGE_REF = "TestStorageRef1";
    private static final String TEMP_WORKFLOW_SCRIPT = "temp-workflow.js";
    private static final String WORKFLOW_SCRIPT = "workflow.js";
    
    @Mock
    LoadingCache<WorkflowSettingsCacheKey, String> settingsCache;
    
    @Test
    public void addScriptsTest() throws WorkerException, ScriptException, IOException
    {
        final Document doc = getDocument();
        WorkflowProcessingScripts.setScripts(doc, TEST_SCRIPT, TEST_STORAGE_REF);
        Assert.assertTrue(!doc.getTask().getScripts().isEmpty());
        for (final Script script : doc.getTask().getScripts()) {
            switch (script.getName()) {
                case TEMP_WORKFLOW_SCRIPT:
                    Assert.assertTrue(script.isLoaded());
                    Assert.assertTrue(!script.isInstalled());
                    Assert.assertTrue(script.getScript().equals(TEST_SCRIPT));
                    break;
                case WORKFLOW_SCRIPT:
                    Assert.assertTrue(!script.isLoaded());
                    Assert.assertTrue(script.isInstalled());
                    break;
                default:
                    Assert.fail();
                    break;
            }
        }
    }
    
    @Test
    public void retrieveWorkflowSettingsTest() throws WorkerException, ApiException, DocumentWorkerTransientException, ExecutionException
    {
        final Gson gson = new Gson();
        when(settingsCache.get(any(WorkflowSettingsCacheKey.class)))
            .thenReturn("bla bla for repo").thenReturn("false").thenReturn("false");
        final WorkflowSettingsRetriever workflowSettingsRetriever = new WorkflowSettingsRetriever(settingsCache);
        final Document document = getDocumentWithCustomData();
        workflowSettingsRetriever.retrieveWorkflowSettings(getWorkflowSettings(), document);
        final String doc = document.getField("CAF_WORKFLOW_SETTINGS").getStringValues().get(0);
        Map<String, Map<String, String>> fromJson = gson.fromJson(doc, Map.class);
        assertThat(fromJson, hasKey("tenantId"));
        assertThat(fromJson.get("tenantId"), hasEntry("id", "davide"));
        assertThat(fromJson, hasKey("task"));
        assertThat(fromJson, hasKey("repository"));
        assertThat(fromJson.get("repository"), hasEntry("ee.grammarmap", "bla bla for repo"));
        assertThat(fromJson, hasKey("tenant"));
        assertThat(fromJson.get("tenant"), hasEntry("RECORD_ENTITY_VERSIONS", "false"));
        assertThat(fromJson.get("tenant"), hasEntry("RECORD_USAGE", "false"));
    }
    
    private static Document getDocument() throws WorkerException
    {
        final Map<String, List<DocumentWorkerFieldValue>> fields = new HashMap<>();
        final Document document = DocumentBuilder.configure().withFields(fields).build();
        document.setReference("TestDoc");
        return document;
    }
    
    private static Document getDocumentWithCustomData() throws WorkerException
    {
        final Map<String, List<DocumentWorkerFieldValue>> fields = new HashMap<>();
        final DocumentWorkerFieldValue repo = new DocumentWorkerFieldValue();
        repo.data = "555";
        repo.encoding = DocumentWorkerFieldEncoding.utf8;
        fields.put("REPOSITORY_ID", Arrays.asList(repo));
        
        final Document document = DocumentBuilder.configure()
            .withFields(fields)
            .withCustomData()
            .add("tenantId", "davide")
            .add("workflowName", "enrichmentworkflow")
            .add("TASK_SETTING_EE.OPERATIONMODE", "EXTRACT")
            .add("TASK_SETTING_EE.GRAMMARMAP", "{\"pii\": []}")
            .documentBuilder()
            .build();
        document.setReference("TestDoc");
        return document;
    }
    
    private static WorkflowSettings getWorkflowSettings()
    {
        RepoConfigSource repoConfigSource = new RepoConfigSource();
        repoConfigSource.setKey("REPOSITORY_ID");
        repoConfigSource.setSource(RepoConfigSource.RepositoryIdSource.FIELD);
        Map<String, RepoConfigSource> repoMap = new HashMap<>();
        repoMap.put("ee.grammarmap", repoConfigSource);
        
        TenandIdConfigSource tenandIdConfigSource = new TenandIdConfigSource();
        tenandIdConfigSource.setKey("tenantId");
        tenandIdConfigSource.setSource(TenandIdConfigSource.TenantIdSource.CUSTOMDATA);
        Map<String, TenandIdConfigSource> tenantMap = new HashMap<>();
        tenantMap.put("id", tenandIdConfigSource);
        
        WorkflowSettings ws = new WorkflowSettings();
        ws.setTenantId(tenantMap);
        ws.setRepositorySettings(repoMap);
        ws.setTenantSettings(Arrays.asList("RECORD_USAGE", "RECORD_ENTITY_VERSIONS"));
        ws.setTaskSettings(new ArrayList());
        return ws;
    }
}
