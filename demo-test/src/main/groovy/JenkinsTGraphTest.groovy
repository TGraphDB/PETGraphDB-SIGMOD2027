#!/usr/bin/env groovy
package main.groovy

class JenkinsTGraphTest implements Serializable {
//    def steps
//    static String foo = "bar"
//    JenkinsTGraphTest(steps) {this.steps = steps}
    static def envVars = [
            "master":[
                    'DIR_WORKSPACE': "G:\\jenkins-workspace",
                    'DIR_BUILD': "G:\\jenkins-workspace", //构建milestone的路径
                    'DIR_CODE': "G:\\jenkins-workspace", //代码路径
                    'DIR_ARTIFACT': "Y:",
                    'DIR_BENCHMARK': "D:\\tgraph\\jenkins",
                    'DIR_DATA': "Z:",
                    'MAVEN_OPTS': '-Xmx18g -Xms2g',
            ],
            "t630":[
                    'DIR_WORKSPACE': "/mnt/disk8t/tgraph/workspace", //运行测试的路径
                    'DIR_BUILD':     "/mnt/nvm2t/tgraph/build", //构建milestone的路径
                    'DIR_ARTIFACT':  "/mnt/nvm2t/tgraph/artifact",//保存milestone的路径
                    'DIR_BENCHMARK': "/mnt/nfs/benchmark", //这个工作站无法启动nas client连接到master
                    'DIR_DATA':      "/mnt/disk8t/tgraph/dataset",
                    'MAVEN_OPTS': '-Xmx48g -Xms4g',
                    'MAVEN_BUILD_OPTS': '-Xmx140g -Xms16g -XX:MaxDirectMemorySize=64g'
            ],
            "client":[
                    'DIR_BUILD':     "/mnt/nvm2t/tgraph/build", //构建milestone的路径
                    'DIR_ARTIFACT':  "/mnt/nvm2t/tgraph/artifact",//保存milestone的路径
                    'DIR_WORKSPACE': "/tgraphdb/workspace",
                    'DIR_BENCHMARK': "/tgraphdb/benchmark",
                    'DIR_DATA': "/tgraphdb/dataset",
                    'MAVEN_OPTS': '-Xmx28g -Xms4g',
                    'MAVEN_BUILD_OPTS': '-Xmx80g -Xms16g -XX:MaxDirectMemorySize=24g'
            ],
            "cloud":[
                    'DIR_BUILD':     "/root/jenkins/tgraph/build", //构建milestone的路径
                    'DIR_ARTIFACT':  "/root/jenkins/tgraph/artifact",//保存milestone的路径
                    'DIR_WORKSPACE': "/root/jenkins/tgraph/workspace",
                    'DIR_BENCHMARK': "/tgraphdb/benchmark",
                    'DIR_DATA': "/tgraphdb/dataset",
                    'MAVEN_OPTS': '-Xmx3g -Xms1g',
                    'MAVEN_BUILD_OPTS': '-Xmx3g -Xms1g -XX:MaxDirectMemorySize=2g'
            ],
    ]

    static def opt(String key, String node) {
        return envVars.get(node).get(key)
    }

    static def dir(String key, String node) {
        return envVars.get(node).get(key)
    }

    static def dir(String key, String node, String childFileName) {
        return dir(key, node) +'/'+childFileName
    }

    static def fExist(String folder, String fName){
        def f = new File(folder, fName)
        return f.exists()
    }

    static def fExist(String fullPath){
        def f = new File(fullPath)
        return f.exists()
    }

    static String serverPortForClient(portCfgStr){
        if(portCfgStr instanceof Integer) return portCfgStr
        String[] arr = portCfgStr.split(":")
        if(arr.length==2){
            return arr[0]
        }else{
            return portCfgStr
        }
    }

    static String dockerPortStr(portCfgStr){
        if(portCfgStr instanceof Integer) return "--network=host "
        String[] arr = portCfgStr.split(":")
        if(arr.length==2){
            return "-p " + portCfgStr + " "
        }else{
            return "--network=host "
        }
    }

    static String dockerEnvStr(map){
        def kvList = []
        map.each {k, v ->
            kvList.add('-e '+k+'="'+v+'"')
        }
        return kvList.join(' ')
    }

