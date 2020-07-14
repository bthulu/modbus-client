package bthulu.modbus.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ModbusException extends RuntimeException {
    public ModbusException(String message) {
        super(message);
    }

    public ModbusException(Throwable cause) {
        super(cause);
    }

    public ModbusException(String message, Throwable cause) {
        super(message, cause);
    }

    private static final Map<Integer, String> slaveExceptionCodes;
    static {
        Map<Integer, String> map = new HashMap<>(15);
        // Function code received in the query is not recognized or allowed by slave
        map.put(1, "Illegal Function");
        // Data address of some or all the required entities are not allowed or do not exist in slave
        map.put(2, "Illegal Data Address");
        // Value is not accepted by slave
        map.put(3, "Illegal Data Value");
        // Unrecoverable error occurred while slave was attempting to perform requested action
        map.put(4, "Slave Device Failure");
        // Slave has accepted request and is processing it, but a long duration of time is required. This response is returned to prevent a timeout error from occurring in the master. Master can next issue a Poll Program Complete message to determine whether processing is completed
        map.put(5, "Acknowledge");
        // Slave is engaged in processing a long-duration command. Master should retry later
        map.put(6, "Slave Device Busy");
        // Slave cannot perform the programming functions. Master should request diagnostic or error information from slave
        map.put(7, "Negative Acknowledge");
        // Slave detected a parity error in memory. Master can retry the request, but service may be required on the slave device
        map.put(8, "Memory Parity Error");
        // Specialized for Modbus gateways. Indicates a misconfigured gateway
        map.put(10, "Gateway Path Unavailable");
        // Specialized for Modbus gateways. Sent when slave fails to respond
        map.put(11, "Gateway Target Device Failed to Respond");
        slaveExceptionCodes = Collections.unmodifiableMap(map);
    }

    static String describeExceptionCode(int exCode) {
        return slaveExceptionCodes.getOrDefault(exCode, "");
    }
}
