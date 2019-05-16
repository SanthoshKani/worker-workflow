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
package com.github.cafdataprocessing.workflow.model;

public class TenandIdConfigSource
{
    private TenantIdSource source;
    private String key;

    /**
     * @return the source
     */
    public TenantIdSource getSource()
    {
        return source;
    }

    /**
     * @param source the source to set
     */
    public void setSource(final TenantIdSource source)
    {
        this.source = source;
    }

    /**
     * @return the key
     */
    public String getKey()
    {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKey(final String key)
    {
        this.key = key;
    }

    public enum TenantIdSource
    {
        FIELD, CUSTOMDATA
    }
}