    static String dockerVolumeStr(dir_map, dir_list){
        def mountList = []
        dir_map.each {k, v ->
            mountList.add('-v '+k+':'+v+':rw,z')
        }
        for(int i=0; i<dir_list.size(); i++){
            mountList.add('-v '+dir_list[i]+':'+dir_list[i]+':rw,z')
        }
        return mountList.join(' ')
    }

    static String getCor(val_a, list_a, list_b){
        for(int i=0; i<list_a.size(); i++){
            if(list_a[i]==val_a){
                return list_b[i]
            }
        }
        throw new IllegalArgumentException("key not found for ${val_a} ${list_a} ${list_b}")
    }

    static String extractConf(Map conf, String dbname, String key){
        def kList = conf['key']
        def vList = conf[dbname]
        for(int i=0; i<kList.size(); i++){
            if(key == kList[i]) return vList[i]
        }
        return ''
    }

    static List<String> extendEnvList(Map conf, String dbname){
        def kList = conf['key']
        def vList = conf[dbname]
        def kvList = []
        for(int i=0; i<kList.size(); i++){
            String key = kList[i]
            kvList.add(key.toUpperCase()+'='+vList[i])
        }
        return kvList
    }

    static List<String> map2EnvList(Map<String, String> map){
        def kvList = []
        map.each {k, v ->
            kvList.add(k+'='+v)
        }
        return kvList
    }

    static void addDbSpecifiedEnv(Map<String, String> envMap, String milestoneName) {
        if (milestoneName.toLowerCase().contains("iotdb")) {
            envMap.put("dn_rpc_address", "0.0.0.0")
            envMap.put("MEMORY_SIZE", "80G")
        }
    }

    static String milestoneName(String dataset, String data_size, String db_name){
        String name = "m_${dataset}_${db_name}_${data_size}"
        return name.toLowerCase().replace("t_", "")
    }

    static List<String> extract(String milestoneName){
        String[] arr = milestoneName.split("_")
        return [arr[1], arr[2].toUpperCase(), 'T_'+arr[3]]
    }

    static String benchmarkName(String dataset, String data_size, int query_size, String query_dist){
        String name = "b_${dataset}_${data_size}_${query_dist}_${query_size}"
        return name.replace(" ", "")
    }

    static void printJavaMavenVersion(script){
        script.sh 'java -version'
        script.sh 'mvn -version'
    }

    static void buildMilestone(script, currentBuild){
        script.sh 'cd /db/bin/demo-test; git log -n 3; git status'
        script.timestamps{
            script.sh 'bash /db/bin/demo-test/docker-entrypoint.sh build_milestone'
        }
        try {
            script.sh "bash /db/bin/demo-test/docker-entrypoint.sh send_db_space_usage2server"
        } catch (Exception e) {
            currentBuild.result = 'UNSTABLE'
            script.error(e.message)
        }
    }

    static void runTestClient(script){
        script.timestamps{
            script.sh 'bash /db/bin/demo-test/docker-entrypoint.sh start_db_client'
        }
    }

    static void runTestServer(script){
        script.timestamps{
            script.sh 'bash /db/bin/demo-test/docker-entrypoint.sh start_db_server'
        }
    }

    static void deploy2hdd(script, env, dbtype, milestone_name){
        String mFullPath = "${env.DIR_ARTIFACT}/${milestone_name}"
        script.dir(env.DIR_WORKSPACE){
            if(dbtype.contains("tcypher")){
                String modifiedName = milestone_name.replace("tgk", "tgl")
                script.sh "rm -rf ${modifiedName}" //删除现有数据库
                script.sh "7z x ${mFullPath}.7z -aoa -otgl"
                script.sh "mv tgl/${milestone_name} ${modifiedName}"
                script.sh "du -h ${modifiedName}"
            }else {
                script.sh "rm -rf ${milestone_name}" //删除现有数据库
                List<String> arr = extract(milestone_name)
                String dataset = arr[0]
                String db = arr[1].toLowerCase()
                String size = arr[2]
                if (db != dbtype){
                    String brotherName = milestoneName(dataset, size, dbtype)
                    String brotherPath = "${env.DIR_ARTIFACT}/${brotherName}"
                    script.sh "mkdir -p tg_plus"
                    script.sh "rm -rf tg_plus/${brotherName}" //删除现有数据库
                    script.sh "7z x ${brotherPath}.7z -aoa -otg_plus"
                    script.sh "mv tg_plus/${brotherName} ${milestone_name}"
                } else {
                    script.sh "7z x ${mFullPath}.7z -aoa" //解压数据库
                }
                script.sh "du -h ${milestone_name}"
            }
        }
    }

