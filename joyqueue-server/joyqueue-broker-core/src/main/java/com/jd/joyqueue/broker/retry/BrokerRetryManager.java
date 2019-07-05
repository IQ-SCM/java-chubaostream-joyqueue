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
package com.jd.joyqueue.broker.retry;

import com.jd.joyqueue.broker.BrokerContext;
import com.jd.joyqueue.broker.BrokerContextAware;
import com.jd.joyqueue.broker.cluster.ClusterManager;
import com.jd.joyqueue.broker.network.support.BrokerTransportClientFactory;
import com.jd.joyqueue.domain.Broker;
import com.jd.joyqueue.domain.Consumer;
import com.jd.joyqueue.event.BrokerEvent;
import com.jd.joyqueue.event.EventType;
import com.jd.joyqueue.event.MetaEvent;
import com.jd.joyqueue.exception.JoyQueueException;
import com.jd.joyqueue.network.transport.TransportClient;
import com.jd.joyqueue.network.transport.config.ClientConfig;
import com.jd.joyqueue.nsr.NameService;
import com.jd.joyqueue.server.retry.api.MessageRetry;
import com.jd.joyqueue.server.retry.api.RetryPolicyProvider;
import com.jd.joyqueue.server.retry.model.RetryMessageModel;
import com.jd.joyqueue.server.retry.remote.RemoteMessageRetry;
import com.jd.joyqueue.server.retry.remote.RemoteRetryProvider;
import com.jd.joyqueue.toolkit.concurrent.EventListener;
import com.jd.joyqueue.toolkit.config.PropertySupplier;
import com.jd.joyqueue.toolkit.retry.RetryPolicy;
import com.jd.joyqueue.toolkit.service.Service;
import com.jd.laf.extension.ExtensionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jd.joyqueue.domain.Broker.DEFAULT_RETRY_TYPE;

/**
 * 服务端重试消息管理
 */
public class BrokerRetryManager extends Service implements MessageRetry<Long>, BrokerContextAware {
    private static final Logger logger = LoggerFactory.getLogger(BrokerRetryManager.class);
    // 重试消息服务
    private MessageRetry delegate;
    // 事件监听
    private EventListener eventListener = new BrokerRetryEventListener();
    // 重试类型
    private volatile String retryType;
    // 重试策略
    private RetryPolicyProvider retryPolicyProvider;
    // 远程重试帮助
    private RemoteRetryProvider remoteRetryProvider;
    // 注册中心
    private NameService nameService;
    // 集群管理
    private ClusterManager clusterManager;

    private PropertySupplier propertySupplier;


    public BrokerRetryManager(BrokerContext brokerContext) {
        setBrokerContext(brokerContext);

    }

    @Override
    protected void validate() throws Exception {
        super.validate();

        if (retryPolicyProvider == null) {
            retryPolicyProvider = (topic, app) -> {
                Consumer consumerByTopicAndApp = nameService.getConsumerByTopicAndApp(topic, app);
                if (consumerByTopicAndApp == null) {
                    logger.debug("nameService.getConsumerByTopicAndApp is null by topic:[{}], app:[{}]", topic, app);
                    return new RetryPolicy();
                }
                RetryPolicy retryPolicy = consumerByTopicAndApp.getRetryPolicy();
                if (retryPolicy == null) {
                    logger.debug("consumerByTopicAndApp.getRetryPolicy() is null by topic:[{}], app:[{}]", topic, app);
                    return new RetryPolicy();
                }
                return consumerByTopicAndApp.getRetryPolicy();
            };
        }

        if (remoteRetryProvider == null) {
            remoteRetryProvider = new RemoteRetryProvider() {
                @Override
                public Set<String> getUrls() {
                    List<Broker> brokers = clusterManager.getLocalRetryBroker();

                    logger.info("broker list:{}", Arrays.toString(brokers.toArray()));

                    Set<String /*url=ip:port*/> urlSet = new HashSet<>();
                    for (Broker broker : brokers) {
                        urlSet.add(broker.getIp() + ":" + broker.getBackEndPort());
                    }

                    return urlSet;
                }

                @Override
                public TransportClient createTransportClient() {
                    ClientConfig clientConfig = new ClientConfig();
                    clientConfig.setIoThreadName("joyqueue-retry-io-eventLoop");
                    return new BrokerTransportClientFactory().create(clientConfig);
                }
            };
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        clusterManager.addListener(eventListener);

        retryType = clusterManager.getBroker().getRetryType();
        delegate = loadRetryManager(retryType);
    }

    @Override
    public void setRetryPolicyProvider(RetryPolicyProvider retryPolicyProvider) {
        this.retryPolicyProvider = retryPolicyProvider;
    }


    @Override
    public void addRetry(List<RetryMessageModel> retryMessageModelList) throws JoyQueueException {
        delegate.addRetry(retryMessageModelList);
    }

    @Override
    public void retrySuccess(String topic, String app, Long[] messageIds) throws JoyQueueException {
        delegate.retrySuccess(topic, app, messageIds);
    }

    @Override
    public void retryError(String topic, String app, Long[] messageIds) throws JoyQueueException {
        delegate.retryError(topic, app, messageIds);
    }

    @Override
    public void retryExpire(String topic, String app, Long[] messageIds) throws JoyQueueException {
        delegate.retryExpire(topic, app, messageIds);
    }

    @Override
    public List<RetryMessageModel> getRetry(String topic, String app, short count, long startId) throws JoyQueueException {
        return delegate.getRetry(topic, app, count, startId);
    }

    @Override
    public int countRetry(String topic, String app) throws JoyQueueException {
        return delegate.countRetry(topic, app);
    }

    private MessageRetry loadRetryManager(String type) throws Exception {
        MessageRetry messageRetry;

        if (type.equals(DEFAULT_RETRY_TYPE)) {

            messageRetry = new RemoteMessageRetry(remoteRetryProvider);
        } else {
            messageRetry = ExtensionManager.getOrLoadExtension(MessageRetry.class, type);
        }

        if (messageRetry == null) {
            throw new RuntimeException("No such implementation found." + type);
        }

        messageRetry.setSupplier(propertySupplier);
        messageRetry.setRetryPolicyProvider(retryPolicyProvider);

        messageRetry.start();

        return messageRetry;
    }

    @Override
    public void setBrokerContext(BrokerContext brokerContext) {
        this.nameService = brokerContext.getNameService();
        this.clusterManager = brokerContext.getClusterManager();
        this.propertySupplier = brokerContext.getPropertySupplier();
    }

    @Override
    public void setSupplier(PropertySupplier supplier) {
        this.propertySupplier = supplier;
    }

    /**
     * 监听broker的重试策略变化
     */
    protected class BrokerRetryEventListener implements EventListener<MetaEvent> {

        @Override
        public void onEvent(MetaEvent event) {
            try {
                if (event.getEventType() == EventType.UPDATE_BROKER) {
                    logger.info("listen update broker event.");
                    Broker broker = ((BrokerEvent) event).getBroker();
                    String type = broker != null ? broker.getRetryType() : null;
                    if (type != null && !type.equals(retryType)) {
                        MessageRetry messageRetry = loadRetryManager(type);
                        if (messageRetry != null) {
                            MessageRetry pre = BrokerRetryManager.this.delegate;
                            if (pre != null) {
                                pre.stop();
                            }
                            BrokerRetryManager.this.delegate = messageRetry;
                        }
                    }

                    retryType = type; // 完成实现变更，将类型变更过来

                    logger.info("Broker Retry Mode is : {}", retryType);
                }
            } catch (Exception e) {
                logger.error("process broker retry event error.", e);
            }
        }

    }
}