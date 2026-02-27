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
                    'DIR_MARIADB_DATA': "G:\\Program Files\\MariaDB 10.6\\data",
                    'MAVEN_OPTS': '-Xmx18g -Xms2g',
                    'MAVEN_BUILD_OPTS': '-Xmx18g -Xms2g'
            ],
            "data":[
                    'DIR_WORKSPACE': "/home/jenkins/workspace/tgraph", //运行测试的路径
                    'DIR_BUILD': "/home/jenkins/workspace/milestone", //构建milestone的路径
                    'DIR_CODE': "/home/jenkins/workspace/code", //代码路径
                    'DIR_ARTIFACT': "/home/jenkins/artifact",//保存milestone的路径
                    'DIR_BENCHMARK': "unsupported", //这个工作站无法启动nas client连接到master
                    'DIR_DATA': "/home/jenkins/dataset",
                    'DIR_MARIADB_DATA': "/mnt/disk8t/mariadb10.6/data",
                    'MAVEN_OPTS': '-Xmx48g -Xms4g',
                    'MAVEN_BUILD_OPTS': '-Xmx100g -Xms8g -XX:MaxDirectMemorySize=64g'
            ],
            "zhworkstation":[
                    'DIR_WORKSPACE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_BUILD': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_CODE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_ARTIFACT': "Y:",
                    'DIR_BENCHMARK': "X:",
                    'DIR_DATA': "Z:",
                    'DIR_MARIADB_DATA': "E:\\Program Files\\MariaDB 10.6\\data",
                    'MAVEN_OPTS': '-Xmx48g -Xms4g',
                    'MAVEN_BUILD_OPTS': '-Xmx80g -Xms8g -XX:MaxDirectMemorySize=40g'
            ],
            "ssworkstation":[
                    'DIR_WORKSPACE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_BUILD': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_CODE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_ARTIFACT': "Y:",
                    'DIR_BENCHMARK': "X:",
                    'DIR_DATA': "Z:",
                    'DIR_MARIADB_DATA': "E:\\Program Files\\MariaDB 10.6\\data",
                    'MAVEN_OPTS': '-Xmx48g -Xms4g',
                    'MAVEN_BUILD_OPTS': '-Xmx90g -Xms8g -XX:MaxDirectMemorySize=50g'
            ],
            "zzy":[
                    'DIR_WORKSPACE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_BUILD': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_CODE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_ARTIFACT': "Y:",
                    'DIR_BENCHMARK': "X:",
                    'DIR_DATA': "Z:",
                    'DIR_MARIADB_DATA': "E:\\Program Files\\MariaDB 10.6\\data",
                    'MAVEN_OPTS': '-Xmx8g -Xms4g',
                    'MAVEN_BUILD_OPTS': '-Xmx8g -Xms4g -XX:MaxDirectMemorySize=5g'
            ],
            "hsc":[
                    'DIR_WORKSPACE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_BUILD': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_CODE': "D:\\tgraph\\jenkins\\workspace",
                    'DIR_ARTIFACT': "Y:",
                    'DIR_BENCHMARK': "X:",
                    'DIR_DATA': "Z:",
                    'DIR_MARIADB_DATA': "E:\\Program Files\\MariaDB 10.6\\data",
                    'MAVEN_OPTS': '-Xmx8g -Xms4g',
                    'MAVEN_BUILD_OPTS': '-Xmx8g -Xms4g -XX:MaxDirectMemorySize=5g'
            ]
    ]

    static def opt(String key, String node) {
        return envVars.get(node).get(key)
    }

    static def dir(String key, String node) {
        return envVars.get(node).get(key)
    }

    static def dir(String key, String node, String childFileName) {
        return dir(key, node) +'\\'+childFileName
    }

    static def fExist(String folder, String fName){
        def f = new File(folder, fName)
        return f.exists()
    }

    static def fExist(String fullPath){
        def f = new File(fullPath)
        return f.exists()
    }

    static boolean needCompile(currentBuild){
        // return true if triggered by user manually
        return currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause').size() > 0
    }

    static def codeSyncAndCompile(script, lstStr, clean=false){
        def lst = lstStr.split(',')
        def run = script.isUnix() ? script.sh : script.bat
        if('tp' in lst) script.dir('temporal-storage') {
            run 'git pull'
            run "mvn -B -Dmaven.test.skip ${clean?'clean':''} install"
        }
        if('neo4j' in lst || 'tcypher' in lst) script.dir('temporal-neo4j') {
            run 'git pull'
//            run "mvn -B -Dmaven.test.skip -Dlicense.skip -Dlicensing.skip ${clean?'clean':''} install -pl org.neo4j:neo4j-kernel"
            run "mvn -B -DskipTests -Dlicense.skip -Dlicensing.skip clean install -pl org.neo4j:neo4j-kernel -am"
            if('tcypher' in lst) run "mvn -B -Dmaven.test.skip -Dlicense.skip -Dlicensing.skip ${clean?'clean':''} install -pl org.neo4j:neo4j-cypher"
        }
        if('test' in lst) script.dir('demo-test'){
            run 'git pull'
            run "mvn -B -Dmaven.test.skip ${clean?'clean':''} compile"
        }
    }

    static def dropDB(script, String db, String dbName, env){
        switch (db){
            case 'postgresql':
                script.bat "echo SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE datname='${dbName}' AND pid!=pg_backend_pid(); | psql -Upostgres"
                script.bat "echo DROP DATABASE IF EXISTS ${dbName}; | psql -Upostgres"
                break
            case 'mariadb':
                try{
                    script.bat "echo DROP DATABASE IF EXISTS ${dbName}; | mysql -u root -plangduhua"
                }catch(ignore){
                    script.dir(env.DIR_MARIADB_DATA) {
                        script.bat "if exist ${dbName} ( rmdir /q /s ${dbName} )"
                    }
                }
                break
            default:
                script.dir(env.DIR_BUILD){
                    script.bat "if exist ${dbName} ( rmdir /q /s ${dbName} )"
                }
                if(env.DIR_NEO4J_LOG!=null && fExist(env.DIR_NEO4J_LOG)) script.dir(env.DIR_NEO4J_LOG){
                    script.bat "del neostore.transaction.db.*"
                }
        }
    }

    static void archive(self, String dir, String fileName){
        self.ws(dir){
            self.archiveArtifacts artifacts: "${fileName}", fingerprint: true, followSymlinks: false, onlyIfSuccessful: true
        }
    }

    static def archiveMilestone(self, String dbtype, String milestone_name, env){
        String milestone_full_path = "${env.DIR_ARTIFACT}\\${milestone_name}"
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
//                    self.bat "del ${milestone_full_path}.7z"
//                    self.zip dir: "${milestone_name}", zipFile: "${milestone_full_path}", overwrite: true, archive: false
                    self.bat "if exist ${milestone_full_path}.7z ( del ${milestone_full_path}.7z )"
//                    self.echo renameIfExist(env.DIR_ARTIFACT, milestone_name+".7z")
                    self.bat "7z a -mmt16 -mx2 ${milestone_full_path}.7z ${milestone_name}"
                    // bat "rmdir /q /s ${params.db}"
                    // bat "xcopy ${params.db} ${env.MILESTONE_FULL_PATH} /y /e /h /i"
                    // bat "ren ${params.db} ${env.MILESTONE_NAME}"
                }
        }
    }

    static def ctrlDB(self, String action, String dbName){
        switch (dbName.toUpperCase()) {
            case 'PG':
                return self.bat(returnStatus: true, script: "net ${action} postgresql-x64-13")
            case 'MA':
                return self.bat(returnStatus: true, script: "net ${action} mariadb")
            default:
                return 0
        }
    }

    private static String getCor(val_a, list_a, list_b){
        for(int i=0; i<list_a.size(); i++){
            if(list_a[i]==val_a){
                return list_b[i]
            }
        }
        throw new IllegalArgumentException("key not found for ${val_a} ${list_a} ${list_b}")
    }

    private static Map<String, String> getMap(Map conf, String key){
        def kList = conf['key']
        def vList = conf[key]
        def kv = [:]
        for(int i=0; i<kList.size(); i++){
            String keyName = kList[i]
            kv[keyName] = vList[i]
        }
        return kv
    }

    static def toEnvKVList(Map params){
        def sb = []
        params.each{ k, v ->
            sb.add(k.toString().toUpperCase()+'='+v)
        }
        println(sb)
        return sb
    }

    private static def innerLoopByKey(conf, keyList, body, params){
        def queue = new ArrayDeque(keyList)
        if(queue.size()>0){
            def key = queue.pop()
            def vList = conf[key]
            for(int i=0; i<vList.size(); i++){
                params[key] = vList[i]
                innerLoopByKey(conf, queue, body, params)
//                println(params)
            }
        }else{
            body(params, conf)
        }
    }

    static def loopByKey(arguments){
//        println(arguments)
        innerLoopByKey(arguments.conf, arguments.keys, arguments.body, [:])
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
            kvList.add(k.toUpperCase()+'='+v)
        }
        return kvList
    }

    static String milestoneName(String dataset, String data_size, String db_name){
        String name = "m_${dataset}_${db_name}_${data_size}"
        return name.toLowerCase().replace("t.", "")
    }

    static List<String> extract(String milestoneName){
        String[] arr = milestoneName.split("_")
        return [arr[1], arr[2].toUpperCase(), 'T.'+arr[3]]
    }

    static String benchmarkName(String dataset, String data_size, int query_size, String query_dist){
        String name = "b_${dataset}_${data_size}_${query_dist}_${query_size}"
        return name.replace(" ", "")
    }

    static void deploy(script, env, String dbtype, String milestone_name){
        String mFullPath = "${env.DIR_ARTIFACT}\\${milestone_name}"
        switch (dbtype) {
            case 'postgresql': //关闭存在的连接
                script.bat "echo SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE datname='${milestone_name}' AND pid!=pg_backend_pid(); | psql -Upostgres"
                script.bat "echo DROP DATABASE IF EXISTS ${milestone_name}; | psql -Upostgres" //删除现有数据库
                script.bat "createdb.exe -Upostgres ${milestone_name}" //创建新的数据库
                script.bat "pg_restore.exe -Upostgres -j 2 -e -d ${milestone_name} ${mFullPath}" //还原数据到数据库
                break
            case 'mariadb':
                break //MariaDB的备份恢复不稳定容易导致数据库崩溃无法使用，故每次还是不备份了，直接重新导入比较好
                script.bat "echo DROP DATABASE IF EXISTS ${milestone_name}; | mysql -u root -plangduhua" //删除现有数据库
                script.dir(env.DIR_MARIADB_DATA){ //有时候删不干净需要手动补一剂
                    script.bat "if exist ${milestone_name} ( rmdir /q /s ${milestone_name} )"
                }
                script.dir(env.DIR_WORKSPACE) {
                    script.bat "if exist ${milestone_name}_d ( rmdir /q /s ${milestone_name}_d )"
                    script.unzip zipFile: "${mFullPath}", dir: "${milestone_name}_d"
                    script.bat "mariabackup.exe --prepare --target-dir=${milestone_name}_d --user=root"
                }
                // 还原数据（删除datadir目录中与之冲突的文件）
                script.bat "net stop MariaDB"
                script.dir(env.DIR_MARIADB_DATA){
                    script.bat "del aria_log*"
                    script.bat "del ib*"
                    script.bat "del x*"
                }
                script.bat "mariabackup.exe --copy-back --force-non-empty-directories --target-dir=${env.DIR_WORKSPACE}\\${milestone_name}_d --user=root"
                script.bat "net start MariaDB"
                break
            default:
                script.dir(env.DIR_WORKSPACE){
                    script.bat "if exist ${milestone_name} ( rmdir /q /s ${milestone_name} )" //删除现有数据库
                    script.bat "7z x ${mFullPath}" //还原数据到数据库
                }
        }
    }

    static void deploy2hdd(script, env, dbtype, milestone_name){
        String mFullPath = "${env.DIR_ARTIFACT}\\${milestone_name}"
        switch (dbtype) {
            case 'postgresql': //关闭存在的连接
                ctrlDB(script, 'start', 'PG')
                script.bat "echo SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE datname='${milestone_name}' AND pid!=pg_backend_pid(); | psql -Upostgres"
                script.bat "echo DROP DATABASE IF EXISTS ${milestone_name}; | psql -Upostgres" //删除现有数据库
                script.bat "createdb.exe -Upostgres ${milestone_name}" //创建新的数据库
                script.bat "pg_restore.exe -Upostgres -j 4 -e -d ${milestone_name} ${mFullPath}" //还原数据到数据库
                ctrlDB(script, 'stop', 'PG')
                break
            case 'mariadb':
                script.error "MariaDB should rebuild rather than deploy"
                break
            default:
                script.dir(env.DIR_WORKSPACE){
                    if(dbtype.contains("tcypher")){
                        String modifiedName = milestone_name.replace("tgk", "tgl")
                        script.bat "if exist ${modifiedName} ( rmdir /q /s ${modifiedName} )" //删除现有数据库
                        script.bat "7z x ${mFullPath}.7z -aoa -otgl"
                        script.bat "move tgl\\${milestone_name} ${modifiedName}"
                    }else {
                        script.bat "if exist ${milestone_name} ( rmdir /q /s ${milestone_name} )" //删除现有数据库
                        if (milestone_name.contains("tgsb")) {
                            String brotherName = milestone_name.replace("tgsb", "tgs")
                            String brotherPath = "${env.DIR_ARTIFACT}\\${brotherName}"
                            script.bat "7z x ${brotherPath}.7z -aoa -otgsb"
                            script.bat "move tgsb\\${brotherName} ${milestone_name}"
                        } else {
                            script.bat "7z x ${mFullPath}.7z -aoa" //解压数据库
                        }
                    }
                }
        }
    }

    static void justDeploy(script, String milestone, conf, boolean debug=false){
        if(!fExist(script.env.DIR_ARTIFACT, milestone)) {
            script.echo "${milestone} not exist, should build first."
            return
        }
        def (dataset, db, data_size) = extract(milestone)
        Map<String, String> dbi = getMap(conf['dbinfo'], db)
        String host = debug ? 'master' : dbi['db_host']
//        String host = 'master'
        String type = dbi['db_type']
        script.echo "lock db host: ${host}"
        script.lock(host) {
            script.echo "deploy to: ${host}"
            script.build job: 'milestone-deploy', parameters: [
                    script.string(name: 'target_host', value: host),
                    script.string(name: 'dbtype', value: type),
                    script.string(name: 'milestone', value: milestone),
            ]
        }
    }

    static void dbSpaceCalc(script, String db_type, String db_host, String milestone, boolean deploy=true) {
        def (dataset, db, data_size) = extract(milestone)
        script.withEnv(["DATASET=${dataset}", "DB_NAME=${db}", "DATA_SIZE=${data_size}",
                "DB_HOST=${db_host}", "DB_TYPE=${db_type}", "MILESTONE_NAME=${milestone}",
                "DB_PATH=${script.env.DIR_BUILD}\\${milestone}", "EXTRA_INFO=${deploy?"deploy":"build"}"
        ]) {
            script.bat "mvn -B exec:java -Dexec.mainClass=edu.buaa.utils.DBSpaceStatistic -Dexec.cleanupDaemonThreads=false"
        }
    }

    static String timeNow(){
        return String.valueOf(System.currentTimeMillis());
    }

    static void createDirIfNotExist(String path){
        File dir = new File(path)
        if(!dir.exists()) dir.mkdirs()
        if(!dir.isDirectory()) throw new IllegalStateException(path+" exists.")
    }
}