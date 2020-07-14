package bthulu.modbus.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ModbusMaster implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ModbusMaster.class);

    private final Bootstrap bootstrap;
    private final int requestTimeoutMs;

    public ModbusMaster() {
        this(5, 60, 15);
    }

    public ModbusMaster(int requestTimeoutSec, int idleSec, int connectTimeoutSec) {
        this(requestTimeoutSec, idleSec, connectTimeoutSec, new NioEventLoopGroup());
    }

    public ModbusMaster(int requestTimeoutSec, int idleSec, int connectTimeoutSec, NioEventLoopGroup elg) {
        requestTimeoutMs = Math.max(requestTimeoutSec, 1) * 1000;
        int finalIdleSec = Math.max(idleSec, 10);
        int connectTimeoutMs = Math.max(connectTimeoutSec, 3) * 1000;
        bootstrap = new Bootstrap();
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs).group(elg).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // modbus连接无需保持重连, 空闲时间到了直接关闭就好
                        pipeline.addLast(new IdleStateHandler(0, 0, finalIdleSec, TimeUnit.SECONDS) {
                            private SocketAddress remoteAddress;

                            @Override
                            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
                                this.remoteAddress = remoteAddress;
                                super.connect(ctx, remoteAddress, localAddress, promise);
                            }

                            @Override
                            protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
                                ctx.close();
                            }

                            @Override
                            public void channelUnregistered(ChannelHandlerContext ctx) {
                                String key = genKey(remoteAddress);
                                channelMap.remove(key);
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                String key = genKey(remoteAddress);
                                if (!(cause instanceof ModbusException)) {
                                    channelMap.remove(key);
                                    ctx.close();
                                }
                                log.error(key, cause);
                            }
                        })
                                .addLast(new LengthFieldBasedFrameDecoder(264, 4, 2))
                                .addLast(new ModbusCodec(requestTimeoutMs));
                    }
                });
    }

    @Override
    public void close() {
        bootstrap.config().group().shutdownGracefully();
    }

    public EventLoopGroup group() {
        return bootstrap.config().group();
    }

    private final Map<String, ChannelFuture> channelMap = new ConcurrentHashMap<>();

    private String genKey(SocketAddress isa) {
        InetSocketAddress a = (InetSocketAddress) isa;
        return a.getAddress().getHostAddress() + ":" + a.getPort();
    }

    private ChannelFuture getChannel(String ipPort) {
        if (ipPort == null || ipPort.isEmpty()) {
            throw new IllegalArgumentException("ipPort is empty");
        }
        String key = ipPort.contains(":") ? ipPort : ipPort + ":502";
        return channelMap.computeIfAbsent(key, k -> {
            String[] split = k.split(":", 2);
            return bootstrap.connect(new InetSocketAddress(split[0], Integer.parseInt(split[1])));
        });
    }

    public CompletableFuture<Void> writeAsync(String ipPort, int address, ModbusByteBuf buf, int retries) {
        if (retries <= 0) {
            return writeAsync0(ipPort, address, buf);
        }
        if (retries > 5) {
            retries = 5;
        }
        CompletableFuture<Void> f = new CompletableFuture<>();
        retry(() -> writeAsync0(ipPort, address, buf), f, retries);
        return f;
    }

    public CompletableFuture<Void> writeAsync(String ipPort, int address, ModbusByteBuf buf) {
        return writeAsync(ipPort, address, buf, 1);
    }

    private CompletableFuture<Void> writeAsync0(String ipPort, int address, ModbusByteBuf buf) {
        buf.validate();
        buf.delegate().readerIndex(0);
        CompletableFuture<Void> f = new CompletableFuture<>();
        getChannel(ipPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                future.channel().writeAndFlush(new WriteRequest(ipPort, address, f, buf));
                return;
            }
            f.completeExceptionally(future.cause());
        });
        return f;
    }

    public CompletableFuture<ModbusByteBuf> readAsync(String ipPort, int address, int count, int retries) {
        if (retries <= 0) {
            return readAsync0(ipPort, address, count);
        }
        if (retries > 5) {
            retries = 5;
        }
        CompletableFuture<ModbusByteBuf> f = new CompletableFuture<>();
        retry(() -> readAsync0(ipPort, address, count), f, retries);
        return f;
    }

    public CompletableFuture<ModbusByteBuf> readAsync(String ipPort, int address, int count) {
        return readAsync(ipPort, address, count, 1);
    }

    private CompletableFuture<ModbusByteBuf> readAsync0(String ipPort, int address, int count) {
        CompletableFuture<ModbusByteBuf> f = new CompletableFuture<>();
        getChannel(ipPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                future.channel().writeAndFlush(new ReadRequest(ipPort, address, f, count));
                return;
            }
            f.completeExceptionally(future.cause());
        });
        return f;
    }

    public ModbusByteBuf read(String ipPort, int address, int count) {
        try {
            return readAsync0(ipPort, address, count).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ModbusException(e);
        }
    }

    public ModbusByteBuf read(String ipPort, int address, int count, int retries) {
        try {
            return readAsync(ipPort, address, count, retries).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ModbusException(e);
        }
    }

    public void write(String ipPort, int address, ModbusByteBuf data) {
        try {
            writeAsync(ipPort, address, data).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ModbusException(e);
        }
    }

    public void write(String ipPort, int address, ModbusByteBuf data, int retries) {
        try {
            writeAsync(ipPort, address, data, retries).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ModbusException(e);
        }
    }

    private <T> void retry(Supplier<CompletableFuture<T>> supplier, CompletableFuture<T> future, int retries) {
        if (future.isDone()) {
            return;
        }
        CompletableFuture<T> f = supplier.get();
        f.whenComplete((buf, cause) -> {
            if (cause == null) {
                future.complete(buf);
                return;
            }
            // 连接超时异常不重试
            if (cause instanceof ConnectTimeoutException) {
                future.completeExceptionally(cause);
                return;
            }
            if (retries > 0) {
                retry(supplier, future, retries - 1);
            } else {
                future.completeExceptionally(cause);
            }
        });
    }
}
