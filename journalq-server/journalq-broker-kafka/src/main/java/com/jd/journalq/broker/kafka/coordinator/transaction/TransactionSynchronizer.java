package com.jd.journalq.broker.kafka.coordinator.transaction;

import com.jd.journalq.broker.coordinator.session.CoordinatorSessionManager;
import com.jd.journalq.broker.kafka.config.KafkaConfig;
import com.jd.journalq.broker.kafka.coordinator.transaction.domain.TransactionMarker;
import com.jd.journalq.broker.kafka.coordinator.transaction.domain.TransactionMetadata;
import com.jd.journalq.broker.kafka.coordinator.transaction.domain.TransactionOffset;
import com.jd.journalq.broker.kafka.coordinator.transaction.domain.TransactionPrepare;
import com.jd.journalq.broker.kafka.coordinator.transaction.domain.TransactionState;
import com.jd.journalq.nsr.NameService;
import com.jd.journalq.toolkit.service.Service;
import com.jd.journalq.toolkit.time.SystemClock;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * TransactionSynchronizer
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/4/12
 */
// TODO 补充日志
// TODO 事务日志写入失败处理
public class TransactionSynchronizer extends Service {

    protected static final Logger logger = LoggerFactory.getLogger(TransactionSynchronizer.class);

    private KafkaConfig config;
    private TransactionIdManager transactionIdManager;
    private TransactionLog transactionLog;
    private CoordinatorSessionManager sessionManager;
    private NameService nameService;

    private TransactionCommitSynchronizer transactionCommitSynchronizer;
    private TransactionAbortSynchronizer transactionAbortSynchronizer;

    public TransactionSynchronizer(KafkaConfig config, TransactionIdManager transactionIdManager, TransactionLog transactionLog, CoordinatorSessionManager sessionManager, NameService nameService) {
        this.config = config;
        this.transactionIdManager = transactionIdManager;
        this.transactionLog = transactionLog;
        this.sessionManager = sessionManager;
        this.nameService = nameService;
    }

    @Override
    protected void validate() throws Exception {
        transactionCommitSynchronizer = new TransactionCommitSynchronizer(config, sessionManager, transactionIdManager, nameService);
        transactionAbortSynchronizer = new TransactionAbortSynchronizer(config, sessionManager, transactionIdManager);
    }

    @Override
    protected void doStart() throws Exception {
        transactionCommitSynchronizer.start();
        transactionAbortSynchronizer.start();
    }

    @Override
    protected void doStop() {
        if (transactionCommitSynchronizer != null) {
            transactionCommitSynchronizer.stop();
        }
        if (transactionAbortSynchronizer != null) {
            transactionAbortSynchronizer.stop();
        }
    }

    public boolean prepare(TransactionMetadata transactionMetadata, Set<TransactionPrepare> prepareList) throws Exception {
        return transactionLog.batchWrite(transactionMetadata.getApp(), transactionMetadata.getId(), prepareList);
    }

    public boolean prepareCommit(TransactionMetadata transactionMetadata, Set<TransactionPrepare> prepareList) throws Exception {
        return writeMarker(transactionMetadata, TransactionState.PREPARE_COMMIT);
    }

    public boolean commit(TransactionMetadata transactionMetadata, Set<TransactionPrepare> prepareList, Set<TransactionOffset> offsets) throws Exception {
        boolean isSuccess = true;
        if (CollectionUtils.isNotEmpty(prepareList)) {
            isSuccess = transactionCommitSynchronizer.commitPrepare(transactionMetadata, prepareList);
        }
        if (CollectionUtils.isNotEmpty(offsets)) {
            isSuccess = transactionCommitSynchronizer.commitOffsets(transactionMetadata, offsets);
        }
        if (isSuccess) {
            writeMarker(transactionMetadata, TransactionState.COMPLETE_COMMIT);
        }
        return isSuccess;
    }

    public boolean prepareAbort(TransactionMetadata transactionMetadata, Set<TransactionPrepare> prepareList) throws Exception {
        return writeMarker(transactionMetadata, TransactionState.PREPARE_ABORT);
    }

    public boolean abort(TransactionMetadata transactionMetadata, Set<TransactionPrepare> prepareList) throws Exception {
        boolean isSuccess = transactionAbortSynchronizer.abort(transactionMetadata, prepareList);
        if (isSuccess) {
            writeMarker(transactionMetadata, TransactionState.COMPLETE_ABORT);
        }
        return isSuccess;
    }

    public boolean commitOffset(TransactionMetadata transactionMetadata, Set<TransactionOffset> offsets) throws Exception {
        return transactionLog.batchWrite(transactionMetadata.getApp(), transactionMetadata.getId(), offsets);
    }

    protected boolean writeMarker(TransactionMetadata transactionMetadata, TransactionState transactionState) throws Exception {
        TransactionMarker marker = convertMarker(transactionMetadata, transactionState);
        return transactionLog.write(transactionMetadata.getApp(), transactionMetadata.getId(), marker);
    }

    protected TransactionMarker convertMarker(TransactionMetadata transactionMetadata, TransactionState transactionState) {
        return new TransactionMarker(transactionMetadata.getApp(), transactionMetadata.getId(), transactionMetadata.getProducerId(),
                transactionMetadata.getProducerEpoch(), transactionState, transactionMetadata.getTimeout(), SystemClock.now());
    }
}