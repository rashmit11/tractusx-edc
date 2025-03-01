/********************************************************************************
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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
 ********************************************************************************/

package org.eclipse.tractusx.edc.tests.runtimes;

import org.eclipse.edc.junit.extensions.RuntimeExtension;

import java.util.Map;

public interface Runtimes {

    static RuntimeExtension memoryRuntime(String runtimeName, String bpn, Map<String, String> properties) {
        return new ParticipantRuntimeExtension(":edc-tests:runtime:runtime-memory", runtimeName, bpn, properties);
    }

    static RuntimeExtension pgRuntime(String runtimeName, String bpn, Map<String, String> properties) {
        return new PgRuntimeExtension(":edc-tests:runtime:runtime-postgresql", runtimeName, bpn, properties);
    }
}
