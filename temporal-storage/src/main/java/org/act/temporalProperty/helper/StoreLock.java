package org.act.temporalProperty.helper;

import org.act.temporalProperty.exception.StoreShutdownException;
import org.act.temporalProperty.exception.TPSRuntimeException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by song on 2018-04-16.
 */
public class StoreLock{
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Condition mergeDone = lock.writeLock().newCondition();
    private boolean systemDown = false;

    public void shutdown(){
        this.systemDown = true;
    }

    public void lockExclusive(){
        lock.writeLock().lock();
        if(systemDown){
            throw new StoreShutdownException();
        }// else return;
    }

    public void unlockExclusive(){
        lock.writeLock().unlock();
    }

    public void mergeLockExclusive(){
        lock.writeLock().lock();
    }

    public void mergeUnlockExclusive(){
        lock.writeLock().unlock();
    }

    public void lockShared(){
        lock.readLock().lock();
        if(systemDown){
            throw new StoreShutdownException();
        }
    }

    public void unlockShared() {
        lock.readLock().unlock();
    }

    public void waitMergeDoneRegardlessShutDown() throws InterruptedException {
        mergeDone.await();
    }

    public void waitMergeDone() throws InterruptedException {
        if(systemDown){
            throw new StoreShutdownException();
        }
        mergeDone.await();
        if(systemDown){
            throw new StoreShutdownException();
        }
    }

    public void mergeDone(){
        mergeDone.signalAll();
    }

    public void shutdownLockExclusive()
    {
        lock.writeLock().lock();
    }
}
