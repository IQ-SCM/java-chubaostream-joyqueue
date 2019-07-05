/**
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
package com.jd.joyqueue.network.codec;

import com.google.common.collect.Lists;
import com.jd.joyqueue.network.command.AddConsumerRequest;
import com.jd.joyqueue.network.command.JoyQueueCommandType;
import com.jd.joyqueue.network.serializer.Serializer;
import com.jd.joyqueue.network.transport.codec.JoyQueueHeader;
import com.jd.joyqueue.network.transport.codec.PayloadCodec;
import com.jd.joyqueue.network.transport.command.Type;
import io.netty.buffer.ByteBuf;

import java.util.List;


/**
 * AddConsumerRequestCodec
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2018/12/10
 */
public class AddConsumerRequestCodec implements PayloadCodec<JoyQueueHeader, AddConsumerRequest>, Type {

    @Override
    public AddConsumerRequest decode(JoyQueueHeader header, ByteBuf buffer) throws Exception {
        AddConsumerRequest addConsumerRequest = new AddConsumerRequest();

        short topicSize = buffer.readShort();
        List<String> topics = Lists.newArrayListWithCapacity(topicSize);
        for (int i = 0; i < topicSize; i++) {
            topics.add(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        }

        addConsumerRequest.setTopics(topics);
        addConsumerRequest.setApp(Serializer.readString(buffer, Serializer.SHORT_SIZE));
        addConsumerRequest.setSequence(buffer.readLong());
        return addConsumerRequest;
    }

    @Override
    public void encode(AddConsumerRequest payload, ByteBuf buffer) throws Exception {
        buffer.writeShort(payload.getTopics().size());
        for (String topic : payload.getTopics()) {
            Serializer.write(topic, buffer, Serializer.SHORT_SIZE);
        }
        Serializer.write(payload.getApp(), buffer, Serializer.SHORT_SIZE);
        buffer.writeLong(payload.getSequence());
    }

    @Override
    public int type() {
        return JoyQueueCommandType.ADD_CONSUMER_REQUEST.getCode();
    }
}