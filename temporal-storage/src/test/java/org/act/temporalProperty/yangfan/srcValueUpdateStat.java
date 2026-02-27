package org.act.temporalProperty.yangfan;

import org.act.temporalProperty.index.value.rtree.IndexEntry;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.StoreBuilder;
import org.act.temporalProperty.util.TrafficDataImporter;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Fan Yang on 2018-5-29, last updated on 2018-5-29
 *
 * class description:
 * This class aims at making the statistics about whether the property values of two records are updated (i.e., different) or not.
 * For example: at 8:00 --- travelTime = 20, at 8:05 ---- travelTime = 25; at 8:00 --- travelTime = 20, at 8:05 ---- travelTime = 20
 * Thus we can know that how many time update interval unaligned situations among multiple properties,
 * to analysis if it can be accelerated (the aligned situations can be accelerated) based on our test set.
 *
 * class usage:
 */

public class srcValueUpdateStat {

    private dataFileImporter fileImporter = new dataFileImporter(1000);
    private List<IndexEntry> entryList = null;
    private static int propertyNum = 4;
    private int[] sameValueCount = {0, 0, 0, 0};
    private List<List<IndexEntry>> proEntryList = new ArrayList<>();

    private Slice[] getPropertyValues (int propertyId, Slice value) {

        switch (propertyId) {

            case 0:
                return new Slice[] {value, null, null, null};
            case 1:
                return new Slice[] {null, value, null, null};
            case 2:
                return new Slice[] {null, null, value, null};
            case 3:
                return new Slice[] {null, null, null, value};
        }

        return null;
    }

    @Test
    public void calUnUpdated() {

        fileImporter.prepareData();
        entryList = fileImporter.getEntryList();

        IndexEntry currEntry;
        IndexEntry nextEntry;

        for (int propertyId = 0; propertyId < propertyNum; propertyId++) {

            List<IndexEntry> plist = new ArrayList<>();
            proEntryList.add(plist);

            for (int entryId = 0; entryId < entryList.size(); entryId++) {

                currEntry = entryList.get(entryId);
                long entityId = currEntry.getEntityId();
                Slice propertyValue = currEntry.getValue(propertyId);
                TimePointL startTime = currEntry.getStart();
                TimePointL endTime = currEntry.getEnd();

                while (true) {

                    if (entryId + 1 == entryList.size()) {

                        plist.add(new IndexEntry(entityId, startTime, endTime, getPropertyValues(propertyId, propertyValue)));
                        break;
                    }

                    nextEntry = entryList.get(entryId + 1);
                    if (!nextEntry.getEntityId().equals(currEntry.getEntityId())) {

                        break;
                    } else if (nextEntry.getValue(propertyId).equals(currEntry.getValue(propertyId))) {

                        sameValueCount[propertyId]++;
                        entryId++;
                    } else {

                        endTime = nextEntry.getStart().pre();
                        break;
                    }
                }

                plist.add(new IndexEntry(entityId, startTime, endTime, getPropertyValues(propertyId, propertyValue)));
            }
        }

        System.out.println("p1SameNum = " + sameValueCount[0] + ", p2SameNum = " + sameValueCount[1] + ", p3SameNum = " + sameValueCount[2]
        + ", p4SameNum = " + sameValueCount[3] + ", totalNum = " + entryList.size());

        System.out.println("p1SamePercent = " + sameValueCount[0] * 1.0 / entryList.size() +
                ", p2SamePercent = " + sameValueCount[1] * 1.0 / entryList.size() +
                ", p3SamePercent = " + sameValueCount[2] * 1.0 / entryList.size() +
                ", p4SamePercent = " + sameValueCount[3] * 1.0 / entryList.size());
    }
}