    static boolean needCompile(currentBuild){
        // return true if triggered by user manually
        return currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause').size() > 0
    }


    static def dropDB(script, String dbName, env){
        script.dir(env.DIR_BUILD){
            script.sh "rm -rf ${dbName}"
        }
        if(env.DIR_NEO4J_LOG!=null && fExist(env.DIR_NEO4J_LOG)) script.dir(env.DIR_NEO4J_LOG){
            script.sh "rm -rf neostore.transaction.db.*"
        }
    }


    static def archiveMilestone(self, String dbtype, String milestone_name, env){
        String milestone_full_path = "${env.DIR_ARTIFACT}/${milestone_name}"
        switch (dbtype) {
            case 'postgresql':
                self.bat "pg_dump.exe -Upostgres --format=c --blobs --file=${milestone_full_path} ${milestone_name}"
                break
            case 'mariadb':
                break //MariaDB的备份恢复不稳定容易导致数据库崩溃无法使用，故每次还是不备份了，直接重新导入比较好
                self.dir(env.DIR_BUILD) {
                    self.bat "if exist ${milestone_name}_d ( rmdir /q /s ${milestone_name}_d )"
                    self.bat "mkdir ${milestone_name}_d"
                    self.bat "mariabackup.exe --backup --target-dir=${milestone_name}_d --user=root --password=langduhua --databases=${milestone_name}"
                    self.zip dir: "${milestone_name}_d", zipFile: "${milestone_full_path}", overwrite: true, archive: false
                    self.bat "rmdir /q /s ${milestone_name}_d"
                }
                break
            default:
                self.echo "archive type: ${dbtype}"
                self.dir(env.DIR_BUILD){
                    self.sh "rm -f ${milestone_full_path}.7z"
                    self.sh "7z a -mmt16 -mx2 ${milestone_full_path}.7z ${milestone_name}"
                }
        }
    }

    static def codeSyncAndCompile(script, lstStr, clean=false){
        script.sh 'git config --global user.email "docker@edu.buaa.cn"'
        script.sh 'git config --global user.name "TestAgentDockerContainer"'
        script.sshagent(credentials: ['sjh']){
            def lst = lstStr.split(',')
            if('tp' in lst) {
                script.sh 'bash /db/bin/demo-test/docker-entrypoint.sh git_pull_tps'
            }
            if('neo4j' in lst || 'tcypher' in lst) {
                script.sh 'bash /db/bin/demo-test/docker-entrypoint.sh git_pull_neo4j'
            }
            if('test' in lst) {
                script.sh 'bash /db/bin/demo-test/docker-entrypoint.sh git_pull_test'
            }
        }
    }

    static def updateServerConf(script, String DB, DATASET, max_connection)
    {
        script.echo "UPDATE Server Config of ${DB} on ${DATASET} with ${max_connection} connections."
        switch (DB){
            case 'PG':
                script.sh "sed -i -E 's/^#?shared_buffers.*/shared_buffers = 48GB/' /var/lib/postgresql/data/postgresql.conf"
                break
            case 'MA':
                script.sh "sed -i -E 's/^#?innodb_buffer_pool_size.*/innodb_buffer_pool_size = 48G/' /etc/mysql/mariadb.conf.d/50-server.cnf"
                break
        }
    }

    static def stopServerCmd(String DB)
    {
        switch (DB){
            case 'PG':
                return "su postgres -c 'pg_ctl stop'"
            case 'MA':
                return "mariadb-admin shutdown -plangduhua"
            case 'AEONG':
                return "/home/AeonG/build/docker-entrypoint.sh stop"
            default:
                return 'echo "### NO SERVER EXIT CMD ###"'
        }
    }

    static def runAndExitAfterTimeout(script, String entrypointScript, String DB, String timeout){
        script.sh "${entrypointScript} & sleep ${timeout} && echo '### TIMEOUT ### Server SHUTDOWN after ${timeout} ###' && ${stopServerCmd(DB)}"
    }

