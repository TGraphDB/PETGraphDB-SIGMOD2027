package org.act.temporalProperty.index.value.cardinality;


/*
 * copied from https://github.com/addthis/stream-lib/blob/master/src/main/java/com/clearspring/analytics/stream/cardinality/HyperLogLog.java
 */


import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.io.Serializable;
//
//        import com.clearspring.analytics.hash.MurmurHash;
//        import com.clearspring.analytics.util.Bits;
//        import com.clearspring.analytics.util.IBuilder;

/**
 * Java implementation of HyperLogLog (HLL) algorithm from this paper:
 * HyperLogLog: the analysis of a near-optimal cardinality estimation algorithm
 * http://algo.inria.fr/flajolet/Publications/FlFuGaMe07.pdf
 * <p/>
 * HLL is an improved version of LogLog that is capable of estimating
 * the cardinality of a set with accuracy = 1.04/sqrt(m) where
 * m = 2^b.  So we can control accuracy vs space usage by increasing
 * or decreasing b.
 * <p/>
 * The main benefit of using HLL over LL is that it only requires 64%
 * of the space that LL does to get the same accuracy.
 * <p/>
 * This implementation implements a single counter.  If a large (millions)
 * number of counters are required you may want to refer to:
 * <p/>
 * http://dsiutils.di.unimi.it/
 * <p/>
 * It has a more complex implementation of HLL that supports multiple counters
 * in a single object, drastically reducing the java overhead from creating
 * a large number of objects.
 * <p/>
 * This implementation leveraged a javascript implementation that Yammer has
 * been working on:
 * <p/>
 * https://github.com/yammer/probablyjs
 * <p>
 * Note that this implementation does not include the long range correction function
 * defined in the original paper.  Empirical evidence shows that the correction
 * function causes more harm than good.
 * </p>
 * <p/>
 * <p>
 * Users have different motivations to use different types of hashing functions.
 * Rather than try to keep up with all available hash functions and to remove
 * the concern of causing future binary incompatibilities this class allows clients
 * to offer the value in hashed int or long form.  This way clients are free
 * to change their hash function on their own time line.  We recommend using Google's
 * Guava Murmur3_128 implementation as it provides good performance and speed when
 * high precision is required.  In our tests the 32bit MurmurHash function included
 * in this project is faster and produces better results than the 32 bit murmur3
 * implementation google provides.
 * </p>
 */
public class HyperLogLog{

    public static HyperLogLog defaultBuilder(){
        return new HyperLogLog(6);
    }

    private final RegisterSet registerSet;
    private final int log2m;
    private final double alphaMM;

    /**
     * Create a new HyperLogLog instance.  The log2m parameter defines the accuracy of
     * the counter.  The larger the log2m the better the accuracy.
     * <p/>
     * accuracy = 1 - 1.04/sqrt(2^log2m)
     *
     * @param log2m - the number of bits to use as the basis for the HLL instance
     */
    public HyperLogLog(int log2m) {
        this(log2m, new RegisterSet(1 << log2m));
    }

    /**
     * Creates a new HyperLogLog instance using the given registers.
     * Used for unmarshalling a serialized
     * instance and for merging multiple counters together.
     *
     * @param registerSet - the initial values for the register set
     */
    private HyperLogLog(int log2m, RegisterSet registerSet) {
        if (log2m < 0 || log2m > 30) throw new IllegalArgumentException("log2m " + log2m + " outside the range [0, 30]");
        this.registerSet = registerSet;
        this.log2m = log2m;
        int m = 1 << this.log2m;

        alphaMM = getAlphaMM(log2m, m);
    }

    public boolean offerHashed(int hashedValue) {
        // j becomes the binary address determined by the first b log2m of x
        // j will be between 0 and 2^log2m
        final int j = hashedValue >>> (Integer.SIZE - log2m);
        final int r = Integer.numberOfLeadingZeros((hashedValue << this.log2m) | (1 << (this.log2m - 1)) + 1) + 1;
        return registerSet.updateIfGreater(j, r);
    }

    public boolean offer(long o) {
        final int x = MurmurHash.hashLong(o);
        return offerHashed(x);
    }


    public long cardinality() {
        double registerSum = 0;
        int count = registerSet.count;
        double zeros = 0.0;
        for (int j = 0; j < registerSet.count; j++) {
            int val = registerSet.get(j);
            registerSum += 1.0 / (1 << val);
            if (val == 0) {
                zeros++;
            }
        }

        double estimate = alphaMM * (1 / registerSum);

        if (estimate <= (5.0 / 2.0) * count) {
            // Small Range Estimate
            return Math.round(linearCounting(count, zeros));
        } else {
            return Math.round(estimate);
        }
    }

    public int sizeof() {
        return registerSet.size * 4;
    }

