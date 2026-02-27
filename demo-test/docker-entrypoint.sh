#!/bin/bash

# default
function start_db_server() {
  REQUIRED_ENV_VARS=("DB_PATH" "CLASS_SERVER" "DB_PORT")
  if [ -z ${CLASS_SERVER+x} ]
  then
      echo 'Error: env var CLASS_SERVER is not set'
      exit 1
  else
      cd /db/bin/demo-test
      mvn -B --offline exec:java -Dexec.cleanupDaemonThreads=false -Dexec.mainClass=$CLASS_SERVER
  fi
}

function start_db_client() {
  cd /db/bin/demo-test
  mvn -B --offline compile exec:java -Dexec.mainClass=edu.buaa.common.benchmark.BenchmarkRunner -Dexec.cleanupDaemonThreads=false
}

function build_milestone() {
  cd /db/bin/demo-test
  mvn -B --offline exec:java -Dexec.mainClass=edu.buaa.common.benchmark.MilestoneBuilder -Dexec.cleanupDaemonThreads=false
}

function send_db_space_usage2server() {
  cd /db/bin/demo-test
  mvn -B --offline exec:java -Dexec.mainClass=edu.buaa.utils.DBSpaceStatistic -Dexec.cleanupDaemonThreads=false
}

# system info of current machine (both hardware and software), no argument needed.
function print_system_info() {
    if [ -z ${IS_COMPILED+x} ]
    then
        IS_COMPILED=' clean compile '
    else
        IS_COMPILED=''
    fi
    cd /db/bin/demo-test
    mvn -B ${IS_COMPILED} exec:java -Dexec.mainClass="edu.buaa.client.RuntimeEnv"
}

function git_pull_tps() {
  cd /db/bin/temporal-storage
  git pull --ff-only
  mvn -B -Dmaven.test.skip clean install
}

function git_pull_neo4j() {
  cd /db/bin/temporal-neo4j
  git pull --ff-only
  mvn -B -Dmaven.test.skip -Dlicense.skip -Dlicensing.skip -Dcheckstyle.skip -Doverwrite clean install -pl org.neo4j:neo4j-kernel -am
}

function git_pull_test() {
  cd /db/bin/demo-test
  git diff
  git reset --hard HEAD     # reset local changes.
  git clean -f -d
  git pull --ff-only
  mvn -B -Dmaven.test.skip clean compile
}