    static def watchdog(script, currentBuild, String timeoutStr, String timeoutProgress, String logPipe, String markerStr){
        long begin = System.currentTimeMillis()
        long veryBegin = begin
        int timeoutP = timeoutProgress.toInteger()
        int timeout = timeoutStr.toInteger()
        def markers = markerStr.split(",") as Set
        println "Watchdog search markers: ${markers}"
        int lastLineCount = 0
        boolean killServerWhenClientExit = false
        for (int i=0; i<2_000; i++) {
            long now = System.currentTimeMillis()
            File f = new File(logPipe)
            boolean hasProgress
            if (f.isFile() && f.exists()) {
                List<String> lines = f.readLines()
                for (line in lines) {
                    boolean found = false
                    if(line.contains('Server_To_Kill_Amitabha')){
                        killServerWhenClientExit = true
                    }
                    if(line.contains('Server_Finish_Amitabha')){
                        markers.remove('Server_Finish_Amitabha')
                    }
                    if(line.contains('Client_Finish_Amitabha')){
                        markers.remove('Client_Finish_Amitabha')
                        if(killServerWhenClientExit){
                            return [false, true, 'Client exit and will kill server.']
                        }
                    }
                    if (found) script.echo "Watchdog current markers: ${markers}"
                }
                if (markers.isEmpty()) {
                    return [false, false, "Watchdog found all markers! Congratulations! Exit..."]
                }
                hasProgress = (lines.size() > lastLineCount)
                lastLineCount = lines.size()
                if (hasProgress) begin = now
            } else {
                script.echo "Watchdog: target file not exist, waiting..."
                hasProgress = false
            }
            Thread.sleep(5000)
            def time2veryBegin = ( now - veryBegin ) / 1000
            def time2lastCheck = ( now - begin ) / 1000L
            if (i%36==1) script.echo "[WatchDog] time2lastCheck: ${time2lastCheck}, hasProgress: ${hasProgress}. time2veryBegin: ${time2veryBegin}."
            if (time2lastCheck > timeoutP && !hasProgress) return [true, true, "WatchDog Timeout! No progress after ${timeoutP} seconds"]
            if (time2veryBegin > timeout) return [true, true, "WatchDog Timeout! Not finish within ${timeout} seconds" ]
        }
        return [true, true, "Not finish within 10000s, watch dog dead."]
    }

