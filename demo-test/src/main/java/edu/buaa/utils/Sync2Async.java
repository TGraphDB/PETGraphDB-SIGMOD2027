package edu.buaa.utils;

import com.google.common.util.concurrent.Futures;

import java.util.concurrent.*;

public class Sync2Async<T> extends FutureTask<T> {
    public Sync2Async(String name, Callable<T> body){
        super(body);
        Thread th = new Thread(this);
        th.setName(name);
        th.setDaemon(true);
        th.start();
    }

    public void join() throws Exception {
        this.get();
    }

    public static <T> Sync2Async<T> run(String name, Callable<T> body) {
        return new Sync2Async<>(name, body);
    }

    public static Sync2Async<Void> run(String name, Runnable body) {
        return new Sync2Async<>(name, () -> {
            body.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface Runnable{
        void run() throws Exception;
    }
}
