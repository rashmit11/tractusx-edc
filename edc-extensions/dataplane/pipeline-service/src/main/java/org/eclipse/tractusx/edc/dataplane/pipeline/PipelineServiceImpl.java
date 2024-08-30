/*
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.tractusx.edc.dataplane.pipeline;

import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSourceFactory;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toSet;

/**
 * Default pipeline service implementation.
 */
public class PipelineServiceImpl implements PipelineService {
    private final List<DataSourceFactory> sourceFactories = new ArrayList<>();
    private final List<DataSinkFactory> sinkFactories = new ArrayList<>();
    private final Map<String, DataSource> sources = new ConcurrentHashMap<>();
    private final Monitor monitor;

    public PipelineServiceImpl(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean canHandle(DataFlowStartMessage request) {
        return getSourceFactory(request) != null && getSinkFactory(request) != null;
    }

    @Override
    public Result<Boolean> validate(DataFlowStartMessage request) {
        var sourceFactory = getSourceFactory(request);
        if (sourceFactory == null) {
            // NB: do not include the source type as that can possibly leak internal information
            return Result.failure("Data source not supported for: " + request.getId());
        }

        var sourceValidation = sourceFactory.validateRequest(request);
        if (sourceValidation.failed()) {
            return Result.failure(sourceValidation.getFailureMessages());
        }

        var sinkFactory = getSinkFactory(request);
        if (sinkFactory == null) {
            // NB: do not include the target type as that can possibly leak internal information
            return Result.failure("Data sink not supported for: " + request.getId());
        }

        var sinkValidation = sinkFactory.validateRequest(request);
        if (sinkValidation.failed()) {
            return Result.failure(sinkValidation.getFailureMessages());
        }

        return Result.success(true);
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataFlowStartMessage request) {
        var sinkFactory = getSinkFactory(request);
        if (sinkFactory == null) {
            return noSinkFactory(request);
        }

        var sink = sinkFactory.createSink(request);

        return transfer(request, sink);
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataFlowStartMessage request, DataSink sink) {
        var sourceFactory = getSourceFactory(request);
        if (sourceFactory == null) {
            return noSourceFactory(request);
        }

        var source = sourceFactory.createSource(request);
        sources.put(request.getProcessId(), source);
        monitor.debug(() -> format("Transferring from %s to %s.", request.getSourceDataAddress().getType(), request.getDestinationDataAddress().getType()));
        return sink.transfer(source)
                .thenApply(result -> {
                    terminate(request.getProcessId());
                    return result;
                });
    }

    @Override
    public StreamResult<Void> terminate(DataFlow dataFlow) {
        return terminate(dataFlow.getId());
    }

    @Override
    public void registerFactory(DataSourceFactory factory) {
        sourceFactories.add(factory);
    }

    @Override
    public void registerFactory(DataSinkFactory factory) {
        sinkFactories.add(factory);
    }

    @Override
    public Set<String> supportedSourceTypes() {
        return sourceFactories.stream().map(DataSourceFactory::supportedType).collect(toSet());
    }

    @Override
    public Set<String> supportedSinkTypes() {
        return sinkFactories.stream().map(DataSinkFactory::supportedType).collect(toSet());
    }

    private StreamResult<Void> terminate(String dataFlowId) {
        var source = sources.remove(dataFlowId);
        if (source == null) {
            return StreamResult.notFound();
        } else {
            try {
                source.close();
                return StreamResult.success();
            } catch (Exception e) {
                return StreamResult.error("Cannot terminate DataFlow %s: %s".formatted(dataFlowId, e.getMessage()));
            }
        }
    }

    @Nullable
    private DataSourceFactory getSourceFactory(DataFlowStartMessage request) {
        return sourceFactories.stream()
                .filter(s -> Objects.equals(s.supportedType(), request.getSourceDataAddress().getType()))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private DataSinkFactory getSinkFactory(DataFlowStartMessage request) {
        return sinkFactories.stream()
                .filter(s -> Objects.equals(s.supportedType(), request.getDestinationDataAddress().getType()))
                .findFirst()
                .orElse(null);
    }

    @NotNull
    private CompletableFuture<StreamResult<Object>> noSourceFactory(DataFlowStartMessage request) {
        return completedFuture(StreamResult.error("Unknown data source type: " + request.getSourceDataAddress().getType()));
    }

    @NotNull
    private CompletableFuture<StreamResult<Object>> noSinkFactory(DataFlowStartMessage request) {
        return completedFuture(StreamResult.error("Unknown data sink type: " + request.getDestinationDataAddress().getType()));
    }


}
