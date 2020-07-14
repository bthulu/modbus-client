package bthulu.modbus.client;

import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.CompletableFuture;

public abstract class ModbusRequest {
    public final String ipPort;
    public final int address;
    private ScheduledFuture<?> timeoutSchedule;

    public ModbusRequest(String ipPort, int address) {
        this.ipPort = ipPort;
        this.address = address;
    }

    public abstract CompletableFuture<?> future();

    public ScheduledFuture<?> getTimeoutSchedule() {
        return timeoutSchedule;
    }

    public void setTimeoutSchedule(ScheduledFuture<?> timeoutSchedule) {
        this.timeoutSchedule = timeoutSchedule;
    }
}
