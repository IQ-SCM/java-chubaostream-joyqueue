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
package org.joyqueue.store;

import org.joyqueue.domain.QosLevel;
import org.joyqueue.toolkit.concurrent.EventListener;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Partition group存储，每个Partition group属于唯一的一个Topic，在Topic范围内拥有唯一的序号，包含多个Partition。
 */
public interface PartitionGroupStore {
    boolean isInitialized();

    /**
     * 获取Topic
     * @return Topic
     */
    String getTopic();

    /**
     * 获取Partition Group 序号
     * @return Partition Group 序号
     */
    int getPartitionGroup();

    /**
     * 列出Partition group下所有的Partition
     * @return Partition数组
     */
    Short[] listPartitions();


    /**
     * 获取分区当前的最小索引，用于初始化消费
     * @param partition 分区
     * @return 返回值小于0时表示分区不存在，否则返回分区最大索引
     */
    long getLeftIndex(short partition);

    /**
     * 获取分区当前的最大索引，用于初始化消费
     * @param partition 分区
     * @return 返回值小于0时表示分区不存在，否则返回分区最大索引
     */
    long getRightIndex(short partition);

    /**
     * 根据消息存储时间获取索引。
     * 如果找到，返回最后一条 “存储时间 <= timestamp” 消息的索引。
     * 如果找不到，返回负值。
     */
    long getIndex(short partition, long timestamp);

    /**
     * 异步写入消息，线程安全，保证ACI，D的保证取决于WriteQosLevel
     * @param writeRequests partition序号和消息
     * @return 以Future形式返回结果
     * @throws NullPointerException eventListener或writeRequests为空时抛出
     * @see WriteResult
     * @see WriteRequest
     */
    CompletableFuture<WriteResult> asyncWrite(QosLevel qosLevel, WriteRequest... writeRequests);

    /**
     * 异步写入消息，线程安全，保证ACI，D的保证取决于WriteQosLevel
     * @param eventListener 回调方法，可以为null，表示不需要回调。
     * @param writeRequests partition序号和消息
     * @throws NullPointerException writeRequests为空时抛出
     * @see WriteResult
     * @see WriteRequest
     */
    void asyncWrite(EventListener<WriteResult> eventListener, QosLevel qosLevel, WriteRequest... writeRequests);



    /**
     * 非阻塞批量读取消息，从指定位置读取消息，如果没有消息立即返回。
     * @param partition partition序号
     * @param index partition内的全局消息序号
     * @param count 要求读取的消息数量，当count < 1 时，按count ==1处理。
     * @param maxSize 返回所有消息的长度之和最大值。参数不大于0时，不限制最大长度。
     *                当第一条消息长度大于maxSize时，返回1条消息。
     *                否则返回尽可能多的消息，保证这些消息长度之不超过maxSize。
     * @return 消息数组，不保证返回消息数量一定等于要求数量count。
     * @see ReadResult
     *
     */
    ReadResult read(short partition, long index, int count, long maxSize) throws IOException;

    /**
     * 是否可读
     * @return true： 可读， false： 不可读
     */
    boolean readable();

    /**
     * 是否可写
     * @return true：可写， false：不可写
     */
    boolean writable();

    /**
     * 等待集群可用后返回的Future
     * @param timeoutMs 超时时长
     * @return true 集群就绪，false 超时
     */
    CompletableFuture<Boolean> whenClusterReady(long timeoutMs);
}
