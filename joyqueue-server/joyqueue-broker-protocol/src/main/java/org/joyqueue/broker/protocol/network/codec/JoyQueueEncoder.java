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
package org.joyqueue.broker.protocol.network.codec;

import io.netty.buffer.ByteBuf;
import org.joyqueue.network.transport.codec.Codec;
import org.joyqueue.network.transport.codec.DefaultEncoder;
import org.joyqueue.network.transport.codec.JoyQueueHeader;
import org.joyqueue.network.transport.codec.PayloadCodecFactory;
import org.joyqueue.network.transport.command.Command;
import org.joyqueue.network.transport.command.JoyQueuePayload;
import org.joyqueue.network.transport.exception.TransportException;

/**
 * JoyQueueEncoder
 *
 * author: gaohaoxiang
 * date: 2019/6/27
 */
public class JoyQueueEncoder extends DefaultEncoder {

    public JoyQueueEncoder(Codec headerCodec, PayloadCodecFactory payloadCodecFactory) {
        super(headerCodec, payloadCodecFactory);
    }

    @Override
    public void encode(Object obj, ByteBuf buffer) throws TransportException.CodecException {
        Command command = (Command) obj;
        if (command.getPayload() instanceof JoyQueuePayload) {
            fillHeader((JoyQueueHeader) command.getHeader(), (JoyQueuePayload) command.getPayload());
        }
        super.encode(obj, buffer);
    }

    public void fillHeader(JoyQueueHeader header, JoyQueuePayload payload) {
        payload.setHeader(header);
    }
}