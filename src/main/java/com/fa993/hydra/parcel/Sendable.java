package com.fa993.hydra.parcel;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public interface Sendable {

    byte MASK_FOR_TOKEN = 4;
    byte MASK_FOR_COMMAND = 5;

    public boolean encode(ByteBuffer buffer) throws BufferOverflowException;

}
