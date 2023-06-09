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
package org.joyqueue.broker.store;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Shorts;
import org.apache.commons.collections.CollectionUtils;
import org.joyqueue.broker.cluster.ClusterManager;
import org.joyqueue.broker.cluster.event.CompensateEvent;
import org.joyqueue.broker.config.BrokerStoreConfig;
import org.joyqueue.domain.Broker;
import org.joyqueue.domain.PartitionGroup;
import org.joyqueue.domain.Replica;
import org.joyqueue.domain.TopicName;
import org.joyqueue.event.MetaEvent;
import org.joyqueue.nsr.NameService;
import org.joyqueue.nsr.event.AddPartitionGroupEvent;
import org.joyqueue.nsr.event.AddTopicEvent;
import org.joyqueue.nsr.event.LeaderChangeEvent;
import org.joyqueue.nsr.event.RemovePartitionGroupEvent;
import org.joyqueue.nsr.event.RemoveTopicEvent;
import org.joyqueue.nsr.event.UpdatePartitionGroupEvent;
import org.joyqueue.store.PartitionGroupStore;
import org.joyqueue.store.StoreService;
import org.joyqueue.toolkit.concurrent.EventListener;
import org.joyqueue.toolkit.concurrent.NamedThreadFactory;
import org.joyqueue.toolkit.service.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * StoreInitializer
 * author: gaohaoxiang
 * date: 2019/8/28
 */
public class StoreInitializer extends Service implements EventListener<MetaEvent> {

    protected static final Logger logger = LoggerFactory.getLogger(StoreInitializer.class);

    private BrokerStoreConfig config;
    private NameService nameService;
    private ClusterManager clusterManager;
    private StoreService storeService;
    private static final long WAIT_STORE_READY_TIMEOUT_MS = 3000L;

    public StoreInitializer(BrokerStoreConfig config, NameService nameService, ClusterManager clusterManager, StoreService storeService) {
        this.config = config;
        this.nameService = nameService;
        this.clusterManager = clusterManager;
        this.storeService = storeService;
    }

    @Override
    protected void doStart() throws Exception {
        restore();
        clusterManager.addListener(this);
    }

    @Override
    protected void doStop() {
        Broker broker = clusterManager.getBroker();
        List<Replica> replicas = nameService.getReplicaByBroker(broker.getId());
        if (CollectionUtils.isEmpty(replicas)) {
            return;
        }
        for (Replica replica : replicas) {
            storeService.stopPartitionGroup(replica.getTopic().getFullName(), replica.getGroup());
        }
    }

