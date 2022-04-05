package com.fa993.hydra.exceptions;

public class NoConfigurationFileException extends RuntimeException {

    public NoConfigurationFileException() {
        super("No Configuration File Found");
    }

}
