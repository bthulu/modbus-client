package bthulu.modbus.client;

import io.netty.channel.EventLoopGroup;

import java.util.concurrent.CompletableFuture;

/**
 * modbus工具类, 使用前必须先通过{@link #setMaster(ModbusMaster)}设置ModbusMaster
 */
public abstract class ModbusUtil {
    private static ModbusMaster master;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (master != null) {
                master.close();
            }
        }));
    }

    private static ModbusMaster master() {
        if (master == null) {
            throw new ModbusException("call ModbusUtil#setMaster before use, if use SpringBootTest, set it in spring init");
        }
        return master;
    }

    public static synchronized void setMaster(ModbusMaster master) {
        close();
        ModbusUtil.master = master;
    }

    public static EventLoopGroup group() {
        return master.group();
    }

    public static ModbusByteBuf read(String ipPort, int address, int count) {
        return master().read(ipPort, address, count);
    }

    public static ModbusByteBuf read(String ipPort, int address, int count, int retries) {
        return master().read(ipPort, address, count, retries);
    }

    public static CompletableFuture<ModbusByteBuf> readAsync(String ipPort, int address, int count) {
        return master().readAsync(ipPort, address, count);
    }

    public static CompletableFuture<ModbusByteBuf> readAsync(String ipPort, int address, int count, int retries) {
        return master().readAsync(ipPort, address, count, retries);
    }

    public static void write(String ipPort, int address, ModbusByteBuf data) {
        master().write(ipPort, address, data);
    }

    public static void write(String ipPort, int address, int i) {
        master().write(ipPort, address, ModbusByteBuf.wrap(i));
    }

    public static void write(String ipPort, int address, short i) {
        master().write(ipPort, address, ModbusByteBuf.wrap(i));
    }

    public static void write(String ipPort, int address, ModbusByteBuf data, int retries) {
        master().write(ipPort, address, data, retries);
    }

    public static CompletableFuture<Void> writeAsync(String ipPort, int address, ModbusByteBuf buf) {
        return master().writeAsync(ipPort, address, buf);
    }

    public static CompletableFuture<Void> writeAsync(String ipPort, int address, ModbusByteBuf buf, int retries) {
        return master().writeAsync(ipPort, address, buf, retries);
    }

    public static synchronized void close() {
        if (master != null) {
            master.close();
        }
    }
}
