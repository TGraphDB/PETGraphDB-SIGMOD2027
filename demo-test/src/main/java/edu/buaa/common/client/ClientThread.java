package edu.buaa.common.client;

import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ClientThread extends Thread {
    private volatile boolean shutdown = false;
    private volatile long complete = 0;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(800);
    private long timeRunning = 0;
    private static final Logger log = LoggerFactory.getLogger(ClientThread.class);

    public ClientThread(int id){
        this.setDaemon(true);
        this.setName("ClientThread-"+id);
    }

    @Override
    public void run(){
        TimeMonitor tm = new TimeMonitor();
        tm.begin("Service");
        try {
            while (!shutdown || queue.size()>0) {
                Runnable req = queue.poll(1, TimeUnit.SECONDS);
                if(req!=null) {
                    tm.begin("Run");
                    req.run();
                    complete++;
                    tm.end("Run");
                    timeRunning += tm.duration("Run");
                }
            }
        }catch (Exception e){
            log.error("ERROR process req.", e);
        }finally {
            tm.end("Service");
            long totalTime = tm.duration("Service");
            log.info("{} exit after process {} request using {} seconds. ({}% time running)", this.getName(), complete, totalTime/1000, timeRunning*100/totalTime);
        }
    }

    public void add(Runnable req) throws InterruptedException {
        if(!shutdown) queue.put(req);
        else throw new IllegalStateException("ClientThread is shutdown.");
    }

    public void shutdown(){
        shutdown = true;
    }

    public int getQueueSize(){
        return queue.size();
    }

    public long getCompleteSize() {
        return complete;
    }
}