    /**
     * Add all the elements of the other set to this set.
     * <p/>
     * This operation does not imply a loss of precision.
     *
     * @param other A compatible Hyperloglog instance (same log2m)
     * @throws CardinalityMergeException if other is not compatible
     */
    public void addAll(HyperLogLog other) throws CardinalityMergeException {
        if (this.sizeof() != other.sizeof()) {
            throw new CardinalityMergeException("Cannot merge estimators of different sizes");
        }

        registerSet.merge(other.registerSet);
    }

    public HyperLogLog merge(HyperLogLog... estimators) throws CardinalityMergeException {
        HyperLogLog merged = new HyperLogLog(log2m, new RegisterSet(this.registerSet.count));
        merged.addAll(this);

        if (estimators == null) {
            return merged;
        }

        for (HyperLogLog estimator : estimators) {
            merged.addAll(estimator);
        }

        return merged;
    }


    public void encode(SliceOutput o){
        o.writeInt(log2m);
        o.writeInt(registerSet.size);
        for (int x : registerSet.readOnlyBits()) {
            o.writeInt(x);
        }
    }

    public static HyperLogLog decode(SliceInput in){
        int log2m = in.readInt();
        int size = in.readInt();
        int[] M = new int[size];
        for(int i=0; i<size; i++){
            M[i] = in.readInt();
        }
        return new HyperLogLog(log2m, new RegisterSet(1 << log2m, M));
    }

    private static class CardinalityMergeException extends RuntimeException {
        public CardinalityMergeException(String message) {
            super(message);
        }
    }

    protected static double getAlphaMM(final int p, final int m) {
        // See the paper.
        switch (p) {
            case 4:
                return 0.673 * m * m;
            case 5:
                return 0.697 * m * m;
            case 6:
                return 0.709 * m * m;
            default:
                return (0.7213 / (1 + 1.079 / m)) * m * m;
        }
    }

    protected static double linearCounting(int m, double V) {
        return m * Math.log(m / V);
    }


    private static class RegisterSet {

        public final static int LOG2_BITS_PER_WORD = 6;
        public final static int REGISTER_SIZE = 5;

        public final int count;
        public final int size;

        private final int[] M;

        public RegisterSet(int count) {
            this(count, null);
        }

        public RegisterSet(int count, int[] initialValues) {
            this.count = count;

            if (initialValues == null) {
                this.M = new int[getSizeForCount(count)];
            } else {
                this.M = initialValues;
            }
            this.size = this.M.length;
        }

        public static int getBits(int count) {
            return count / LOG2_BITS_PER_WORD;
        }

        public static int getSizeForCount(int count) {
            int bits = getBits(count);
            if (bits == 0) {
                return 1;
            } else if (bits % Integer.SIZE == 0) {
                return bits;
            } else {
                return bits + 1;
            }
        }

        public void set(int position, int value) {
            int bucketPos = position / LOG2_BITS_PER_WORD;
            int shift = REGISTER_SIZE * (position - (bucketPos * LOG2_BITS_PER_WORD));
            this.M[bucketPos] = (this.M[bucketPos] & ~(0x1f << shift)) | (value << shift);
        }

        public int get(int position) {
            int bucketPos = position / LOG2_BITS_PER_WORD;
            int shift = REGISTER_SIZE * (position - (bucketPos * LOG2_BITS_PER_WORD));
            return (this.M[bucketPos] & (0x1f << shift)) >>> shift;
        }

        public boolean updateIfGreater(int position, int value) {
            int bucket = position / LOG2_BITS_PER_WORD;
            int shift = REGISTER_SIZE * (position - (bucket * LOG2_BITS_PER_WORD));
            int mask = 0x1f << shift;

            // Use long to avoid sign issues with the left-most shift
            long curVal = this.M[bucket] & mask;
            long newVal = value << shift;
            if (curVal < newVal) {
                this.M[bucket] = (int) ((this.M[bucket] & ~mask) | newVal);
                return true;
            } else {
                return false;
            }
        }

        public void merge(RegisterSet that) {
            for (int bucket = 0; bucket < M.length; bucket++) {
                int word = 0;
                for (int j = 0; j < LOG2_BITS_PER_WORD; j++) {
                    int mask = 0x1f << (REGISTER_SIZE * j);

                    int thisVal = (this.M[bucket] & mask);
                    int thatVal = (that.M[bucket] & mask);
                    word |= (thisVal < thatVal) ? thatVal : thisVal;
                }
                this.M[bucket] = word;
            }
        }

        int[] readOnlyBits() {
            return M;
        }
    }



    /**
     * 一种快速的非加密hash
     * 适用于对保密性要求不高以及不在意hash碰撞攻击的场合
     */
    private static class MurmurHash {

        static int hashLong(long data) {
            int m = 0x5bd1e995;
            int r = 24;

            int h = 0;

            int k = (int) data * m;
            k ^= k >>> r;
            h ^= k * m;

            k = (int) (data >> 32) * m;
            k ^= k >>> r;
            h *= m;
            h ^= k * m;

            h ^= h >>> 13;
            h *= m;
            h ^= h >>> 15;

            return h;
        }
    }
}