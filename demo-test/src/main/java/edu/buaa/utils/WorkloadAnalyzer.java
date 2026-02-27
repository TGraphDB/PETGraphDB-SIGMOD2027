package edu.buaa.utils;

import edu.buaa.common.benchmark.BenchmarkReader;
import edu.buaa.common.transaction.AbstractTransaction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class WorkloadAnalyzer {

    public static void main(String[] args) throws IOException {
        File benchmarkFile = new File("C:\\Users\\26944\\Desktop\\benchmark\\b_energy_T.all_htap_100000\\benchmark.json");
        boolean bigLock = false;
        boolean readLock = false;
        BenchmarkReader reader = new BenchmarkReader(benchmarkFile, true);
        ArrayList<AbstractTransaction> transactions = new ArrayList<>();
        while (reader.hasNext()) {
            transactions.add(reader.next());
        }
        System.out.println(analyze(transactions, bigLock, readLock));
    }

    private static String analyze(ArrayList<AbstractTransaction> transactions, boolean bigLock, boolean readLock) {
        // 单独计算两个append事务之间的冲突数量和总数量
        long allBetweenAppend = 0;
        long allExceptBetweenAppend = 0;
        long conflictBetweenAppend = 0;
        long conflictExceptBetweenAppend = 0;
        long unknownExceptBetweenAppend = 0;
        long transactionNum = transactions.size();
        long totalCompare = transactionNum * (transactionNum - 1) / 2;
        long cnt = 0;
        for (int i = 0; i < transactions.size(); i++) {
            for (int j = 1; j < transactions.size(); j++) {
                AbstractTransaction transaction1 = transactions.get(i);
                AbstractTransaction transaction2 = transactions.get(j);
                AbstractTransaction.ConflictType type = AbstractTransaction.isConflict(transaction1, transaction2, bigLock, readLock);
                if (transaction1.getTxType() == AbstractTransaction.TxType.tx_import_temporal_data &&
                transaction2.getTxType() == AbstractTransaction.TxType.tx_import_temporal_data) {
                    allBetweenAppend++;
                    if (type == AbstractTransaction.ConflictType.TRUE) {
                        conflictBetweenAppend++;
                    }
                }
                else {
                    allExceptBetweenAppend++;
                    if (type == AbstractTransaction.ConflictType.TRUE) {
                        conflictExceptBetweenAppend++;
                    }
                    else if (type == AbstractTransaction.ConflictType.UNKNOWN) {
                        unknownExceptBetweenAppend++;
                    }
                }
                cnt++;
                if (cnt % 10000 == 0) {
                    System.out.println("compare finished: " + cnt + "/" + totalCompare);
                }
            }
        }
        return "conflict analysis: \n" +
                "relationship num between 2 append transactions: " + allBetweenAppend + ", " +
                "conflict num: " + conflictBetweenAppend + ", " +
                "conflict rate: " + (((double) conflictBetweenAppend) / ((double) allBetweenAppend)) + "\n" +
                "other valid relationship num between 2 transactions: " + allExceptBetweenAppend + ", " +
                "conflict num: " + conflictExceptBetweenAppend + ", " +
                "conflict rate: " + (((double) conflictExceptBetweenAppend) / ((double) allExceptBetweenAppend)) + ", " +
                "unknown num: " + unknownExceptBetweenAppend + ", " +
                "unknown rate: " + (((double) unknownExceptBetweenAppend) / ((double) allExceptBetweenAppend)) + "\n";
    }
}