    protected void restore() throws Exception {
        Broker broker = clusterManager.getBroker();
        List<Replica> replicas = nameService.getReplicaByBroker(broker.getId());
        if (CollectionUtils.isEmpty(replicas)) {
            return;
        }
//        CountDownLatch latch = new CountDownLatch(replicas.size());
//        for (Replica replica : replicas) {
//            PartitionGroup group = clusterManager.getPartitionGroupByGroup(replica.getTopic(),replica.getGroup());
//            if (group == null) {
//                logger.warn("group is null topic {},replica {}", replica.getTopic(), replica.getGroup());
//                throw new RuntimeException(String.format("group is null topic %s,replica %s", replica.getTopic(), replica.getGroup()));
//            }
//            if (!group.getReplicas().contains(broker.getId())) {
//                continue;
//            }
//            PartitionGroupStore store = doRestore(group, replica, broker);
//            if(null != store) {
//                store.whenClusterReady(WAIT_STORE_READY_TIMEOUT_MS).thenAccept(ready -> {
//                    if(!ready) {
//                        logger.warn("Wait for partition group ready timeout! Topic: {}, group: {}.", replica.getTopic(), replica.getGroup());
//                    }
//                    latch.countDown();
//                });
//            } else {
//                latch.countDown();
//            }
//        }
//        logger.info("Waiting all stores ready...");
//        boolean timeout = latch.await(WAIT_STORE_READY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
//        if(timeout) {
//            logger.warn("Some stores is NOT ready until timeout!");
//        } else {
//            logger.info("All stores are ready!");
//        }
//        // 并行恢复所有的PartitionGroup
        ExecutorService executor = Executors.newFixedThreadPool(32, new NamedThreadFactory("Store-recover-threads"));
        try {
            CompletableFuture.allOf(
                    replicas.stream()
                            .map(replica -> CompletableFuture.runAsync(() -> {
                                try {
                                    PartitionGroup group = clusterManager.getPartitionGroupByGroup(replica.getTopic(), replica.getGroup());
                                    if (group == null) {
                                        logger.warn("group is null topic {},replica {}", replica.getTopic(), replica.getGroup());
                                        throw new RuntimeException(String.format("group is null topic %s,replica %s", replica.getTopic(), replica.getGroup()));
                                    }
                                    if (!group.getReplicas().contains(broker.getId())) {
                                        return;
                                    }
                                    doRestore(group, replica, broker);
                                } catch (Exception e) {
                                    throw new CompletionException(e);
                                }
                                    }, executor)
                            ).toArray(CompletableFuture[]::new)
            ).get();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Recovery from local
     **/
    protected PartitionGroupStore doRestore(PartitionGroup group, Replica replica, Broker broker) throws Exception {
        logger.info("begin restore topic {},group.no {} group {}",replica.getTopic().getFullName(),replica.getGroup(),group);

        return storeService.restoreOrCreatePartitionGroup(
                group.getTopic().getFullName(),
                group.getGroup(),
                Shorts.toArray(group.getPartitions()),
                new ArrayList<>(group.getReplicas()),
                new ArrayList<>(group.getLearners()),
                broker.getId(),StoreUtils.partitionGroupExtendProperties(group));
    }

    /**
     * Create partition group properties supplier
     *
     **/
    @Override
    public void onEvent(MetaEvent event) {
        try {
            switch (event.getEventType()) {
                case ADD_TOPIC: {
                    AddTopicEvent addTopicEvent = (AddTopicEvent) event;
                    for (PartitionGroup partitionGroup : addTopicEvent.getPartitionGroups()) {
                        onAddPartitionGroup(addTopicEvent.getTopic().getName(), partitionGroup);
                    }
                    break;
                }
                case REMOVE_TOPIC: {
                    RemoveTopicEvent removeTopicEvent = (RemoveTopicEvent) event;
                    for (PartitionGroup partitionGroup : removeTopicEvent.getPartitionGroups()) {
                        onRemovePartitionGroup(removeTopicEvent.getTopic().getName(), partitionGroup);
                    }
                    break;
                }
                case ADD_PARTITION_GROUP: {
                    AddPartitionGroupEvent addPartitionGroupEvent = (AddPartitionGroupEvent) event;
                    onAddPartitionGroup(addPartitionGroupEvent.getTopic(), addPartitionGroupEvent.getPartitionGroup());
                    break;
                }
                case UPDATE_PARTITION_GROUP: {
                    UpdatePartitionGroupEvent updatePartitionGroupEvent = (UpdatePartitionGroupEvent) event;
                    onUpdatePartitionGroup(updatePartitionGroupEvent.getTopic(), updatePartitionGroupEvent.getOldPartitionGroup(), updatePartitionGroupEvent.getNewPartitionGroup());
                    break;
                }
                case REMOVE_PARTITION_GROUP: {
                    RemovePartitionGroupEvent removePartitionGroupEvent = (RemovePartitionGroupEvent) event;
                    onRemovePartitionGroup(removePartitionGroupEvent.getTopic(), removePartitionGroupEvent.getPartitionGroup());
                    break;
                }
                case LEADER_CHANGE: {
                    LeaderChangeEvent leaderChangeEvent = (LeaderChangeEvent) event;
                    PartitionGroup oldConfig=leaderChangeEvent.getOldPartitionGroup();
                    PartitionGroup newConfig=leaderChangeEvent.getNewPartitionGroup();
                    Preconditions.checkArgument(oldConfig!=null&&newConfig!=null&&oldConfig.getGroup()==newConfig.getGroup(),"Preferred leader change must with same partition group");
                    onLeaderChange(leaderChangeEvent.getTopic(), newConfig.getGroup(), newConfig.getLeader());
                    break;
                }
                case COMPENSATE: {
                    CompensateEvent compensateEvent = (CompensateEvent) event;
//                    onCompensate(compensateEvent.getTopics());
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("onEvent exception, event: {}", event, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @param topic  topic
     * @param partitionGroup partition group
     * @param newPreferredLeader preferred leader
     **/
    private void onLeaderChange(TopicName topic, int partitionGroup, int newPreferredLeader) throws Exception{
        storeService.updatePreferredLeader(topic.getFullName(),partitionGroup,newPreferredLeader);
    }


    /**
     * Handle new partition group event
     *
     **/
    protected void onAddPartitionGroup(TopicName topicName, PartitionGroup partitionGroup) throws Exception {
        logger.info("Store init add  topic: {}, partitionGroup: {}", topicName, partitionGroup);
        Set<Integer> replicas = partitionGroup.getReplicas();
        PartitionGroupStore store =
                storeService.createPartitionGroup(
                        topicName.getFullName(),
                        partitionGroup.getGroup(),
                        Shorts.toArray(partitionGroup.getPartitions()),
                        new ArrayList<>(replicas),new ArrayList<>(partitionGroup.getLearners()), clusterManager.getBrokerId(),
                        StoreUtils.partitionGroupExtendProperties(partitionGroup));
        if(null != store) {
            CountDownLatch latch = new CountDownLatch(1);
            store.whenClusterReady(WAIT_STORE_READY_TIMEOUT_MS).thenAccept(ready -> latch.countDown());
            latch.await(WAIT_STORE_READY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

    }

    protected void onUpdatePartitionGroup(TopicName topicName, PartitionGroup oldPartitionGroup, PartitionGroup newPartitionGroup) throws Exception {
        logger.info("Store init update topic from: [{}] to [{}].", oldPartitionGroup, newPartitionGroup);
        int currentBrokerId = clusterManager.getBrokerId();
        if(oldPartitionGroup.getReplicas().contains(currentBrokerId) || newPartitionGroup.getReplicas().contains(currentBrokerId)) {
            // 先处理副本变更
            if(!oldPartitionGroup.getReplicas().equals(newPartitionGroup.getReplicas())) {
                storeService.maybeUpdateReplicas(topicName.getFullName(), newPartitionGroup.getGroup(), newPartitionGroup.getReplicas());
            }
            // 再处理分区变更
            if(!oldPartitionGroup.getPartitions().equals(newPartitionGroup.getPartitions())) {
                storeService.maybeRePartition(topicName.getFullName(), newPartitionGroup.getGroup(), newPartitionGroup.getPartitions());
            }
        }
    }

    protected void onRemovePartitionGroup(TopicName topicName, PartitionGroup partitionGroup) throws Exception {
        logger.info("OnRemovePartitionGroup, topic: {}, partitionGroup: {}", topicName, partitionGroup);
        storeService.removePartitionGroup(topicName.getFullName(), partitionGroup.getGroup());
    }
}
