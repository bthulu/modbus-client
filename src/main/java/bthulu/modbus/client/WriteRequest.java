package bthulu.modbus.client;

import java.util.concurrent.CompletableFuture;

public class WriteRequest extends ModbusRequest {
    private final CompletableFuture<Void> future;
    public final ModbusByteBuf payload;

    public WriteRequest(String ipPort, int address, CompletableFuture<Void> future, ModbusByteBuf payload) {
        super(ipPort, address);
        this.future = future;
        this.payload = payload;
    }

    @Override
    public CompletableFuture<Void> future() {
        return future;
    }
}
