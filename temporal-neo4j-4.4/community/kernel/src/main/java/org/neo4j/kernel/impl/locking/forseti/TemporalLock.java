package org.neo4j.kernel.impl.locking.forseti;

import org.neo4j.internal.helpers.collection.Pair;

import java.util.ArrayList;
import java.util.Set;

// be caution not to generate deadlock on the objects of this class
public class TemporalLock {
    private final ArrayList<Pair<Long, Long>> sharedTimeRange, upgradingTimeRange, exclusiveTimeRange;
    private final ForsetiClient owner;

    public TemporalLock(ForsetiClient owner) {
        this.owner = owner;
        this.sharedTimeRange = new ArrayList<>();
        this.upgradingTimeRange = new ArrayList<>();
        this.exclusiveTimeRange = new ArrayList<>();
    }

    public void lockShared(long start, long end) {
        addToInterval(start, end, sharedTimeRange);
    }

    public void upgradeShared(long start, long end) {
        addToInterval(start, end, upgradingTimeRange);
    }

    public void lockExclusive(long start, long end) {
        addToInterval(start, end, exclusiveTimeRange);
    }

    public boolean sharedConflict(long start, long end) {
        if (intersect(start, end, upgradingTimeRange)) return true;
        return intersect(start, end, exclusiveTimeRange);
    }

    public boolean exclusiveConflict(long start, long end, boolean sharedNotConflictDetected) {
        if (intersect(start, end, sharedTimeRange)) return true;
        if (sharedNotConflictDetected) return false;
        if (intersect(start, end, upgradingTimeRange)) return true;
        return intersect(start, end, exclusiveTimeRange);
    }

    public boolean sharedLocked(long start, long end) {
        return covered(start, end, sharedTimeRange);
    }

    public boolean upgradeLocked(long start, long end) {
        return covered(start, end, upgradingTimeRange);
    }

    public boolean exclusiveLocked(long start, long end) {
        return covered(start, end, exclusiveTimeRange);
    }

    // used for merging a new interval to merged and sorted intervals and keep them merged and sorted
    private void addToInterval(long start, long end, ArrayList<Pair<Long, Long>> intervals) {
        if (start > end) throw new RuntimeException("start time is larger than end time!");
        if (intervals.size() == 0) {
            Pair<Long, Long> newInterval = Pair.pair(start, end);
            intervals.add(newInterval);
            return;
        }
        long newStart = start, newEnd = end;
        for (Pair<Long, Long> interval : intervals) {
            if (newStart < interval.first()) {
                break;
            }
            if (newStart <= interval.other()) {
                newStart = interval.first();
                break;
            }
        }
        for (Pair<Long, Long> interval : intervals) {
            if (newEnd < interval.first()) {
                break;
            }
            if (newEnd <= interval.other()) {
                newEnd = interval.other();
                break;
            }
        }
        long finalNewStart = newStart;
        long finalNewEnd = newEnd;
        Pair<Long, Long> newInterval = Pair.pair(newStart, newEnd);
        intervals.removeIf(interval -> interval.first() >= finalNewStart && interval.other() <= finalNewEnd);
        // now intervals and newInterval are not intersect with each other, just insert newInterval to a correct place
        boolean inserted = false;
        for (int i = 0; i < intervals.size(); i++) {
            if (intervals.get(i).first() > newEnd) {
                intervals.add(i, newInterval);
                inserted = true;
                break;
            }
        }
        if (!inserted) {
            intervals.add(newInterval);
        }
    }

    // check whether intervals intersect with the given range
    private boolean intersect(long start, long end, ArrayList<Pair<Long, Long>> intervals) {
        for (Pair<Long, Long> interval : intervals) {
            if (start > interval.other()) continue;
            if (end < interval.first()) break;
            return true;
        }
        return false;
    }

    // check whether intervals cover the given range
    private boolean covered(long start, long end, ArrayList<Pair<Long, Long>> intervals) {
        for (Pair<Long, Long> interval : intervals) {
            if (start > interval.other()) continue;
            if (end < interval.first()) break;
            if (start >= interval.first() && end <= interval.other()) return true;
        }
        return false;
    }

    public ForsetiClient getOwner() {
        return owner;
    }
}
