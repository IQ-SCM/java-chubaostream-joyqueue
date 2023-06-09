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
package org.joyqueue.network.transport.codec;

import io.netty.buffer.ByteBuf;
import org.joyqueue.network.transport.command.Header;

/**
 * PayloadDecoder
 *
 * author: gaohaoxiang
 * date: 2018/8/21
 */
public interface PayloadDecoder<H extends Header> {

    Object decode(H header, ByteBuf buffer) throws Exception;
}