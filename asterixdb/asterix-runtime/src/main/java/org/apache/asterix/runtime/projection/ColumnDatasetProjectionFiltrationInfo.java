/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.runtime.projection;

import static org.apache.asterix.om.utils.ProjectionFiltrationTypeUtil.ALL_FIELDS_TYPE;
import static org.apache.asterix.om.utils.ProjectionFiltrationTypeUtil.EMPTY_TYPE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.asterix.om.types.ARecordType;
import org.apache.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import org.apache.hyracks.algebricks.core.algebra.prettyprint.AlgebricksStringBuilderWriter;

import com.fasterxml.jackson.core.JsonGenerator;

public class ColumnDatasetProjectionFiltrationInfo extends ExternalDatasetProjectionInfo {
    private final ARecordType metaProjectedType;
    private final ILogicalExpression filterExpression;
    private final Map<ILogicalExpression, ARecordType> filterPaths;
    private final ILogicalExpression rangeFilterExpression;

    public ColumnDatasetProjectionFiltrationInfo(ARecordType recordRequestedType, ARecordType metaProjectedType,
            Map<String, FunctionCallInformation> sourceInformationMap, Map<ILogicalExpression, ARecordType> filterPaths,
            ILogicalExpression filterExpression, ILogicalExpression rangeFilterExpression) {
        super(recordRequestedType, sourceInformationMap);
        this.metaProjectedType = metaProjectedType;

        this.filterExpression = filterExpression;
        this.rangeFilterExpression = rangeFilterExpression;
        this.filterPaths = filterPaths;
    }

    private ColumnDatasetProjectionFiltrationInfo(ColumnDatasetProjectionFiltrationInfo other) {
        super(other.projectedType, other.functionCallInfoMap);
        metaProjectedType = other.metaProjectedType;

        filterExpression = other.filterExpression;
        filterPaths = new HashMap<>(other.filterPaths);

        rangeFilterExpression = other.rangeFilterExpression;
    }

    @Override
    public ColumnDatasetProjectionFiltrationInfo createCopy() {
        return new ColumnDatasetProjectionFiltrationInfo(this);
    }

    @Override
    public void print(AlgebricksStringBuilderWriter writer) {
        StringBuilder builder = new StringBuilder();
        if (projectedType != ALL_FIELDS_TYPE) {
            writer.append(" project (");
            if (projectedType == EMPTY_TYPE) {
                writer.append(projectedType.getTypeName());
            } else {
                writer.append(getOnelinerSchema(projectedType, builder));
            }
            writer.append(')');
        }

        if (metaProjectedType != null && metaProjectedType != ALL_FIELDS_TYPE) {
            writer.append(" project-meta (");
            writer.append(getOnelinerSchema(metaProjectedType, builder));
            writer.append(')');
        }

        if (filterExpression != null) {
            writer.append(" filter on: ");
            writer.append(filterExpression.toString());
        }

        if (rangeFilterExpression != null) {
            writer.append(" range-filter on: ");
            writer.append(rangeFilterExpression.toString());
        }
    }

    @Override
    public void print(JsonGenerator generator) throws IOException {
        if (projectedType == ALL_FIELDS_TYPE) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        if (projectedType == EMPTY_TYPE) {
            generator.writeStringField("project", projectedType.getTypeName());
        } else {
            generator.writeStringField("project", getOnelinerSchema(projectedType, builder));
        }

        if (metaProjectedType != null && metaProjectedType != ALL_FIELDS_TYPE) {
            generator.writeStringField("project-meta", getOnelinerSchema(projectedType, builder));
        }

        if (filterExpression != null) {
            generator.writeStringField("filter-on", filterExpression.toString());
        }

        if (rangeFilterExpression != null) {
            generator.writeStringField("range-filter-on", rangeFilterExpression.toString());
        }
    }

    public ARecordType getMetaProjectedType() {
        return metaProjectedType;
    }

    public ILogicalExpression getFilterExpression() {
        return filterExpression;
    }

    public Map<ILogicalExpression, ARecordType> getFilterPaths() {
        return filterPaths;
    }

    public ILogicalExpression getRangeFilterExpression() {
        return rangeFilterExpression;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ColumnDatasetProjectionFiltrationInfo otherInfo = (ColumnDatasetProjectionFiltrationInfo) o;
        return projectedType.deepEqual(otherInfo.projectedType)
                && Objects.equals(metaProjectedType, otherInfo.metaProjectedType)
                && Objects.equals(functionCallInfoMap, otherInfo.functionCallInfoMap)
                && Objects.equals(filterExpression, otherInfo.filterExpression)
                && Objects.equals(filterPaths, otherInfo.filterPaths)
                && Objects.equals(rangeFilterExpression, otherInfo.rangeFilterExpression);
    }

}
