package edu.buaa.common.transaction;

public class TransactionFailedException extends RuntimeException {
    public TransactionFailedException(Throwable e) {
        super(e);
    }

    private AbstractTransaction tx = null;

    public TransactionFailedException(Throwable e, AbstractTransaction tx) {
        super(e);
        this.tx = tx;
    }

    public TransactionFailedException() {
    }

    public AbstractTransaction getTx() {
        return tx;
    }
}
