package edu.buaa.utils;

public class TimeTicker {
    private final int seconds;
    private final int times;
    private final long init;
    private int countdown;
    private long startT;

    public TimeTicker(int seconds, int times){
        this.seconds = seconds;
        this.times = times;
        this.countdown = times;
        this.startT = System.currentTimeMillis();
        this.init = startT;
    }

    public boolean shouldTick(){
        if(countdown>0){
            countdown--;
            return false;
        }else{
            countdown = times;
            long now = System.currentTimeMillis();
            if( now - this.startT > seconds * 1000L){
                this.startT = now;
                return true;
            }else{
                return false;
            }
        }
    }

    public long duration(){
        return startT - init;
    }

    public boolean shouldTick(int i){
        long now = System.currentTimeMillis();
        if( (now - this.startT) > seconds * 1000L){
            this.startT = now;
            return true;
        }else{
            return false;
        }
    }
}
