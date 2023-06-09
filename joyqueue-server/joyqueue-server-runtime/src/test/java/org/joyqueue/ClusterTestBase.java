package org.joyqueue;

import com.google.common.collect.Lists;
import org.joyqueue.broker.BrokerService;
import org.joyqueue.broker.config.Args;
import org.joyqueue.broker.config.ConfigDef;
import org.joyqueue.broker.consumer.Consume;
import org.joyqueue.broker.consumer.model.ConsumePartition;
import org.joyqueue.broker.consumer.model.PullResult;
import org.joyqueue.broker.consumer.position.PositionStore;
import org.joyqueue.broker.consumer.position.model.Position;
import org.joyqueue.broker.producer.Produce;
import org.joyqueue.broker.producer.PutResult;
import org.joyqueue.broker.protocol.JoyQueueCommandHandler;
import org.joyqueue.domain.*;
import org.joyqueue.exception.JoyQueueCode;
import org.joyqueue.helper.PortHelper;
import org.joyqueue.message.BrokerMessage;
import org.joyqueue.network.protocol.ProtocolService;
import org.joyqueue.network.session.Producer;
import org.joyqueue.nsr.InternalServiceProvider;
import org.joyqueue.nsr.NameService;
import org.joyqueue.nsr.ServiceProvider;
import org.joyqueue.nsr.messenger.Messenger;
import org.joyqueue.nsr.network.NsrCommandHandler;
import org.joyqueue.plugin.SingletonController;
import org.joyqueue.store.StoreService;
import org.joyqueue.store.WriteResult;
import org.joyqueue.toolkit.io.Files;
import org.joyqueue.toolkit.network.IpUtil;
import org.joyqueue.toolkit.service.Service;
import org.joyqueue.toolkit.time.SystemClock;
import org.junit.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Cluster test base
 **/
public class ClusterTestBase extends Service {

    private String DEFAULT_JOYQUEUE="joyqueue";
    private String ROOT_DIR =System.getProperty("java.io.tmpdir")+ File.separator+DEFAULT_JOYQUEUE;
    protected int brokerPort=40088;
    protected int portInterval=10000;
    protected List<BrokerService> brokers=new ArrayList<>();

    public  void start() throws Exception{
        // clean dir ,important
        Files.deleteDirectory(new File(ROOT_DIR));
        closeSingleton();
    }

    public  void closeSingleton() throws Exception{
        SingletonController.forceCloseSingleton();
        SingletonController.closeClassSingleton(Consume.class);
        SingletonController.closeClassSingleton(PositionStore.class);
        SingletonController.closeClassSingleton(Produce.class);
        SingletonController.closeClassSingleton(NameService.class);
        SingletonController.closeClassSingleton(StoreService.class);
        SingletonController.closeClassSingleton(JoyQueueCommandHandler.class);
        SingletonController.closeClassSingleton(Messenger.class);
        SingletonController.closeClassSingleton(ProtocolService.class);
        SingletonController.closeClassSingleton(ServiceProvider.class);
        SingletonController.closeClassSingleton(InternalServiceProvider.class);
        SingletonController.closeClassSingleton(NsrCommandHandler.class);
    }

    /**
     * Launch a N node cluster,顺序启动集群节点
     * @param N node num
     * @param port  broker port
     *
     **/
    public boolean launchCluster(int N, int port, int timeout, TimeUnit unit, String engineName) throws Exception{
        String journalKeeperNodes = IpUtil.getLocalIp()+":"+String.valueOf(PortHelper.getJournalkeeperPort(port));
        for(int i=0;i<N;i++) {
            String rootDir=ROOT_DIR+File.separator+String.format("_%d",i);

            BrokerService broker=new BrokerService(args(port+i*portInterval,rootDir,journalKeeperNodes,engineName));
            /*CompletableFuture.runAsync(new Runnable() {
                @Override
                public void run() {
                    try{

                }catch (Exception e){
                    }
                }
            });*/
            broker.start();
            brokers.add(broker);
        }
        Thread.sleep(5000);
        // wait cluster ready
        BrokerService b= brokers.get(0);
        long start= SystemClock.now();
        do {
            try {
                int cluster = b.getBrokerContext().getNameService().getAllBrokers().size();
                if (cluster >= N) {
                    break;
                }
            }catch (Exception e){

            }
            if (SystemClock.now() - start < unit.toMillis(timeout)) {
                Thread.sleep(1000);
            } else {
                throw new IllegalStateException("Start cluster timeout");
            }
        }while(true);
        System.out.println("Started cluster ok!");
        return true;
    }

