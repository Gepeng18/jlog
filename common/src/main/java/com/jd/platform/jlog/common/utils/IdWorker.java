package com.jd.platform.jlog.common.utils;


import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;

/**
 * 雪花算法（SnowFlake）
 *
 * @author wuweifeng
 * @version 1.0
 * @date 2021-08-23
 */
public class IdWorker {
    private static final long EPOCH;

    private static final long SEQUENCE_BITS = 6L;

    private static final long WORKER_ID_BITS = 10L;

    private static final long SEQUENCE_MASK = (1 << SEQUENCE_BITS) - 1;

    private static final long WORKER_ID_LEFT_SHIFT_BITS = SEQUENCE_BITS;

    private static final long TIMESTAMP_LEFT_SHIFT_BITS = WORKER_ID_LEFT_SHIFT_BITS + WORKER_ID_BITS;

    private static final long WORKER_ID_MAX_VALUE = 1L << WORKER_ID_BITS;

    private static long workerId;

    static {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2017, Calendar.APRIL, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        EPOCH = calendar.getTimeInMillis();
        initWorkerId();
    }

    private static long sequence;

    private static long lastTime;

    /**
     * 初始化workerId
     */
    private static void initWorkerId() {
        InetAddress address = getLocalAddress();
        byte[] ipAddressByteArray = address.getAddress();
        setWorkerId((long) (((ipAddressByteArray[ipAddressByteArray.length - 2] & 0B11) << Byte.SIZE) + (ipAddressByteArray[ipAddressByteArray.length - 1] & 0xFF)));
    }

    private static InetAddress getLocalAddress() {
        try {
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                if (addresses.hasMoreElements()) {
                    return addresses.nextElement();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot get LocalHost InetAddress, please check your network!");
        }
        return null;
    }

    /**
     * 设置工作进程Id.
     *
     * @param workerId 工作进程Id
     */
    private static void setWorkerId(final Long workerId) {
        if (workerId >= 0L && workerId < WORKER_ID_MAX_VALUE) {
            IdWorker.workerId = workerId;
        } else {
            throw new RuntimeException("workerId is illegal");
        }
    }

    //雪花算法生成id
    public static long nextId() {
        long time = System.currentTimeMillis();
        // 如果时钟回溯，就报错
        if (lastTime > time) {
            throw new RuntimeException("Clock is moving backwards, last time is %d milliseconds, current time is %d milliseconds" + lastTime);
        }
        // 如果当前时间和上次生成ID的时间相同，则sequence自增1，如果自增后发现是0，则等下一个时间戳
        // 如果当前时间和上次生成ID的时间不相同，则sequence从0开始
        if (lastTime == time) {
            if (0L == (sequence = ++sequence & SEQUENCE_MASK)) {
                time = waitUntilNextTime(time);
            }
        } else {
            sequence = 0;
        }
        // 记录本次生成ID的时间
        lastTime = time;
        // 雪花算法生生成id，时间戳为 当前时间 - 2017.3.1的时间戳， workerId是根据ip地址确定的
        return ((time - EPOCH) << TIMESTAMP_LEFT_SHIFT_BITS) | (workerId << WORKER_ID_LEFT_SHIFT_BITS) | sequence;
    }

    private static long waitUntilNextTime(final long lastTime) {
        long time = System.currentTimeMillis();
        while (time <= lastTime) {
            time = System.currentTimeMillis();
        }
        return time;
    }
}
