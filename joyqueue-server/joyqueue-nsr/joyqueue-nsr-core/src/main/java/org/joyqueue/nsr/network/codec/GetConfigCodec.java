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

import io.netty.buffer.ByteBuf;
import org.joyqueue.network.serializer.Serializer;
import org.joyqueue.network.transport.command.Header;
import org.joyqueue.network.transport.command.Type;
import org.joyqueue.nsr.network.NsrPayloadCodec;
import org.joyqueue.nsr.network.command.GetConfig;
import org.joyqueue.nsr.network.command.NsrCommandType;

/**
 * @author wylixiaobin
 * Date: 2019/1/29
 */
public class GetConfigCodec implements NsrPayloadCodec<GetConfig>, Type {
    @Override
    public GetConfig decode(Header header, ByteBuf buffer) throws Exception {
        return new GetConfig().group(Serializer.readString(buffer)).key(Serializer.readString(buffer));
    }

    @Override
    public void encode(GetConfig payload, ByteBuf buffer) throws Exception {
        Serializer.write(payload.getGroup(),buffer);
        Serializer.write(payload.getKey(),buffer);
    }

    @Override
    public int type() {
        return NsrCommandType.GET_CONFIG;
    }
}
