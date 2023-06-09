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
package org.joyqueue.nsr.network.codec;

import org.joyqueue.network.codec.GetTopicsCodec;
import org.joyqueue.network.command.GetTopics;
import org.joyqueue.network.transport.command.Types;
import org.joyqueue.nsr.network.NsrPayloadCodec;
import org.joyqueue.nsr.network.command.NsrCommandType;

/**
 * @author wylixiaobin
 * Date: 2019/1/27
 */
public class NsrGetTopicsCodec extends GetTopicsCodec implements NsrPayloadCodec<GetTopics>, Types {

    @Override
    public int[] types() {
        return new int[]{NsrCommandType.GET_TOPICS,NsrCommandType.MQTT_GET_TOPICS};
    }
}
