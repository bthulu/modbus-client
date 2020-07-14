package bthulu.modbus.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ModbusCodec extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(ModbusCodec.class);

    private final Map<Integer, ModbusRequest> requestMap = new ConcurrentHashMap<>();

    private final int requestTimeoutMs;

    private final AtomicInteger tidAi = new AtomicInteger(0);

    public ModbusCodec(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    private long lastWriteTime;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // 冠亿modbus设备处理能力有限, 连续不间断发送请求, 会导致部分请求返回设备繁忙.
        // 因此将两次连续发送间隔一小段时间, 5ms的时间间隔能确保连续读取1000次, 设备繁忙率在千分之五以下
        long nowMs = System.currentTimeMillis();
        if (lastWriteTime + 5 < nowMs) {
            lastWriteTime = nowMs;
            write(ctx, (ModbusRequest) msg);
            return;
        }
        long executeTime = lastWriteTime + 5;
        lastWriteTime = executeTime;
        ctx.executor().schedule(() -> write(ctx, (ModbusRequest) msg), executeTime - nowMs, TimeUnit.MILLISECONDS);
    }

    private void write(ChannelHandlerContext ctx, ModbusRequest request) {
        int tid = tidAi.incrementAndGet() % 65535;
        // 超时处理, 并保存本次请求等待响应
        ScheduledFuture<?> schedule = ctx.executor().schedule(() -> {
            requestMap.remove(tid);
            if (request.future().isDone()) {
                return;
            }
            request.future().completeExceptionally(new ModbusException(request.ipPort + " time out"));
        }, requestTimeoutMs, TimeUnit.MILLISECONDS);
        request.setTimeoutSchedule(schedule);
        requestMap.put(tid, request);

        if (request instanceof WriteRequest) {
            WriteRequest w = (WriteRequest) request;

            ModbusByteBuf payload = w.payload;
            payload.validate();
            payload.delegate().readerIndex(0);
            int dataByteSize = payload.readableBytes();

            ByteBuf out = ctx.alloc().buffer(13 + dataByteSize);
            out.writeShort(tid) // 事务编号
                    .writeShort(0) // tcp协议编号
                    .writeShort(7 + dataByteSize) // 剩余字节长度
                    .writeBytes(new byte[]{0x01, 0x10}) // slave编号, 功能码
                    .writeShort(w.address) // 起始寄存器地址
                    .writeShort(dataByteSize / 2) // 写入寄存器数量
                    .writeByte(dataByteSize); // 写入字节长度
            ByteBuf delegate = payload.delegate();
            out.writeBytes(delegate);
            ReferenceCountUtil.release(delegate);
            ctx.writeAndFlush(out);
        } else {
            ReadRequest r = (ReadRequest) request;
            ByteBuf out = ctx.alloc().buffer(12);
            out.writeShort(tid) // 事务编号
                    .writeShort(0) // tcp协议编号
                    .writeShort(6) // 剩余字节长度
                    .writeBytes(new byte[]{0x01, 0x03}) // slave编号, 功能码
                    .writeShort(r.address) // 起始寄存器地址
                    .writeShort(r.count); // 读取长度
            ctx.writeAndFlush(out);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        try {
            channelRead(in);
        } finally {
            ReferenceCountUtil.release(in);
        }
    }

    @SuppressWarnings("unchecked")
    private void channelRead(ByteBuf in) {
        int tid = in.readUnsignedShort();
        in.skipBytes(5);
        byte b = in.readByte();
        ModbusRequest request = requestMap.remove(tid);
        if (request == null) {
            // 请求因超时被从requestMap中移除, 之后收到响应, 就会到达这里
            log.trace("response missed request, maybe timeout before");
            return;
        }
        if (b == 3) {
            in.skipBytes(1);
            request.getTimeoutSchedule().cancel(false);
            ModbusByteBuf buf = ModbusByteBuf.capacity(in.readableBytes()).add(in);
            ((CompletableFuture<ModbusByteBuf>) request.future()).complete(buf);
            return;
        }
        if (b == 16) {
            /*int start = in.readUnsignedShort(); // 起始寄存器地址
            short len = in.readShort(); // 写入字节数*/
            request.getTimeoutSchedule().cancel(false);
            request.future().complete(null);
            return;
        }
        byte errCode = in.readByte();
        String errMsg = ModbusException.describeExceptionCode(errCode);
        request.getTimeoutSchedule().cancel(false);
        request.future().completeExceptionally(new ModbusException(request.ipPort + ": device error, " + errMsg));
    }

}
