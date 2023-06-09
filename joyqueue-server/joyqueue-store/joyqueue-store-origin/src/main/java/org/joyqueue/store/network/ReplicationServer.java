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
package org.joyqueue.store.network;

import org.joyqueue.broker.BrokerContext;
import org.joyqueue.network.event.TransportEvent;
import org.joyqueue.network.transport.TransportServer;
import org.joyqueue.network.transport.TransportServerFactory;
import org.joyqueue.network.transport.command.handler.CommandHandlerFactory;
import org.joyqueue.network.transport.command.handler.ExceptionHandler;
import org.joyqueue.network.transport.config.ServerConfig;
import org.joyqueue.store.ha.election.ElectionService;
import org.joyqueue.toolkit.concurrent.EventBus;
import org.joyqueue.toolkit.concurrent.EventListener;
import org.joyqueue.toolkit.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 存储层Raft复制服务端
 *
 * author: gaohaoxiang
 * date: 2018/9/17
 */
public class ReplicationServer extends Service {

    protected static final Logger logger = LoggerFactory.getLogger(ReplicationServer.class);

    private ServerConfig config;
    private EventBus<TransportEvent> transportEventBus;
    private ExceptionHandler exceptionHandler;
    private TransportServerFactory transportServerFactory;
    private TransportServer transportServer;
    private CommandHandlerFactory commandHandlerFactory;

    public ReplicationServer(ServerConfig config, BrokerContext brokerContext, ElectionService electionService) {
        this.config = config;
        this.transportEventBus = new EventBus<>("joyqueue-backend-eventBus");
        this.exceptionHandler = new BrokerExceptionHandler();
        this.commandHandlerFactory = new RaftCommandFactory(brokerContext,electionService);
        this.transportServerFactory = new RaftServerTransportFactory(commandHandlerFactory, exceptionHandler, transportEventBus);
    }

    public void addListener(EventListener<TransportEvent> listener) {
        transportEventBus.addListener(listener);
    }

    public void removeListener(EventListener<TransportEvent> listener) {
        transportEventBus.removeListener(listener);
    }

    @Override
    protected void doStart() throws Exception {
        this.transportEventBus.start();
        this.transportServer = transportServerFactory.bind(config, config.getHost(), config.getPort());
        this.transportServer.start();
        logger.info("backend server is started, host: {}, port: {}", config.getHost(), config.getPort());
    }

    @Override
    protected void doStop() {
        this.transportEventBus.stop();
        this.transportServer.stop();
    }
}