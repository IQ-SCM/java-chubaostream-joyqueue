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
package org.joyqueue.broker.kafka.network.helper;

import io.netty.buffer.ByteBuf;
import org.joyqueue.broker.kafka.KafkaCommandType;

/**
 * KafkaProtocolHelper
 *
 * author: gaohaoxiang
 * date: 2018/11/13
 */
public class KafkaProtocolHelper {

    public static boolean isSupport(ByteBuf buffer) {
        if (buffer.readableBytes() < 12) {
            return false;
        }
        int size = buffer.readInt();
        short type = buffer.readShort();
        short version = buffer.readShort();
//        if (size > 0 && (type >= 0 && type < 255) && version <= 3) {
        if (size > 0
                && version >= 0
                && KafkaCommandType.contains(type)) {
            return true;
        }
        return false;
    }
}