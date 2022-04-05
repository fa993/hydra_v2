package com.fa993.hydra.exceptions;

public class MalformedConfigurationFileException extends RuntimeException {

    public MalformedConfigurationFileException() {
        super("Exception in Parsing Configuration File");
    }

}
