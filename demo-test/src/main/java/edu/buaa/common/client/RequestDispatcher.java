package edu.buaa.common.client;

import com.google.common.util.concurrent.ListenableFutureTask;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

public class RequestDispatcher {
    private final int parallelCnt;
    List<ClientThread> threadList = new LinkedList<>();

    public RequestDispatcher(int parallelCnt){
        this.parallelCnt = parallelCnt;
        for(int i=0;i<parallelCnt;i++){
            threadList.add(new ClientThread(i));
        }
        this.startAll();
    }

    public ListenableFutureTask<DBProxy.ServerResponse> submit(Callable<DBProxy.ServerResponse> req, int section) throws InterruptedException {
        ListenableFutureTask<DBProxy.ServerResponse> ft = ListenableFutureTask.create(req);
        int i;
        if(section>=0) {
            i = section % parallelCnt;
        }else{
            i=0;
            int queueSize = Integer.MAX_VALUE;
            for(int j=0;j<threadList.size();j++){
                ClientThread t = threadList.get(j);
                if(t.getQueueSize()<queueSize) {
                    i=j;
                    queueSize = t.getQueueSize();
                }
            }
        }
        threadList.get(i).add(ft);
        return ft;
    }

    public void awaitClose() throws InterruptedException {
        for(int i=0;i<parallelCnt;i++){
            threadList.get(i).shutdown();
        }
        for(int i=0;i<parallelCnt;i++){
            threadList.get(i).join();
        }
    }

    public int getQueueSize(){
        int size = 0;
        for(int i=0;i<parallelCnt;i++){
            size += threadList.get(i).getQueueSize();
        }
        return size;
    }

    public long getCompletedTaskCount() {
        long completeSize = 0;
        for(int i=0;i<parallelCnt;i++){
            completeSize += threadList.get(i).getCompleteSize();
        }
        return completeSize;
    }

    public void startAll() {
        for(ClientThread t: threadList){
            t.start();
        }
    }

    public interface RequestWs extends Callable<DBProxy.ServerResponse>{

    }
}
