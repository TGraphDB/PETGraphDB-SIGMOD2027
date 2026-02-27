package edu.buaa.common.transaction;

public class RequestProcessResult extends AbstractTransaction.Result {
    private long lockWait = -1;

    public long getLockWait() {
        return lockWait;
    }

    public void setLockWait(long lockWait) {
        this.lockWait = lockWait;
    }
}
