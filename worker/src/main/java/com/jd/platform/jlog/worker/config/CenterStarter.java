package com.jd.platform.jlog.worker.config;

import com.jd.platform.jlog.common.constant.Constant;
import com.jd.platform.jlog.common.utils.IpUtils;
import com.jd.platform.jlog.core.ConfiguratorFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 跟配置中心的通信
 *
 * @author wuweifeng
 * @version 1.0
 * @date 2021-08-12
 */
@Component
public class CenterStarter {
    /**
     * 该worker为哪个app服务
     */
    @Value("${config.workerPath}")
    private String workerPath;
    /**
     * 配置中心地址
     */
    @Value("${config.server}")
    private String configServer;
    /**
     * 机房
     */
    @Value("${config.mdc}")
    private String mdc;


    /**
     * 上报自己的ip到配置中心
     */
    public void uploadSelfInfo() {
        //开启上传worker信息
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(() -> {

            try {
                ConfiguratorFactory.getInstance().putConfig(buildKey(), buildValue());
                ConfiguratorFactory.getInstance().putConfig(buildSecondKey(), buildValue());
            } catch (Exception e) {
                //do nothing
                e.printStackTrace();
            }

        }, 0, 5, TimeUnit.SECONDS);
    }

    /**
     * 在配置中心存放的key
     */
    private String buildKey() {
        String hostName = IpUtils.getHostName();
        return Constant.WORKER_PATH + workerPath + "/" + hostName;
    }

    /**
     * 在配置中心对应机房存放的key
     */
    private String buildSecondKey() {
        String hostName = IpUtils.getHostName();
        return Constant.WORKER_PATH + workerPath + "/" + mdc + "/" + hostName;
    }

    /**
     * 在配置中心存放的value
     */
    private String buildValue() {
        String ip = IpUtils.getIp();
        return ip + Constant.SPLITER + Constant.NETTY_PORT;
    }

}
