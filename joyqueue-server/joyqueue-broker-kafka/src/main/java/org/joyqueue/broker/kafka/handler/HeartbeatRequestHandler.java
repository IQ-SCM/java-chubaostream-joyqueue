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
package org.joyqueue.broker.kafka.handler;

import org.joyqueue.broker.kafka.KafkaCommandType;
import org.joyqueue.broker.kafka.KafkaContext;
import org.joyqueue.broker.kafka.KafkaContextAware;
import org.joyqueue.broker.kafka.command.HeartbeatRequest;
import org.joyqueue.broker.kafka.command.HeartbeatResponse;
import org.joyqueue.broker.kafka.coordinator.group.GroupCoordinator;
import org.joyqueue.network.transport.Transport;
import org.joyqueue.network.transport.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HeartbeatRequestHandler
 *
 * author: gaohaoxiang
 * date: 2018/11/5
 */
public class HeartbeatRequestHandler extends AbstractKafkaCommandHandler implements KafkaContextAware {

    protected static final Logger logger = LoggerFactory.getLogger(HeartbeatRequestHandler.class);

    private GroupCoordinator groupCoordinator;

    @Override
    public void setKafkaContext(KafkaContext kafkaContext) {
        this.groupCoordinator = kafkaContext.getGroupCoordinator();
    }

    @Override
    public Command handle(Transport transport, Command command) {
        HeartbeatRequest heartbeatRequestRequest = (HeartbeatRequest) command.getPayload();
        short errorCode = groupCoordinator.handleHeartbeat(heartbeatRequestRequest.getGroupId(), heartbeatRequestRequest.getMemberId(), heartbeatRequestRequest.getGroupGenerationId());
        HeartbeatResponse heartbeatResponse = new HeartbeatResponse(errorCode);
        return new Command(heartbeatResponse);
    }

    @Override
    public int type() {
        return KafkaCommandType.HEARTBEAT.getCode();
    }
}
