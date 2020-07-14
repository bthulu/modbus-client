package bthulu.modbus.client;

import java.util.concurrent.CompletableFuture;

public class ReadRequest extends ModbusRequest {
    private final CompletableFuture<ModbusByteBuf> future;
    public final int count;

    public ReadRequest(String ipPort, int address, CompletableFuture<ModbusByteBuf> future, int count) {
        super(ipPort, address);
        this.future = future;
        this.count = count;
    }

    @Override
    public CompletableFuture<ModbusByteBuf> future() {
        return future;
    }
}
