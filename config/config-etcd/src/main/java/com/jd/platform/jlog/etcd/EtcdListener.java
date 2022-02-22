package com.jd.platform.jlog.etcd;

import com.google.protobuf.ByteString;
import com.ibm.etcd.api.Event;
import com.ibm.etcd.api.KeyValue;
import com.ibm.etcd.client.EtcdClient;
import com.ibm.etcd.client.kv.KvClient;
import com.jd.platform.jlog.core.ConfigChangeEvent;
import com.jd.platform.jlog.core.ConfigChangeListener;
import com.jd.platform.jlog.core.ConfigChangeType;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author tangbohu
 * @version 1.0.0
 * @ClassName EtcdListener.java
 * @Description TODO
 * @createTime 2022年02月21日 23:34:00
 */
public class EtcdListener implements ConfigChangeListener {
    private final String key;
    private KvClient.WatchIterator iterator;
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            new DefaultThreadFactory("etcdListener", 1));

    public EtcdListener(String key) {
        this.key = key;
    }


    @Override
    public void onShutDown() {
        this.iterator.close();
        getExecutorService().shutdownNow();
    }

    @Override
    public void onChangeEvent(ConfigChangeEvent event) {
        iterator = EtcdConfigurator.client.getKvClient().watch(ByteString.copyFromUtf8(key)).start();
        while (iterator.hasNext()){
            Event eve = iterator.next().getEvents().get(0);
            KeyValue kv = eve.getKv();
            Event.EventType eveType = eve.getType();
            ConfigChangeType changeType = eveType.equals(Event.EventType.DELETE) ? ConfigChangeType.MODIFY : ConfigChangeType.DELETE;
            event.setKey(key).setNewValue(kv.getValue().toStringUtf8()).setChangeType(changeType);
            this.onChangeEvent(event);
        }
    }

    @Override
    public ExecutorService getExecutorService() {
        return executor;
    }
}