    /**
     * Launch multi broker use default store engine, JoyQueue
     **/
    public boolean launchCluster(int N) throws Exception{
        return launchCluster(N,brokerPort,300000,TimeUnit.MILLISECONDS,null);
    }

    /**
     * Launch multi broker
     **/
    public boolean launchJournalKeeperCluster(int N) throws Exception{
        //"JournalKeeper"
        return launchCluster(N,brokerPort,300000,TimeUnit.MILLISECONDS,"JournalKeeper");
    }


    /**
     * Build args to override default setting
     **/
    public String[] args(int port,String applicationRoot,String journalKeeperNodes,String engineName){
        Args args=new Args();
        args.append(ConfigDef.APPLICATION_DATA_PATH.key(),applicationRoot);
        args.append(ConfigDef.TRANSPORT_SERVER_PORT.key(),String.valueOf(port));
        args.append(ConfigDef.NAME_SERVER_JOURNAL_KEEPER_NODES.key(),journalKeeperNodes);
        args.append(ConfigDef.NAME_SERVER_COMPENSATION_CACHE_ENABLE.key(),"false");
        args.append(ConfigDef.NAME_SERVER_ALL_METADATA_CACHE_EXPIRE_TIME.key(),"15000");
        args.append(ConfigDef.NAME_SERVER_COMPENSATION_INTERVAL.key(),"15000");
        if(engineName!=null)
            args.append(ConfigDef.STORE_ENGINE.key(),engineName);
        System.setProperty("PreloadBufferPool.MaxMemory","30%");
        return args.build();
    }


    /**
     * All broker
     **/
    public List<BrokerService> brokers(){
        return null;
    }

    /**
     * Create topic on random select broker
     **/
    public void createTopic(String topic, short partitions) throws Exception{
        NameService ns= nameService();
        Set<Short> partitionSet=new HashSet();
        for(short i=0;i<partitions;i++){
            partitionSet.add(i);
        }
        List<Broker> brokers=ns.getAllBrokers();
        if(brokers.size()<=0) throw new IllegalStateException("No broker!");
        brokers=brokers.size()<3?brokers: shuffle(brokers,3);
        Topic t=new Topic();
        t.setName(new TopicName(topic));
        t.setPartitions(partitions);
        PartitionGroup partitionGroup=new PartitionGroup();
        partitionGroup.setTopic(new TopicName(topic));
        partitionGroup.setGroup(0);
        partitionGroup.setElectType(PartitionGroup.ElectType.raft);
        partitionGroup.setPartitions(partitionSet);
        partitionGroup.setReplicas(brokers.stream().map(Broker::getId).collect(Collectors.toSet()));
        ns.addTopic(t, Lists.newArrayList(partitionGroup));
        // validate topic
        Assert.assertNotNull(ns.getTopicConfig(new TopicName(topic)));
    }

    /**
     *
     * Produce subscribe
     *
     **/
    public void produceSubscribe(String topic,String app) throws Exception{
        NameService ns= nameService();
        Subscription subscription=new Subscription();
        subscription.setTopic(new TopicName(topic));
        subscription.setApp(app);
        subscription.setType(Subscription.Type.PRODUCTION);
        ns.subscribe(subscription,ClientType.JOYQUEUE);
        Assert.assertNotNull(ns.getProducerByTopicAndApp(new TopicName(topic),app));
    }

    /**
     * Consume subscribe
     **/
    public void consumeSubscribe(String topic,String app) throws Exception{
        NameService ns= nameService();
        Subscription subscription=new Subscription();
        subscription.setTopic(new TopicName(topic));
        subscription.setApp(app);
        subscription.setType(Subscription.Type.CONSUMPTION);
        ns.subscribe(subscription,ClientType.JOYQUEUE);
        Assert.assertNotNull(ns.getConsumerByTopicAndApp(new TopicName(topic),app));
    }

    /**
     * Simple consume
     **/
    public void simpleConsumeMessage(Consume consume,String topic,String app) throws Exception{
        org.joyqueue.network.session.Consumer consumer=new org.joyqueue.network.session.Consumer();
        consumer.setTopic(topic);
        consumer.setApp(app);
        consumer.setId("magic-id");
        int timeout=60*1000;
        int tries=10;
        int message=0;
        while(tries-->0){
            PullResult pr = consume.getMessage(consumer, 10, timeout);
            Assert.assertTrue(pr.getCode()== JoyQueueCode.SUCCESS);
            if(!pr.isEmpty()) {
                message += pr.getBuffers().size();
            }
            Thread.sleep(100);
        }
        Assert.assertTrue(message>0);
    }


