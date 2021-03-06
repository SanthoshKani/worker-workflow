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

import com.github.cafdataprocessing.workflow.model.ArgumentDefinition;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hpe.caf.worker.document.model.Document;
import com.hpe.caf.worker.document.testing.DocumentBuilder;
import com.hpe.caf.worker.document.testing.TestServices;
import com.microfocus.darwin.settings.client.ResolvedSetting;
import com.microfocus.darwin.settings.client.SettingsApi;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ArgumentsManagerTest {

    @Test
    public void argumentFromFieldTest() throws Exception {

        final List<ArgumentDefinition> argumentDefinitions = getArgumentDefinitions();

        final SettingsApi settingsApi = mock(SettingsApi.class);

        final Document document = DocumentBuilder.configure().withServices(TestServices.createDefault())
                .withCustomData()
                .documentBuilder()
                .withFields()
                    .addFieldValue("exampleField", "value of example field")
                .documentBuilder()
                .build();

        final ArgumentsManager argumentsManager = new ArgumentsManager(settingsApi, "");
        argumentsManager.addArgumentsToDocument(argumentDefinitions, document);

        final Gson gson = new Gson();
        final Type type = new TypeToken<Map<String, String>>() {}.getType();
        final Map<String, String> arguments = gson.fromJson(
                document.getTask().getResponse().getCustomData().get("CAF_WORKFLOW_SETTINGS"), type);

        assertEquals("value of example field", arguments.get("example"));
        assertEquals("A default value", arguments.get("shouldDefault"));
    }

    @Test
    public void argumentFromTaskSettingTest() throws Exception {

        final List<ArgumentDefinition> argumentDefinitions = getArgumentDefinitions();

        final SettingsApi settingsApi = mock(SettingsApi.class);

        final Document document = DocumentBuilder.configure().withServices(TestServices.createDefault())
                .withCustomData()
                    .add("TASK_SETTING_EXAMPLE", "value of task setting example field")
                .documentBuilder()
                .withFields()
                .documentBuilder()
                .build();

        final ArgumentsManager argumentsManager = new ArgumentsManager(settingsApi, "");
        argumentsManager.addArgumentsToDocument(argumentDefinitions, document);

        final Gson gson = new Gson();
        final Type type = new TypeToken<Map<String, String>>() {}.getType();
        final Map<String, String> arguments = gson.fromJson(
                document.getTask().getResponse().getCustomData().get("CAF_WORKFLOW_SETTINGS"), type);

        assertEquals("value of task setting example field", arguments.get("example"));
    }

    @Test
    public void argumentFromCustomDataTest() throws Exception {

        final List<ArgumentDefinition> argumentDefinitions = getArgumentDefinitions();

        final SettingsApi settingsApi = mock(SettingsApi.class);

        final Document document = DocumentBuilder.configure().withServices(TestServices.createDefault())
                .withCustomData()
                .add("exampleCustomData", "value of from custom data")
                .documentBuilder()
                .withFields()
                .documentBuilder()
                .build();

        final ArgumentsManager argumentsManager = new ArgumentsManager(settingsApi, "");
        argumentsManager.addArgumentsToDocument(argumentDefinitions, document);

        final Gson gson = new Gson();
        final Type type = new TypeToken<Map<String, String>>() {}.getType();
        final Map<String, String> arguments = gson.fromJson(
                document.getTask().getResponse().getCustomData().get("CAF_WORKFLOW_SETTINGS"), type);

        assertEquals("value of from custom data", arguments.get("example"));
    }

    @Test
    public void argumentFromSettingsServiceUsingCustomDataTest() throws Exception {

        final List<ArgumentDefinition> argumentDefinitions = getArgumentDefinitions();

        final SettingsApi settingsApi = mock(SettingsApi.class);

        final ResolvedSetting resolvedSetting = new ResolvedSetting();
        resolvedSetting.setValue("valueFromSettingsService");
        when(settingsApi.getResolvedSetting("exampleSetting", "repository-rId,tenantId-tId-some-suffix"))
                .thenReturn(resolvedSetting);

        final Document document = DocumentBuilder.configure().withServices(TestServices.createDefault())
                .withCustomData()
                .add("repositoryId", "rId")
                .add("tenantId", "tId")
                .documentBuilder()
                .withFields()
                .documentBuilder()
                .build();

        final ArgumentsManager argumentsManager = new ArgumentsManager(settingsApi, "");
        argumentsManager.addArgumentsToDocument(argumentDefinitions, document);

        final Gson gson = new Gson();
        final Type type = new TypeToken<Map<String, String>>() {}.getType();
        final Map<String, String> arguments = gson.fromJson(
                document.getTask().getResponse().getCustomData().get("CAF_WORKFLOW_SETTINGS"), type);

        assertEquals("valueFromSettingsService", arguments.get("example"));
    }

    @Test
    public void argumentFromSettingsServiceUsingFieldTest() throws Exception {

        final List<ArgumentDefinition> argumentDefinitions = getArgumentDefinitions();

        final SettingsApi settingsApi = mock(SettingsApi.class);

        final ResolvedSetting resolvedSetting = new ResolvedSetting();
        resolvedSetting.setValue("valueFromSettingsService");
        when(settingsApi.getResolvedSetting("exampleSetting", "repository-rId,tenantId-tId-some-suffix"))
                .thenReturn(resolvedSetting);

        final Document document = DocumentBuilder.configure().withServices(TestServices.createDefault())
                .withCustomData()
                .add("tenantId", "tId")
                .documentBuilder()
                .withFields()
                .addFieldValue("repositoryId", "rId")
                .documentBuilder()
                .build();

        final ArgumentsManager argumentsManager = new ArgumentsManager(settingsApi, "");
        argumentsManager.addArgumentsToDocument(argumentDefinitions, document);

        final Gson gson = new Gson();
        final Type type = new TypeToken<Map<String, String>>() {}.getType();
        final Map<String, String> arguments = gson.fromJson(
                document.getTask().getResponse().getCustomData().get("CAF_WORKFLOW_SETTINGS"), type);

        assertEquals("valueFromSettingsService", arguments.get("example"));
    }

    private List<ArgumentDefinition> getArgumentDefinitions() {
        final List<ArgumentDefinition> argumentDefinitions = new ArrayList<>();
        ArgumentDefinition argumentDefinition = new ArgumentDefinition();
        argumentDefinition.setName("example");
        argumentDefinition.setSources(new ArrayList<>());

        {
            final ArgumentDefinition.Source fieldSource = new ArgumentDefinition.Source();
            fieldSource.setName("exampleField");
            fieldSource.setType(ArgumentDefinition.SourceType.FIELD);
            argumentDefinition.getSources().add(fieldSource);
        }

        {
            //Apollo product example where custom data gets prefixed with TASK_SETTING_
            final ArgumentDefinition.Source taskCustomDataSource = new ArgumentDefinition.Source();
            taskCustomDataSource.setName("TASK_SETTING_EXAMPLE");
            taskCustomDataSource.setType(ArgumentDefinition.SourceType.CUSTOM_DATA);
            argumentDefinition.getSources().add(taskCustomDataSource);
        }

        {
            final ArgumentDefinition.Source customDataSource = new ArgumentDefinition.Source();
            customDataSource.setName("exampleCustomData");
            customDataSource.setType(ArgumentDefinition.SourceType.CUSTOM_DATA);
            argumentDefinition.getSources().add(customDataSource);
        }

        {
            final ArgumentDefinition.Source settingsServiceSource = new ArgumentDefinition.Source();
            settingsServiceSource.setName("exampleSetting");
            settingsServiceSource.setType(ArgumentDefinition.SourceType.SETTINGS_SERVICE);
            settingsServiceSource
                    .setOptions("repository-%f:repositoryId%,repository-%cd:repositoryId%," +
                            "tenantId-%cd:tenantId%-some-suffix");
            argumentDefinition.getSources().add(settingsServiceSource);
        }

        argumentDefinitions.add(argumentDefinition);

        ArgumentDefinition argumentDefinitionWithDefault = new ArgumentDefinition();
        argumentDefinitionWithDefault.setName("shouldDefault");
        argumentDefinitionWithDefault.setDefaultValue("A default value");

        argumentDefinitions.add(argumentDefinitionWithDefault);

        return argumentDefinitions;
    }
}
