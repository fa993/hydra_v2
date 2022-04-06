package com.fa993.hydra.parcel;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class Token implements Sendable {

    private int data;

    public Token() {
        this.data = -1;
    }

    public void encode(ByteBuffer buffer) throws BufferOverflowException {
        buffer.put(MASK_FOR_TOKEN);
        buffer.putInt(this.data);
    }

    public void setData(int newData) {
        this.data = newData;
    }

}
