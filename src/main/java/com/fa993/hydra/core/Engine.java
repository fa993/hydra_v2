package com.fa993.hydra.core;


import com.fa993.hydra.parcel.Sendable;
import com.fa993.hydra.parcel.Token;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Three main rules for resolving timeout disputes
 * -If the current server wants to be primary, and it recieves a token with id larger than it.. swallow the event
 * -If the current server is secondary, always pass the token along
 * -Forgot the third one
 */
public class Engine {


    private boolean timedOut;
    private boolean isPrimary = false;

    private ByteBuffer transmissionBuffer = ByteBuffer.allocateDirect(1024);

    private int transmitterConnectedToIndex = -1;

    private ByteBuffer receiverBuffer = ByteBuffer.allocateDirect(1024);

    private ServerSocket receiver;
    private Socket transmitter;

    private Configuration config;

    private Engine() throws Exception {
        this.config = Configuration.readFromFile();
    }

    private void setupReceiver() throws IOException {
        URL url = new URL(this.config.getServers()[this.config.getCurrentServerIndex()]);
        ServerSocketChannel ch = ServerSocketChannel.open();
        ch.bind(new InetSocketAddress(url.getHost(), url.getPort()));
        this.receiver = ch.socket();
        this.receiver.setSoTimeout(this.config.getCooldownTime() + this.config.getCurrentServerIndex() * this.config.getHeartbeatTime() * 2);
    }

    private void setupTransmitter(String url) throws IOException {
        URL u = new URL(url);
        SocketChannel ch = SocketChannel.open();
        ch.configureBlocking(true);
        ch.connect(new InetSocketAddress(u.getHost(), u.getPort()));
        if(this.transmitter != null && !this.transmitter.isClosed()) {
            this.transmitter.close();
            this.transmitterConnectedToIndex = -1;
        }
        this.transmitter = ch.socket();
        this.transmitter.setTcpNoDelay(false);
    }

    private void startListening() throws IOException, InterruptedException {
        Socket s = null;
        Token t = new Token();
        boolean competingForPrimary = false;
        while (s == null) {
            if (this.receiver == null) {
                setupReceiver();
            }
            try {
                s = this.receiver.accept();
                s.getChannel().configureBlocking(true);
                s.setSoTimeout(this.config.getCooldownTime() + this.config.getCurrentServerIndex() * this.config.getHeartbeatTime() * 2);
            } catch (SocketTimeoutException e) {
                //timed out....
                competingForPrimary = true;
                t.setData(this.config.getCurrentServerIndex());
                sendToken(t);
                continue;
            }
            while (true) {
                try {
                    Channels.newChannel(s.getInputStream()).read(receiverBuffer);
                } catch (SocketTimeoutException ex) {
                    //timed out
                    competingForPrimary = true;
                    t.setData(this.config.getCurrentServerIndex());
                    sendToken(t);
                    continue;
                } catch (IOException ex) {
                    receiverBuffer.clear();
                    if(!s.isClosed()) {
                        s.close();
                    }
                    s = null;
                    break;
                }
                receiverBuffer.flip();
                while (receiverBuffer.limit() - 1 > -1 && receiverBuffer.hasRemaining() && receiverBuffer.get(receiverBuffer.limit() - 1) == '\0') {
                    //process
                    switch (receiverBuffer.get()) {
                        case Sendable.MASK_FOR_TOKEN:
                            int nextToken = receiverBuffer.getInt();
                            if ((competingForPrimary && nextToken > this.config.getCurrentServerIndex()) || (isPrimary && nextToken != this.config.getCurrentServerIndex())) {
                                //swallow the event
                                break;
                            }
                            if (nextToken == this.config.getCurrentServerIndex()) {
                                //you are primary
                                if (!isPrimary) {
                                    isPrimary = true;
                                    System.out.println("I am primary");
                                }
                                Thread.sleep(this.config.getHeartbeatTime());
                            } else {
                                if(competingForPrimary) {
                                    System.out.println("I am not primary");
                                }
                                isPrimary = false;
                            }
                            competingForPrimary = false;
                            t.setData(nextToken);
                            sendToken(t);

                            break;
                        case Sendable.MASK_FOR_COMMAND:
                            break;
                    }
                    if (receiverBuffer.get() != '\0') {
                        System.out.println("Incorrect Format");
                        throw new RuntimeException("Hey");
                    }
                }
                receiverBuffer.clear();
            }
        }
//        startTalking();
    }

    private synchronized void transmit(Sendable send) throws IOException {
        transmissionBuffer.clear();
        while (!send.encode(transmissionBuffer)) {
            transmissionBuffer.flip();
            this.transmitter.getChannel().write(transmissionBuffer);
            transmissionBuffer.clear();
        }
        transmissionBuffer.put((byte) '\0');
        transmissionBuffer.flip();
        this.transmitter.getChannel().write(transmissionBuffer);
//        int b =  this.transmitter.getInputStream().read();
//        if(b == 0) {
//            //Ok
//            return true;
//        } else {
//            return false;
//        }
    }

    private void sendToken(Token t) {
        if (this.transmitterConnectedToIndex == (this.config.getCurrentServerIndex() + 1) % this.config.getServers().length) {
            //just transmit
            try {
                transmit(t);
                return;
            } catch (IOException ex) {
                //irregular failure occurred
                this.transmitterConnectedToIndex = -1;
            }
        }
        for (int i = (this.config.getCurrentServerIndex() + 1) % this.config.getServers().length; ; i = (i + 1) % this.config.getServers().length) {
            try {
                if(!(this.transmitter != null && i == this.transmitterConnectedToIndex)) {
                    setupTransmitter(this.config.getServers()[i]);
                }
                transmit(t);
                this.transmitterConnectedToIndex = i;
                break;
            } catch (IOException ex) {
                //log failed connection x
            }
        }
    }


    public static void main(String[] args) throws Exception {
        while (true) {
            Engine t = null;
            try {
                t = new Engine();
                t.startListening();
            } catch (Exception ex) {
                if (t != null) {
                    if (!t.receiver.isClosed()) {
                        t.receiver.close();
                    }
                    if (!t.transmitter.isClosed()) {
                        t.transmitter.close();
                        t.transmitterConnectedToIndex = -1;
                    }
                }
            }
        }
    }
}
