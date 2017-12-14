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

package org.apache.jackrabbit.oak.segment.compaction.report;

import javax.annotation.Nonnull;

public class GCReportBuilder {

    @Nonnull
    private GCReport report = new GCReport();

    public GCReportBuilder result(GCReport.Result result) {
        report.result = result;
        return this;
    }

    public GCReportBuilder started() {
        report.startTimestamp = System.currentTimeMillis();
        return this;
    }

    public GCReportBuilder gcCount(long gcCount) {
        report.gcCount = gcCount;
        return this;
    }

    public GCReport build() {
        GCReport oldReport = report;
        report = new GCReport();
        return oldReport;
    }
}