# 处理命令行参数
main() {
    # 如果没有参数，执行默认函数
    if [ $# -eq 0 ]; then
        start_db_server
    else
        # 遍历所有参数并执行对应的函数
        for arg in "$@"; do
            if [ "$(type -t "$arg")" = "function" ]; then
                $arg  # 调用对应函数
            else
                echo "Warning: Bash function '$arg' undefined, use args as cmd."
                $arg
#                exit 1
            fi
        done
    fi
}

# 调用主函数并传递所有参数
main "$@"




## Function: Start TGraph TCP Server which accept TCypher queries.
## Example: tcypherServerStart path-to-db-dir
## Explain: path-to-db-dir is a TGraph DB folder which contains traffic demo road network topology
#function runTCypherServer() {
#    mvn -B clean compile exec:java \
#        -Dexec.mainClass="simple.TCypherSocketServer" \3
#        -Dexec.classpathScope="test" \
#        -Dexec.args="$1"
#}
#
#function runNeo4j1KernelServer(){
#  export DB_PATH="D:\tgraph\test\energy\neo4j1"
#  export DB_PATH="G:\0.5Y-neo4j1"
#  export DB_PATH="G:\3Y-neo4j1"
#  mvn -B -o compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.mainClass="edu.buaa.energy.server.Neo4jServer1"
#}
#
#function runNeo4j2KernelServer(){
#  export DB_PATH="D:\tgraph\test\energy\neo4j2"
#  export DB_PATH="G:\0.5Y-neo4j2"
#  export DB_PATH="G:\3Y-neo4j2"
#  mvn -B -o compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.mainClass="edu.buaa.energy.server.Neo4jServer2"
#}
#
#function closeServer(){
#  echo EXIT | nc localhost 8438
#}
#
#function compileKernel(){
#  mvn -B -o install -pl org.neo4j:neo4j-kernel -Dlicense.skip -Dlicensing.skip -Dmaven.test.skip
#}
#
#function compileStorage(){
#  mvn -B -o install -Dmaven.test.skip
#}
##
##function runNeo4jKernelServer(){
##  export DB_PATH="E:\tgraph\test-db"
##  export DB_TYPE=array
##  export DB_TYPE=treemap
##  mvn -B --offline compile exec:java -Dexec.mainClass="edu.buaa.server.Neo4jKernelTcpServer"
##}
#
#function postgreBackup(){
#  # https://www.postgresql.org/docs/13/app-pgdump.html
#  # -h localhost -p 5432
#  pg_dump.exe -Upostgres --format=c --blobs --file=D:\tgraph\test\energy\milestone\pg.dump europe_energy
#}
#
#function postgreRestore(){
#  # https://www.postgresql.org/docs/13/app-pgrestore.html
#  dropdb.exe -Upostgres -W europe_energy
#  createdb.exe -Upostgres -W europe_energy
#  pg_restore.exe -Upostgres -e -d europe_energy D:\tgraph\test\energy\milestone\pg.dump
#}
#
#function mariaBackup(){
#  mariabackup.exe --backup --target-dir=D:\tgraph\test\energy\milestone\mariadb --user=root --password=langduhua --databases="re_europe"
#}
#
#function mariaRestore(){
#  mariabackup.exe --prepare --target-dir=D:\tgraph\test\energy\milestone\mariadb --user=root
#  # 还原数据（删除datadir目录中与之冲突的文件即可）
#  net stop MariaDB
#  del ..\data\aria_log*
#  del ..\data\ib*
#  del ..\data\x*
#  del ..\data\re_europe\*
#  rmdir ..\data\re_europe
#  mariabackup.exe --copy-back --force-non-empty-directories --target-dir=D:\tgraph\test\energy\milestone\mariadb --user=root
#  net start MariaDB
#  # 修改属组属主
#  # chown -R mysql:mysql /var/lib/mysql
#}
#
##======================================== TESTS without INDEX ========================================
#
#
#function runWriteTest() {
#  export TEMPORAL_DATA_PER_TX=100
#  export TEMPORAL_DATA_START=0501
#  export TEMPORAL_DATA_END=0501
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\test-data"
#  export MAX_CONNECTION_CNT=1
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.WriteTemporalPropertyTest
#}
#
#function runSnapshotTest() {
#  export TEST_PROPERTY_NAME=travel_time
#  export TEMPORAL_DATA_START=201006300940
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export MAX_CONNECTION_CNT=16
#  export SERVER_RESULT_FILE="Result_SnapshotTest.gz"
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.SnapshotTest
#}
#
#function runSnapshotAggregationMaxTest() {
#  export TEST_PROPERTY_NAME=travel_time
#  export TEMPORAL_DATA_START=201006300830
#  export TEMPORAL_DATA_END=201006300930
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_SnapshotAggregationMaxTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.SnapshotAggregationMaxTest
#}
#
#function runSnapshotAggregationDurationTest() {
#  export TEST_PROPERTY_NAME=full_status
#  export TEMPORAL_DATA_START=201006300830
#  export TEMPORAL_DATA_END=201006300930
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_SnapshotAggregationDurationTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.SnapshotAggregationDurationTest
#}
#
#function runEntityTemporalConditionTest() {
#  export TEST_PROPERTY_NAME=travel_time
#  export TEMPORAL_DATA_START=201006300830
#  export TEMPORAL_DATA_END=201006300930
#  export TEMPORAL_CONDITION=600
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_EntityTemporalConditionTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.EntityTemporalConditionTest
#}
#
#
#function runReachableAreaQueryTest() {
#  export TEST_START_CROSS_ID=75124
#  export TEMPORAL_DATA_START=201006300830
#  export TRAVEL_TIME=50000
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_EntityTemporalConditionTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.ReachableAreaQueryTest
#}
#
#
##=====================================================================================================
#
#
##========================================== TESTS with INDEX =========================================
#
#function runTGraphIndexedKernelServer(){
# # export DB_PATH="E:\tgraph\test-db\tgraph"
#  export DB_PATH="C:\tgraph\test-db\tgraph1d"
#  mvn -B --offline compile exec:java -Dexec.mainClass="edu.buaa.server.TGraphIndexedKernelServer"
#}
#
#function runCreateAggrMaxIndex() {
#  export INDEX_PROPERTY_NAME=travel_time
#  export INDEX_TEMPORAL_DATA_START=201005010900
#  export INDEX_TEMPORAL_DATA_END=201005011900
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="ID_CreateAggrMaxIndexTest.gz"
#  export MAX_CONNECTION_CNT=1
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.index.CreateTGraphAggrMaxIndexTest
#}
#
#function runSnapshotAggregationMaxIndexTest() {
#  export INDEX_ID=8
#  export TEST_PROPERTY_NAME=travel_time
#  export TEMPORAL_DATA_START=201006300830
#  export TEMPORAL_DATA_END=201006300930
##  export INDEX_PROPERTY_NAME=travel_time
##  export INDEX_TEMPORAL_DATA_START=201006290000
##  export INDEX_TEMPORAL_DATA_END=201006300000
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_SnapshotAggregationMaxIndexTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.index.SnapshotAggregationMaxIndexTest
#}
#
#function runCreateAggrDurationIndex {
##  export INDEX_PROPERTY_NAME=travel_time
#  export INDEX_TEMPORAL_DATA_START=201006290000
#  export INDEX_TEMPORAL_DATA_END=201006300000
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="ID_CreateAggrDurationIndexTest.gz"
#  export MAX_CONNECTION_CNT=1
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.index.CreateTGraphAggrDurationIndexTest
#}
#
#function runSnapshotAggregationDurationIndexTest() {
#  export TEST_PROPERTY_NAME=full_status
#  export TEMPORAL_DATA_START=201006300830
#  export TEMPORAL_DATA_END=201006300930
##  export INDEX_TEMPORAL_DATA_START=201006290000
##  export INDEX_TEMPORAL_DATA_END=201006300000
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_SnapshotAggregationDurationIndexTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.SnapshotAggregationDurationIndexTest
#}
#
#function runEntityTemporalConditionIndexTest() {
#  export TEST_PROPERTY_NAME=travel_time
#  export TEMPORAL_DATA_START=201006300830
#  export TEMPORAL_DATA_END=201006300930
#  export TEMPORAL_CONDITION=600
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_EntityTemporalConditionTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.EntityTemporalConditionTest
#}



#function runReachableAreaQueryTest() {
#  export TEST_START_CROSS_ID=75124
#  export TEMPORAL_DATA_START=201006300830
#  export TRAVEL_TIME=50000
#  export DB_HOST=localhost
#  export RAW_DATA_PATH="E:\tgraph\test-result"
#  export SERVER_RESULT_FILE="Result_EntityTemporalConditionTest.gz"
#  export MAX_CONNECTION_CNT=16
#  export VERIFY_RESULT=false
#  mvn -B --offline test -Dtest=simple.tgraph.kernel.ReachableAreaQueryTest
#}

## Function: Test TGraph TCypher Server write performance.
## Example: tcypherClientWriteTest /media/song/test/db-network-only-ro 192.168.1.141 8 10 200000 /media/song/test/data-set/beijing-traffic/TGraph/byday/100501
## Explain:
##  /media/song/test/db-network-only-ro is a TGraph DB folder which contains traffic demo road network topology
##  192.168.1.141  is the TCypher Server hostname
##  8 is the number of connections to the server(both server and client use one thread to process one connection
##  10 is the number of Cypher queries per transaction
##  200000 is the total number of data lines to send.(from the data file)
##  /media/song/test/data-set/beijing-traffic/TGraph/byday/100501 is the path of the data file
#function tcypherClientWriteSpropTest() {
#    if [ -z ${IS_COMPILED+x} ]
#    then
#        IS_COMPILED=' clean test-compile '
#    else
#        IS_COMPILED=''
#    fi
#    mvn -B ${IS_COMPILED} exec:java \
#        -Dexec.mainClass="org.act.temporal.test.tcypher.WriteStaticPropertyTest" \
#        -Dexec.classpathScope="test" \
#        -Dexec.args="$1 $2 $3 $4 $5 $6"
#}
#
#
#
#function genBenchmark() {
#  export WORK_DIR="E:"
#  export BENCHMARK_FILE_OUTPUT=benchmark
#  export TEMPORAL_DATA_PER_TX=100
#  export TEMPORAL_DATA_START=0503
#  export TEMPORAL_DATA_END=0504
#  export REACHABLE_AREA_TX_CNT=20
#  mvn -B --offline compile exec:java -Dexec.mainClass="edu.buaa.traffic.TrafficTxGenerator"
#}
#
#function genResult() {
#  export BENCHMARK_FILE_INPUT="E:\tgraph\test-data\benchmark.gz"
#  export BENCHMARK_FILE_OUTPUT="E:\tgraph\test-data\benchmark-with-result.gz"
#  export REACHABLE_AREA_REPLACE_TX=true
#  mvn -B --offline compile exec:java -Dexec.mainClass="edu.buaa.traffic.BenchmarkTxResultGenerator"
#}
#
#function runBenchmark() {
#  export DB_TYPE=tgraph_kernel
#  #export DB_TYPE=sql_server
#  #export DB_HOST=39.96.57.88
#  export DB_HOST=localhost
#  export BENCHMARK_FILE_INPUT="E:\tgraph\test-data\benchmark-with-result.gz"
#  export MAX_CONNECTION_CNT=1
#  export VERIFY_RESULT=true
#  mvn -B --offline compile exec:java -Dexec.mainClass="edu.buaa.traffic.BenchmarkRunner"
#}