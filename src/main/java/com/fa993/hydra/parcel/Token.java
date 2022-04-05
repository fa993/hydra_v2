package com.fa993.hydra.parcel;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class Token implements Sendable {

    private int data;

    public Token() {
        this.data = -1;
    }

    public boolean encode(ByteBuffer buffer) throws BufferOverflowException {
        buffer.put(MASK_FOR_TOKEN);
        buffer.putInt(this.data);
        return true;
    }

    public void setData(int newData) {
        this.data = newData;
    }

}
