package org.daijie.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Random;

/**
 *
 * <p>
 * 在分布式系统中，需要生成全局UID的场合还是比较多的，twitter的snowflake解决了这种需求，
 * 实现也还是很简单的，除去配置信息，核心代码就是毫秒级时间41位+机器ID 10位+毫秒内序列12位。
 * 该项目地址为：https://github.com/twitter/snowflake是用Scala实现的。
 * python版详见开源项目https://github.com/erans/pysnowflake。
 * </p>
 *
 * @author daijie
 * @since 2016-01-22
 */
public class IdWorker {

	/**
	 * 根据具体机器环境提供
	 */
	private final long workerId;

	/**
	 * 滤波器,使时间变小,生成的总位数变小,一旦确定不能变动
	 */
	private final static long twepoch = 1361753741828L;

	private long sequence = 0L;

	private final static long workerIdBits = 10L;

	private final static long maxWorkerId = -1L ^ -1L << workerIdBits;

	private final static long sequenceBits = 12L;

	private final static long workerIdShift = sequenceBits;

	private final static long timestampLeftShift = sequenceBits + workerIdBits;

	private final static long sequenceMask = -1L ^ -1L << sequenceBits;

	private long lastTimestamp = -1L;

	/**
	 * 主机和进程的机器码
	 */
	private static IdWorker worker = new IdWorker();

	private static final Logger logger = LoggerFactory.getLogger(IdWorker.class);

	/**
	 * 主机和进程的机器码
	 */
	private static final int _genmachine;


	static {
		try {
			int machinePiece;
			{
				try {
					StringBuilder sb = new StringBuilder();
					Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
					while (e.hasMoreElements()) {
						NetworkInterface ni = e.nextElement();
						sb.append(ni.toString());
					}
					machinePiece = sb.toString().hashCode() << 16;
				} catch (Throwable e) {
					logger.error(" IdWorker error. ", e);
					machinePiece = new Random().nextInt() << 16;
				}
				logger.debug("machine piece post: " + Integer.toHexString(machinePiece));
			}

			final int processPiece;
			{
				int processId = new Random().nextInt();
				try {
					processId = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().hashCode();
				} catch (Throwable t) {
				}

				ClassLoader loader = IdWorker.class.getClassLoader();
				int loaderId = loader != null ? System.identityHashCode(loader) : 0;

				StringBuilder sb = new StringBuilder();
				sb.append(Integer.toHexString(processId));
				sb.append(Integer.toHexString(loaderId));
				processPiece = sb.toString().hashCode() & 0xFFFF;
				logger.debug("process piece: " + Integer.toHexString(processPiece));
			}

			_genmachine = machinePiece | processPiece;
			logger.debug("machine : " + Integer.toHexString(_genmachine));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}


	public IdWorker() {
		workerId = _genmachine % (IdWorker.maxWorkerId + 1);
	}


	public static long getId() {
		return worker.nextId();
	}
	
	public static String getDayId() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		String day = sdf.format(new Date());
		return day + worker.nextId();
	}


	public synchronized long nextId() {
		long timestamp = timeGen();
		if (lastTimestamp == timestamp) {
			sequence = sequence + 1 & IdWorker.sequenceMask;
			if (sequence == 0) {
				timestamp = tilNextMillis(lastTimestamp);
			}
		} else {
			sequence = 0;
		}
		if (timestamp < lastTimestamp) {
			try {
				throw new Exception(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds",
						lastTimestamp - timestamp));
			} catch (Exception e) {
				logger.error(" IdWorker error. ", e);
			}
		}

		lastTimestamp = timestamp;
		return timestamp - twepoch << timestampLeftShift | workerId << IdWorker.workerIdShift | sequence;
	}


	private long tilNextMillis(final long lastTimestamp1) {
		long timestamp = timeGen();
		while (timestamp <= lastTimestamp1) {
			timestamp = timeGen();
		}
		return timestamp;
	}


	private long timeGen() {
		return System.currentTimeMillis();
	}
	
	public static String getRandomNumber(int length) {
		StringBuilder val = new StringBuilder(length);
		Random random = new Random();
		for (int i = 0; i < length; i++) {
			val.append(String.valueOf(random.nextInt(10)));
		}
		return val.toString();
	}

}