    /**
     * Waiting consume position ready
     **/
    public void waitConsumeSubscribeReady(Consume consume,String topic,String app,int partitionGroup,int partitions,long time,TimeUnit unit) throws Exception{
        long start=SystemClock.now();
        long timeout=unit.toMillis(time);
        while(true) {
            Map<ConsumePartition, Position> a=consume.getConsumePositionByGroup(new TopicName(topic),app, partitionGroup);
            if(a!=null&&a.size()==partitions){
                for(Map.Entry<ConsumePartition,Position>  e:a.entrySet()){
                    System.out.println("consume position ready:"+e.getKey()+":"+e.getValue());
                }
                return;
            }
            if(SystemClock.now()-start>timeout){
                throw new IllegalStateException("consume position not ready!");
            }
            System.out.println("waiting consume subscribe ready on leader");
            Thread.sleep(1000);
        }
    }
    /***
     * Wait metadata ready
     **/
    public void waitMetadataReady(BrokerService brokerService,String topic,long time,TimeUnit unit) throws Exception{
        long start=SystemClock.now();
        long timeout=unit.toMillis(time);
        do {
            List<Short> partitions = brokerService.getBrokerContext().getClusterManager().getLocalPartitions(new TopicName(topic));
            if(partitions!=null&partitions.size()>0){
                return;
            }
            if(SystemClock.now()-start>timeout){
                throw new IllegalStateException("partition metadata not ready!");
            }
            Thread.sleep(1000);
        }while(true);
    }

    public void simpleProduceMessage(Produce produce,String topic,String app,String msg,String businessId) throws Exception{
        org.joyqueue.network.session.Producer producer=new Producer();
        producer.setTopic(topic);
        producer.setApp(app);
        //producer.setClientType(Cl);
        BrokerMessage bm= create(topic,app,msg,businessId);
        PutResult pr=produce.putMessage(producer,Lists.newArrayList(bm),QosLevel.REPLICATION);
        Assert.assertTrue(pr.getWriteResults().size()>0);
        for(WriteResult r:pr.getWriteResults().values()){
            Assert.assertTrue(r.getCode()== JoyQueueCode.SUCCESS);
        }
    }

    /**
     * Broker message
     **/
    public BrokerMessage create(String topic, String app, String msg,String businessId){
        BrokerMessage bm=new BrokerMessage();
        bm.setTopic(topic);
        bm.setApp(app);
        bm.setBody(msg.getBytes());
        bm.setBusinessId(businessId);
        bm.setClientIp(IpUtil.getLocalIp().getBytes());
        return bm;
    }

    /**
     * Random select n broker
     *
     **/
    public List<Broker> shuffle(List<Broker> brokers,int n){
        Collections.shuffle(brokers);
        return brokers.subList(0,n);
    }


    /**
     * Get Name service
     **/
    public NameService nameService() throws Exception{
        if(brokers.size()<=0) throw new IllegalStateException("no broker exist");
        BrokerService b= brokers.get(0);
        return b.getBrokerContext().getNameService();
    }


    public BrokerService waitLeaderReady(String topic, int partitionGroup, long timeout, TimeUnit unit) throws Exception{
        NameService ns=nameService();

        long start=SystemClock.now();
        long timeoutMs=unit.toMillis(timeout);
        while(true){
            TopicConfig tc=ns.getTopicConfig(new TopicName(topic));
            if(tc!=null) {
                Map<Integer, PartitionGroup> pgMap = tc.getPartitionGroups();
                if (pgMap != null && pgMap.get(partitionGroup) != null) {
                    if (!pgMap.get(partitionGroup).getLeader().equals(-1)) break;
                    if (SystemClock.now() - start > timeoutMs) {
                        throw new IllegalStateException("Leader not found");
                    }
                }
            }
            Thread.sleep(1000);
            System.out.println("waiting leader for "+topic+"/"+partitionGroup);
        }
        TopicConfig tc=ns.getTopicConfig(new TopicName(topic));
        PartitionGroup pg=tc.getPartitionGroups().get(partitionGroup);
        for(BrokerService broker:brokers){
            if(broker.getBrokerContext().getBroker().getId().equals(pg.getLeader())){
                return broker;
            }
        }
        return null;
    }

    public void doStop(){
        System.out.println("Begin to stop cluster");
        for(BrokerService b:brokers){
            b.stop();
        }
        try {
            Thread.sleep(100);
        }catch (InterruptedException e){

        }
        Files.deleteDirectory(new File(ROOT_DIR));
    }
}
