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

import com.github.cafdataprocessing.processing.service.client.ApiClient;
import com.github.cafdataprocessing.processing.service.client.ApiException;
import com.github.cafdataprocessing.processing.service.client.api.RepositoryConfigurationApi;
import com.github.cafdataprocessing.processing.service.client.api.TenantConfigurationApi;
import com.github.cafdataprocessing.processing.service.client.model.EffectiveRepositoryConfigValue;
import com.github.cafdataprocessing.processing.service.client.model.EffectiveTenantConfigValue;
import com.github.cafdataprocessing.workflow.cache.WorkflowSettingsCacheKey;
import com.github.cafdataprocessing.workflow.cache.WorkflowSettingsRepositoryCacheKey;
import com.github.cafdataprocessing.workflow.cache.WorkflowSettingsTenantCacheKey;
import static com.github.cafdataprocessing.workflow.transform.TransformerFunctions.LOG;
import com.github.cafdataprocessing.workflow.transform.models.RepoConfigSource;
import com.github.cafdataprocessing.workflow.transform.models.WorkflowSettings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.hpe.caf.worker.document.exceptions.DocumentWorkerTransientException;
import com.hpe.caf.worker.document.model.Document;
import com.sun.jersey.api.client.ClientHandlerException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkflowSettingsRetriever
{
    private final static Logger LOGGER = LoggerFactory.getLogger(WorkflowSettingsRetriever.class);
    private final LoadingCache<WorkflowSettingsCacheKey, String> settingsCache;
    private final ApiClient apiClient;
    private final Gson gson;
    private final RepositoryConfigurationApi repositoryConfigApi;
    private final TenantConfigurationApi tenantConfigApi;

    public WorkflowSettingsRetriever()
    {
        apiClient = new ApiClient();
        settingsCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .recordStats()
            .build(
                new CacheLoader<WorkflowSettingsCacheKey, String>()
            {
                @Override
                public String load(final WorkflowSettingsCacheKey key)
                    throws ApiException, DocumentWorkerTransientException
                {
                    LOGGER.debug("Key not found in cache: " + key);
                    return getSettingsFromServer(key);
                }
            });
        gson = new Gson();
        final String processingServiceBaseUrl = System.getenv("CAF_WORKFLOW_WORKER_PROCESSING_API_URL");
        Objects.requireNonNull(processingServiceBaseUrl);
        apiClient.setBasePath(processingServiceBaseUrl);
        tenantConfigApi = new TenantConfigurationApi(apiClient);
        repositoryConfigApi = new RepositoryConfigurationApi(apiClient);
    }

    public void addCustomWorkflowConfig(final WorkflowSettings requiredConfig, final Document document)
        throws ApiException, DocumentWorkerTransientException
    {
        final String tenantId = document.getCustomData("tenantId");
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(requiredConfig);
        final Map<String, Map<String, String>> settings = new HashMap<>();
        settings.put("task", processTaskConfigs(document, requiredConfig.getTaskSettings()));
        settings.put("repository", processRepositoryConfigs(document, tenantId, requiredConfig.getRepositorySettings()));
        settings.put("tenant", processTenantConfigs(tenantId, requiredConfig.getTenantSettings()));
        document.getField("CAF_WORKFLOW_SETTINGS").set(gson.toJson(settings));
        document.getTask().getResponse().getCustomData().put("CAF_WORKFLOW_SETTINGS", gson.toJson(settings));
    }

    private Map<String, String> processTenantConfigs(final String tenantId, final List<String> configs)
        throws ApiException, DocumentWorkerTransientException
    {
        if (configs == null) {
            return null;
        }
        final Map<String, String> customConfigs = new HashMap<>();
        for (final String config : configs) {
            try {
                customConfigs.put(config,
                                  settingsCache.get(
                                      new WorkflowSettingsTenantCacheKey(tenantId, config)));
            } catch (final ExecutionException ex) {
                if (ex.getCause() instanceof ApiException) {
                    throw (ApiException) ex.getCause();
                } else if (ex.getCause() instanceof DocumentWorkerTransientException) {
                    throw (DocumentWorkerTransientException) ex.getCause();
                } else {
                    throw new RuntimeException(ex);
                }
            }
        }
        return customConfigs;
    }

    private Map<String, String> processRepositoryConfigs(final Document document, final String tenantId,
                                                         final Map<String, RepoConfigSource> configs) throws ApiException,
                                                                                                             DocumentWorkerTransientException
    {
        final Map<String, String> customConfigs = new HashMap<>();
        String repositoryKey = null;
        for (final Map.Entry<String, RepoConfigSource> config : configs.entrySet()) {
            switch (config.getValue().getSource()) {
                case FIELD:
                    repositoryKey = document.getField(
                        config.getValue().getKey()).getValues().stream().findFirst().get().getStringValue();
                    break;
                case CUSTOMDATA:
                    repositoryKey = document.getCustomData(config.getValue().getKey());
                    if (repositoryKey == null) {
                        throw new RuntimeException("Unable to obtain repository id for config " + config.getValue().getKey());
                    }
                    break;
                default:
                    throw new RuntimeException("Invalid source for repository id. Source of "
                        + config.getValue().getSource() + " is not recognised as a valid source.");
            }
            try {
                customConfigs.put(config.getKey(),
                                  settingsCache.get(
                                      new WorkflowSettingsRepositoryCacheKey(tenantId, config.getKey(), repositoryKey)));
            } catch (final ExecutionException ex) {
                if (ex.getCause() instanceof ApiException) {
                    throw (ApiException) ex.getCause();
                } else if (ex.getCause() instanceof DocumentWorkerTransientException) {
                    throw (DocumentWorkerTransientException) ex.getCause();
                } else {
                    throw new RuntimeException(ex);
                }
            }
        }
        return customConfigs;
    }

    private static Map<String, String> processTaskConfigs(final Document document, final List<String> configs)
        throws ApiException, DocumentWorkerTransientException
    {
        final Map<String, String> customConfigs = new HashMap<>();
        for (final String config : configs) {
            final String configValue = document.getCustomData("TASK_SETTING_" + config.toUpperCase(Locale.US));
            customConfigs.put(config, configValue);
        }
        return customConfigs;
    }

    /**
     * Returns the value of the tenant's configuration that was requested.
     *
     * @param tenantId A unique string that identifies the tenant.
     * @param key The unique string used to identify a specific configuration.
     * @return The string representation of the value of the configuration requested.
     * @throws ApiException When an error occurs while trying to retrieve a config's value from the processing service.
     * @throws NullPointerException When the tenantId or key passed to the method is null.
     */
    private String getTenantRepositorySpecificConfigValue(final String tenantId, final String key,
                                                          final String repositoryId) throws ApiException,
                                                                                            DocumentWorkerTransientException
    {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(repositoryId);
        try {
            final EffectiveRepositoryConfigValue effectiveRepoConfigValue
                = repositoryConfigApi.getEffectiveRepositoryConfig(tenantId, repositoryId, key);
            LOG.debug("Retrieved value for repository configuration using key: {}", key);
            LOG.debug("Retrieved value for repository configuration is of type: {}", effectiveRepoConfigValue.getValueType());
            // escape the config value in case it has characters that would cause issues in JavaScript
            return effectiveRepoConfigValue.getValue();
        } catch (final ApiException ex) {
            if (ex.getCode() == 404) {
                LOG.error("Unable to obtain repository configuration from processing service for tenant: {} using key: {} as no "
                    + "configuration could be found match the provided key", tenantId, key);
                throw ex;
            }
            LOG.error("Unable to obtain repository configuration from processing service for tenant: {} using key: {}", tenantId, key);
            throw ex;
        } catch (final ClientHandlerException ex) {
            LOG.error("Unable to obtain repository configuration from processing service for tenant: {} using key: {}", tenantId, key);
            throw new DocumentWorkerTransientException("Unable to contact processing service to retrieve repository configs.");
        }
    }

    /**
     * Returns the value of the tenant's configuration that was requested.
     *
     * @param tenantId A unique string that identifies the tenant.
     * @param key The unique string used to identify a specific configuration.
     * @return The string representation of the value of the configuration requested.
     * @throws ApiException When an error occurs while trying to retrieve a config's value from the processing service.
     * @throws NullPointerException When the tenantId or key passed to the method is null.
     */
    public String getTenantSpecificConfigValue(final String tenantId, final String key)
        throws ApiException, DocumentWorkerTransientException
    {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(key);
        try {
            final EffectiveTenantConfigValue effectiveRepoConfigValue
                = tenantConfigApi.getEffectiveTenantConfig(tenantId, key);
            LOG.debug("Retrieved value for tenant configuration using key: {}", key);
            LOG.debug("Retrieved value for tenant configuration is of type: {}", effectiveRepoConfigValue.getValueType());
            // escape the config value in case it has characters that would cause issues in JavaScript
            return effectiveRepoConfigValue.getValue();
        } catch (final ApiException ex) {
            if (ex.getCode() == 404) {
                LOG.error("Unable to obtain tenant configuration from processing service for tenant: {} using key: {} as no "
                    + "configuration could be found match the provided key", tenantId, key);
                throw ex;
            }
            LOG.error("Unable to obtain tenant configuration from processing service for tenant: {} using key: {}", tenantId, key);
            throw ex;
        } catch (final ClientHandlerException ex) {
            LOG.error("Unable to obtain tenant configuration from processing service for tenant: {} using key: {}", tenantId, key);
            throw new DocumentWorkerTransientException("Unable to contact processing service to retrieve tenant configs.");
        }
    }

    private String getSettingsFromServer(final WorkflowSettingsCacheKey key) throws ApiException, DocumentWorkerTransientException
    {
        if (key instanceof WorkflowSettingsRepositoryCacheKey) {
            final WorkflowSettingsRepositoryCacheKey cacheKey = (WorkflowSettingsRepositoryCacheKey) key;
            if (cacheKey.getSettingKey() == null) {
                throw new RuntimeException("Can't look up value for null setting key.");
            }
            return getTenantRepositorySpecificConfigValue(cacheKey.getTenantId(),
                                                          cacheKey.getSettingKey(),
                                                          cacheKey.getRepositoryId());
        } else if (key instanceof WorkflowSettingsTenantCacheKey) {
            final WorkflowSettingsTenantCacheKey cacheKey = (WorkflowSettingsTenantCacheKey) key;
            if (cacheKey.getSettingKey() == null) {
                throw new RuntimeException("Can't look up value for null setting key.");
            }
            return getTenantSpecificConfigValue(cacheKey.getTenantId(), cacheKey.getSettingKey());
        } else {
            throw new RuntimeException("Workflow settings cache key type not recognised."
                + " Only supposed cache keys are of types WorkflowSettingsRepositoryCacheKey and WorkflowSettingsTenantCacheKey.");
        }
    }
}
