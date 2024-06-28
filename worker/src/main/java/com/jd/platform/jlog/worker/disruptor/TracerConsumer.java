package com.jd.platform.jlog.worker.disruptor;

import com.jd.platform.jlog.common.constant.LogTypeEnum;
import com.jd.platform.jlog.common.model.TracerBean;
import com.jd.platform.jlog.common.model.RunLogMessage;
import com.jd.platform.jlog.common.model.TracerData;
import com.jd.platform.jlog.common.utils.ProtostuffUtils;
import com.jd.platform.jlog.common.utils.ZstdUtils;
import com.jd.platform.jlog.worker.store.TracerLogToDbStore;
import com.jd.platform.jlog.worker.store.TracerModelToDbStore;
import com.lmax.disruptor.WorkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.beans.BeanMap;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

import static com.jd.platform.jlog.common.constant.Constant.DEFAULT_BYTE;

/**
 * TracerConsumer
 *
 * @author wuweifeng
 * @version 1.0
 * @date 2021-08-24
 */
public class TracerConsumer implements WorkHandler<OneTracer> {
    /**
     * 已消费完毕的总数量
     */
    private static final LongAdder totalDealCount = new LongAdder();
    /**
     * logger
     */
    private Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * 待入库队列，出入参model
     */
    private TracerModelToDbStore tracerModelToDbStore;
    /**
     * 待入库队列，普通日志
     */
    private TracerLogToDbStore tracerLogToDbStore;

    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public TracerConsumer(TracerModelToDbStore tracerModelToDbStore, TracerLogToDbStore tracerLogToDbStore) {
        this.tracerModelToDbStore = tracerModelToDbStore;
        this.tracerLogToDbStore = tracerLogToDbStore;
    }

    @Override
    public void onEvent(OneTracer oneTracer) {
        try {
            long totalConsume = totalDealCount.longValue();
            boolean needInfo = totalConsume % 1000 == 0;

            //解压后的字节数组
            byte[] decompressBytes = ZstdUtils.decompressBytes(oneTracer.getBytes());

            TracerData tracerData = ProtostuffUtils.deserialize(decompressBytes, TracerData.class);

            //消费处理
            buildTracerModel(tracerData);

            //处理完毕，将数量加1
            totalDealCount.increment();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建要入库的对象
     */
    private void buildTracerModel(TracerData tracerData) {
        //普通日志
        if (LogTypeEnum.TRADE.equals(tracerData.getType())) {
            dealTracerLog(tracerData.getTempLogs());
        } else {
            dealFilterModel(tracerData.getTracerBeanList());
        }
    }

    /**
     * 处理中途日志
     */
    private void dealTracerLog(List<RunLogMessage> tempLogs) {
        if (tempLogs == null) {
            return;
        }
        for (RunLogMessage runLogMessage : tempLogs) {
            Map<String, Object> map = new HashMap<>(12);
            map.put("tracerId", runLogMessage.getTracerId());
            map.put("className", runLogMessage.getClassName());
            map.put("threadName", runLogMessage.getThreadName());
            map.put("methodName", runLogMessage.getMethodName());
            map.put("logLevel", runLogMessage.getLogLevel());
            map.put("createTime", formatLongTime(runLogMessage.getCreateTime()));
            map.put("content", runLogMessage.getContent());
            map.putAll(runLogMessage.getTagMap());
            tracerLogToDbStore.offer(map);
        }
    }

    /**
     * 处理filter里处理的出入参
     */
    private void dealFilterModel(List<TracerBean> tracerList) {
        if (tracerList == null) {
            return;
        }
        for (TracerBean tracerModel : tracerList) {
            if (tracerModel.getResponseContent() == null) {
                tracerModel.setResponseContent(DEFAULT_BYTE);
            }
            tracerModel.setCreateTime(formatLongTime(tracerModel.getCreateTimeLong()));
            Map map = new HashMap(BeanMap.create(tracerModel));
            map.remove("createTimeLong");
            tracerModelToDbStore.offer(map);
        }

    }

    private static String formatLongTime(long time) {
        return DEFAULT_FORMATTER.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()));
    }

}