    @NonCPS
    private static boolean removeIfLineContainsMarker(Set<String> markers, String line){
        return markers.removeIf { line.contains(it) }
    }
//    static void archive(self, String dir, String fileName){
//        self.ws(dir){
//            self.archiveArtifacts artifacts: "${fileName}", fingerprint: true, followSymlinks: false, onlyIfSuccessful: true
//        }
//    }
//    static def toEnvKVList(Map params){
//        def sb = []
//        params.each{ k, v ->
//            sb.add(k.toString().toUpperCase()+'='+v)
//        }
//        println(sb)
//        return sb
//    }

//    private static def innerLoopByKey(conf, keyList, body, params){
//        def queue = new ArrayDeque(keyList)
//        if(queue.size()>0){
//            def key = queue.pop()
//            def vList = conf[key]
//            for(int i=0; i<vList.size(); i++){
//                params[key] = vList[i]
//                innerLoopByKey(conf, queue, body, params)
////                println(params)
//            }
//        }else{
//            body(params, conf)
//        }
//    }
//
//    static def loopByKey(arguments){
////        println(arguments)
//        innerLoopByKey(arguments.conf, arguments.keys, arguments.body, [:])
//    }

//    static void deploy(script, env, String dbtype, String milestone_name){
//        String mFullPath = "${env.DIR_ARTIFACT}\\${milestone_name}"
//        switch (dbtype) {
//            case 'postgresql': //关闭存在的连接
//                script.bat "echo SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE datname='${milestone_name}' AND pid!=pg_backend_pid(); | psql -Upostgres"
//                script.bat "echo DROP DATABASE IF EXISTS ${milestone_name}; | psql -Upostgres" //删除现有数据库
//                script.bat "createdb.exe -Upostgres ${milestone_name}" //创建新的数据库
//                script.bat "pg_restore.exe -Upostgres -j 2 -e -d ${milestone_name} ${mFullPath}" //还原数据到数据库
//                break
//            case 'mariadb':
//                break //MariaDB的备份恢复不稳定容易导致数据库崩溃无法使用，故每次还是不备份了，直接重新导入比较好
//                script.bat "echo DROP DATABASE IF EXISTS ${milestone_name}; | mysql -u root -plangduhua" //删除现有数据库
//                script.dir(env.DIR_MARIADB_DATA){ //有时候删不干净需要手动补一剂
//                    script.bat "if exist ${milestone_name} ( rmdir /q /s ${milestone_name} )"
//                }
//                script.dir(env.DIR_WORKSPACE) {
//                    script.bat "if exist ${milestone_name}_d ( rmdir /q /s ${milestone_name}_d )"
//                    script.unzip zipFile: "${mFullPath}", dir: "${milestone_name}_d"
//                    script.bat "mariabackup.exe --prepare --target-dir=${milestone_name}_d --user=root"
//                }
//                // 还原数据（删除datadir目录中与之冲突的文件）
//                script.bat "net stop MariaDB"
//                script.dir(env.DIR_MARIADB_DATA){
//                    script.bat "del aria_log*"
//                    script.bat "del ib*"
//                    script.bat "del x*"
//                }
//                script.bat "mariabackup.exe --copy-back --force-non-empty-directories --target-dir=${env.DIR_WORKSPACE}\\${milestone_name}_d --user=root"
//                script.bat "net start MariaDB"
//                break
//            default:
//                script.dir(env.DIR_WORKSPACE){
//                    script.bat "if exist ${milestone_name} ( rmdir /q /s ${milestone_name} )" //删除现有数据库
//                    script.bat "7z x ${mFullPath}" //还原数据到数据库
//                }
//        }
//    }


//    private static Map<String, String> getMap(Map conf, String key){
//        def kList = conf['key']
//        def vList = conf[key]
//        def kv = [:]
//        for(int i=0; i<kList.size(); i++){
//            String keyName = kList[i]
//            kv[keyName] = vList[i]
//        }
//        return kv
//    }
    static String webhook(script, String job, jid, BUILD_STATUS, BUILD_RESULT, params){
        try {
            def pMap = [:]
            params?.each { param ->
                pMap["${param.key}"] = "${param.value}"
            }
//            script.echo pMap.toString()
            def body = [
                    "parameters": pMap,
                    "status"    : BUILD_RESULT,
                    "phase"     : BUILD_STATUS,
                    "number"    : jid,
            ]
//            def jsonObj = new groovy.json.JsonBuilder(body)
//            script.echo jsonObj.toPrettyString()
//            def response =
            script.httpRequest(
                    url: 'http://master:8999/jenkins/' + job,
                    httpMode: 'POST',
                    contentType: 'APPLICATION_JSON',
                    requestBody: groovy.json.JsonOutput.toJson(body),
                    customHeaders: [[name: 'Authorization', value: 'Bearer namoAmitabha']]
            )
//            script.echo "Status: ${response.status}"
//            script.echo "Response: ${response}"
            return timeNow()
        }catch (NotSerializableException e) {
            script.echo 'NotSerializableException when sending webhook http request.'
        }catch(Exception e){
            script.echo 'error when sending webhook http request.'
//            script.echo e.getMessage()
            StringWriter sw = new StringWriter()
            PrintWriter pw = new PrintWriter(sw)
            e.printStackTrace(pw)
            script.echo sw.toString()
        }finally{
            return ''
        }
    }

//    static void dbSpaceCalc(script, String db_type, String db_host, String milestone, boolean deploy=true) {
//        def (dataset, db, data_size) = extract(milestone)
//        script.withEnv(["DATASET=${dataset}", "DB_NAME=${db}", "DATA_SIZE=${data_size}",
//                "DB_HOST=${db_host}", "DB_TYPE=${db_type}", "MILESTONE_NAME=${milestone}",
//                "DB_PATH=${script.env.DIR_BUILD}\\${milestone}", "EXTRA_INFO=${deploy?"deploy":"build"}"
//        ]) {
//            script.bat "mvn -B exec:java -Dexec.mainClass=edu.buaa.utils.DBSpaceStatistic -Dexec.cleanupDaemonThreads=false"
//        }
//    }

    static String timeNow(){
        return String.valueOf(System.currentTimeMillis());
    }

    static void createDirIfNotExist(String path){
        File dir = new File(path)
        if(!dir.exists()) dir.mkdirs()
        if(!dir.isDirectory()) throw new IllegalStateException(path+" exists.")
    }
}