package bthulu.modbus.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ModbusByteBuf {
    private final ByteBuf unpooled;

    private ModbusByteBuf(int initialCapacity) {
        this.unpooled = Unpooled.buffer(initialCapacity);
    }

    public static ModbusByteBuf capacity(int initialCapacity) {
        return new ModbusByteBuf(initialCapacity);
    }

    public static ModbusByteBuf wrap(int... data) {
        if (data.length > 62) {
            throw new ModbusException("exceed limited 125 registers");
        }
        ModbusByteBuf buf = new ModbusByteBuf(data.length * 4);
        buf.addInt(data);
        return buf;
    }

    public static ModbusByteBuf wrap(short... data) {
        if (data.length > 125) {
            throw new ModbusException("exceed limited 125 registers");
        }
        ModbusByteBuf buf = new ModbusByteBuf(data.length * 2);
        buf.addShort(data);
        return buf;
    }

    public static ModbusByteBuf wrap(byte[] data) {
        if ((data.length & 0x01) == 1) {
            throw new IllegalArgumentException("bytes length is odd");
        }
        ModbusByteBuf buf = new ModbusByteBuf(data.length);
        buf.unpooled.writeBytes(data);
        return buf;
    }

    public int readInt() {
        byte b0 = unpooled.readByte();
        byte b1 = unpooled.readByte();
        byte b2 = unpooled.readByte();
        byte b3 = unpooled.readByte();
        return (b2 << 24) | ((b3 & 0xFF) << 16) | ((b0 & 0xFF) << 8) | (b1 & 0xFF);
    }

    public int[] readInt(int count) {
        int[] ints = new int[count];
        for (int i = 0; i < count * 4; i += 4) {
            byte b0 = unpooled.readByte();
            byte b1 = unpooled.readByte();
            byte b2 = unpooled.readByte();
            byte b3 = unpooled.readByte();
            ints[i / 4] = (b2 << 24) | ((b3 & 0xFF) << 16) | ((b0 & 0xFF) << 8) | (b1 & 0xFF);
        }
        return ints;
    }

    public short readShort() {
        return unpooled.readShort();
    }

    public int readUnsignedShort() {
        return unpooled.readUnsignedShort();
    }

    public short[] readShort(int count) {
        short[] shorts = new short[count];
        for (int i = 0; i < count; i++) {
            shorts[i] = unpooled.readShort();
        }
        return shorts;
    }

    public byte readByte() {
        return unpooled.readByte();
    }

    public byte[] readByte(int count) {
        byte[] bytes = new byte[count];
        for (int i = 0; i < count; i++) {
            bytes[i] = unpooled.readByte();
        }
        return bytes;
    }

    public char[] readBitsReverse() {
        int us = unpooled.readUnsignedShort();
        char[] uscs = Integer.toBinaryString(us).toCharArray();
        char[] cs = new char[16];
        for (int i = 0; i < uscs.length; i++) {
            cs[i] = uscs[uscs.length - 1 - i];
        }
        for (int i = uscs.length; i < 16; i++) {
            cs[i] = '0';
        }
        return cs;
    }

    public ModbusByteBuf skipRegisters(int length) {
        unpooled.skipBytes(length * 2);
        return this;
    }

    public int readableBytes() {
        return unpooled.readableBytes();
    }

    ByteBuf delegate() {
        return unpooled;
    }

    public ModbusByteBuf addInt(int data) {
        unpooled.writeBytes(new byte[]{(byte) (data >> 8), (byte) (data), (byte) (data >> 24), (byte) (data >> 16)});
        return this;
    }

    public ModbusByteBuf addInt(int... data) {
        for (int i : data) {
            unpooled.writeBytes(new byte[]{(byte) (i >> 8), (byte) (i), (byte) (i >> 24), (byte) (i >> 16)});
        }
        return this;
    }

    public ModbusByteBuf addShort(short data) {
        unpooled.writeShort(data);
        return this;
    }

    public ModbusByteBuf addShort(short... data) {
        for (short s : data) {
            unpooled.writeShort(s);
        }
        return this;
    }

    public ModbusByteBuf addShort(int... data) {
        for (int s : data) {
            unpooled.writeShort(s);
        }
        return this;
    }

    public ModbusByteBuf addShort(int data) {
        unpooled.writeShort(data);
        return this;
    }

    public ModbusByteBuf addByte(byte[] data) {
        if ((data.length & 0x01) == 1) {
            throw new IllegalArgumentException("bytes length is odd");
        }
        unpooled.writeBytes(data);
        return this;
    }

    public ModbusByteBuf add(ByteBuf buf) {
        unpooled.writeBytes(buf);
        return this;
    }

    public void validate() {
        if (readableBytes() > 250) {
            throw new ModbusException("exceed limited 125 registers");
        }
    }

    public boolean release() {
        return unpooled.release();
    }
}
