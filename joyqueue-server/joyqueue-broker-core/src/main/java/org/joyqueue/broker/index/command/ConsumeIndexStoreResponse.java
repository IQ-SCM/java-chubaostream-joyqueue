/**
 * Copyright 2019 The JoyQueue Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.joyqueue.broker.index.command;

import org.joyqueue.network.command.CommandType;
import org.joyqueue.network.transport.command.JoyQueuePayload;

import java.util.Map;

/**
 * Created by zhuduohui on 2018/9/7.
 */
public class ConsumeIndexStoreResponse extends JoyQueuePayload {
    private Map<String, Map<Integer, Short>> indexStoreStatus;

    public ConsumeIndexStoreResponse(Map<String, Map<Integer, Short>> indexStoreStatus) {
        this.indexStoreStatus = indexStoreStatus;
    }

    public Map<String, Map<Integer, Short>> getIndexStoreStatus() {
        return indexStoreStatus;
    }

    @Override
    public int type() {
        return CommandType.CONSUME_INDEX_STORE_RESPONSE;
    }
}
