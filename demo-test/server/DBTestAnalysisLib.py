# -*- coding: utf-8 -*-
"""
Created on 2022/2/24

@author: Song
"""

import pandas as pd
import numpy as np
import os, time, json
from matplotlib import pyplot as plt
import requests
import re
import jenkins
import streamlit as st
import sys, os
from datetime import datetime


def jenkinsBuild(job, params):
    paramStr = '&'.join('{}={}'.format(key, value) for key, value in params.items())
    job_url = "http://localhost:8843/job/{}/buildWithParameters?{}".format(job, paramStr)
    return requests.get(job_url)


from aliyun.log import GetLogsRequest, LogClient

client = LogClient('cn-beijing.log.aliyuncs.com', os.getenv('ALIYUN_LOG_ACCESS_KEY_ID'), os.getenv('ALIYUN_LOG_ACCESS_KEY_SECRET'))


plt.rcParams['font.family'] = ['SimHei']  # 用来正常显示中文标签
plt.rcParams['axes.unicode_minus'] = False  # 用来正常显示负号


@st.cache_data
def download(topic, beginT, endT):
    res = client.get_logs(GetLogsRequest("tgraph-demo-test", "tgraph-log",
                                         fromTime=beginT, toTime=endT, topic=topic,
                                         query="* and not type: size", line=-1, offset=0, reverse=False))
    result = []
    for log in res.get_logs():
        # print(log.get_time())
        # print('source:', log.get_source())
        result.append(log.get_contents())
    return result


@st.cache_data
def size(topic, beginT, endT):
    res = client.get_logs(GetLogsRequest("tgraph-demo-test", "tgraph-log",
                                         fromTime=beginT, toTime=endT, topic=topic,
                                         query="* and type: size", line=-1, offset=0, reverse=False))
    return len(res.get_logs())


def fetchLog(testName):
    def lastXday(x):
        t = int(time.time()) - x * 24 * 3600
        return time.strftime("%Y.%m.%d %H:%M", time.localtime(t))

    def extractBeginTime(tName):
        return tName.split('^')[1].replace('_', ' ')

    # print(testName)
    fileName = (testName + '.csv').replace(':', '.')
    hasCache = os.access(fileName, os.R_OK)
    data = []
    if hasCache:
        data = pd.read_csv(fileName)
    if (not hasCache) or (hasCache and len(data) < 10):
        raw = download(testName, extractBeginTime(testName), lastXday(0))
        data = pd.DataFrame(raw)
        data.to_csv('rawlog/'+fileName)
    if len(data) == 0:
        raise DataNotReadyErr(testName + ' is empty!')
    df = data[['type']]  #
    df['exeTime'] = pd.to_numeric(data['exeTime'])  # .astype(int)
    df['sendTime'] = pd.to_numeric(data['sendTime'])
    if 'params' in data:
        df['params'] = data['params']
    if 'txSuccess' in data:
        df['txSuccess'] = data['txSuccess']
    else:
        df['txSuccess'] = True
    return df


def normalize(df):
    txMap = {
        'tx_query_snapshot_aggr_max': 'R:S.Aggr.Max',
        'tx_query_snapshot_aggr_duration': 'R:S.Aggr.Dur',
        'tx_query_road_by_temporal_condition': 'R:E.Temp.Cond',
        'tx_query_snapshot': 'R:Snapshot',
        'tx_query_entity_history': 'R:E.History',
        'tx_update_temporal_data': 'W:E.Temp.Edit',
        'tx_import_temporal_data': 'W:Temp.Append',
        'tx_import_static_data': 'W:Static.Import',
        'tx_query_reachable_area': 'R:Reachable'
    }
    sysMap = {
        'neo4j1': 'neo4j1_kernel',
        'neo4j2': 'neo4j2_kernel',
        'postgresql': 'postgresql',
        'mariadb': 'mariadb',
        'tgraph_kernel': 'tgraph_kernel'
    }
    df['type'] = df['type'].apply(lambda row: txMap[row])
    # df['sys'] = df['sys'].apply(lambda row: sysMap[row])
    return df


def cat(*args):
    pds = list(args)
    if len(pds) > 1:
        return pd.concat(pds, ignore_index=True)
    else:
        return pds[0]


def limit(df):
    return df.sample(1000)


def dictKey2PdCol(d, colName):
    toUnion = []
    for k in d:
        d[k][colName] = k
        toUnion.append(d[k])
    return pd.concat(toUnion, ignore_index=True)


def extractParam2Col(df, pNameList):
    def extractor(row):
        d = json.loads(row['params'])
        return [d[pName] for pName in pNameList]

    if 'params' in df:
        df[pNameList] = df.apply(extractor, axis=1, result_type="expand")
        # print(df)
    return df


class DataNotReadyErr(Exception):
    def __init__(self, msg):
        self.message = msg

    def __str__(self):  # 这里就是异常的字符串信息
        return self.message


@st.cache_data
def lastTestName(dataset, db, size, maxCon):
    query = leancloud.Query('TGraphTest')
    query.equal_to('Dataset', dataset)
    query.equal_to('maxCon', maxCon)
    query.equal_to('MSize', size)
    query.contains('DB', '_' + db.lower() + '_')
    query.add_descending('createdAt')
    if query.count() == 0:
        raise DataNotReadyErr('test not found on {} {} {} {}'.format(dataset, db, size, maxCon))
    else:
        return query.first().get('TestName')


def downMilestone(dataset, db, mSize, tdb):
    resultArr = tdb.execute('''SELECT space_detail FROM "Jenkins-Build" WHERE db=%s AND dataset=%s AND "tpSize"=%s
    ORDER BY "ID" DESC LIMIT 1''',
                (db, dataset, mSize))
    if len(resultArr) > 0:
        return resultArr[0]['space_detail']
    else:
        raise DataNotReadyErr('no db build record for {} {} {}'.format(dataset, db, mSize))
    # query = leancloud.Query('TestMilestone')
    # query.equal_to('DB', db)
    # query.equal_to('Dataset', dataset)
    # query.equal_to('MSize', 't' + mSize)
    # query.not_equal_to('extra', 'deploy')
    # query.add_descending('createdAt')
    # if query.count() == 0:
    #     raise DataNotReadyErr('milestone not found on {} {} {}'.format(dataset, db, mSize))
    # else:
    #     obj = query.first()
    #     print(dataset, db, mSize, obj.updated_at.strftime("%Y-%m-%d %H:%M"))
    #     return obj.get('detail'), obj.updated_at.strftime("%Y-%m-%d %H:%M")


# def milestoneBuildTime(dataset, db, mSize):
#     query = leancloud.Query('TestMilestone')
#     query.equal_to('DB', db)
#     query.equal_to('Dataset', dataset)
#     query.equal_to('MSize', 't' + mSize)
#     query.not_equal_to('extra', 'deploy')
#     query.add_descending('createdAt')
#     if query.count() == 0:
#         raise DataNotReadyErr('milestone not found on {} {} {}'.format(dataset, db, mSize))
#     else:
#         objList = query.find()
#         print(dataset, db, mSize, 'x' + str(len(objList)))
#         return list(map(lambda obj: {
#             'time': obj.updated_at.strftime("%Y-%m-%d %H:%M"),
#             'duration': obj.get('duration'),
#             'dataset': dataset,
#             'system': db,
#             'tp_size': mSize,
#             # 'detail': obj.get('detail')
#         }, objList))


def calcSpace(dataset, db, mSize, tdb):
    raw = downMilestone(dataset, db, mSize, tdb)
    # print(raw)
    content = json.loads(raw)
    # print(content),'time': t
    result = {'sData': 0, 'sIndex': 0, 'tData': 0, 'tIndex': 0}
    if db in ['MA', 'PG']:
        for table in content:
            if table['relname'].endswith('_tp'):
                result['tData'] += table['tablesize']
                result['tIndex'] += table['indexsize']
            else:
                result['sData'] += table['tablesize']
                result['sIndex'] += table['indexsize']
    elif db.startswith('TG'):
        result['sData'] = content['neo_data']
        result['sIndex'] = content['neo_index']
        result['tData'] = content['tp_node_data'] + content['tp_rel_data']
        result['tIndex'] = content['tp_node_index'] + content['tp_rel_index']
    elif db in ['N1', 'N2']:
        raw0 = downMilestone(dataset, db, 'T.0', tdb)
        c0 = json.loads(raw0)
        # print(c0)
        result['sData'] = c0['data']
        result['sIndex'] = c0['index']
        result['tData'] = content['data'] - c0['data']
        result['tIndex'] = content['index'] - c0['index']
    else:
        raise DataNotReadyErr('no db matched of {} {} {}'.format(dataset, db, mSize))
    return result


@st.cache_data
def lastTestNameByTest(dataset, db, test, maxCon):
    query = leancloud.Query('TGraphTest')
    query.contains('TestName', test)  #
    query.contains('Dataset', dataset)
    query.equal_to('maxCon', maxCon)
    query.contains('DB', '_' + db.lower() + '_')
    query.add_descending('createdAt')
    if query.count() == 0:
        raise DataNotReadyErr('test not found on {} {} {} {}'.format(dataset, db, test, maxCon))
    else:
        rows = query.find()
        return map(lambda row: '%s on %s (%ds)'.format(row.get('TestName'), row.get('device'), row.get('duration')),
                   rows)


@st.cache_data
def lastTestNameBy(dataset, db, test, tpsize, maxCon):
    query = leancloud.Query('TGraphTest')
    query.contains('TestName', test)  #
    query.contains('Dataset', dataset)
    query.equal_to('MSize', tpsize.lower())
    query.equal_to('maxCon', maxCon)
    query.equal_to('device', 'data')
    query.contains('DB', '_' + db.lower() + '_')
    query.add_descending('createdAt')
    if query.count() == 0:
        raise DataNotReadyErr('test not found on data: {} {} {} {}'.format(dataset, db, tpsize, maxCon))
    else:
        # row = query.first()
        # return [[row.get('TestName'), row.get('device'), row.get('duration')]]
        rows = query.find()
        return list(map(lambda row: [row.get('TestName'), row.get('device'), row.get('duration'), row.updated_at.strftime("%Y-%m-%d %H:%M")], rows))


def highlight_invalid(series):
    db = series.name.lower()
    # print(db)
    lastValidT = -1  # latestBuildFor(db, '', 'space')
    if lastValidT < 0:
        return ['background-color:lightgrey' for i in range(0, len(series))]
    else:
        fm_dt = datetime.fromtimestamp(lastValidT).strftime("%Y-%m-%d %H:%M")
        isValid = series > fm_dt
        l = []
        for v in isValid:
            if v:
                l.append('background-color:lightgreen')
            else:
                l.append('')
        return l


@st.cache_data
def findValidTest(dataset, db, testName, tpSize, maxCon):
    tests = lastTestNameBy(dataset, db, testName, tpSize, maxCon)
    for test in tests:
        info = TestNameInfo(test[0])
        if info.mtpsize in info.qtpsize:
            # print(test)
            return [info, test[1], test[2]]
    raise DataNotReadyErr('test not found on {} {} {} {}'.format(dataset, db, testName, maxCon))


def g(row, key):
    return row[key] if key in row else None


class HashResult:
    def __init__(self, data, keys, getFunc=lambda item, key: item[key]):
        self._data = data
        self._keys = keys
        self._cache = {}
        for item in data:
            cache_key = '_'.join(list(map(lambda k: getFunc(item, k), keys)))
            if cache_key in self._cache:
                self._cache[cache_key].append(item)
            else:
                self._cache[cache_key] = [item]

    def get(self, *args):
        cache_key = '_'.join(args)
        if cache_key in self._cache:
            return self._cache[cache_key]
        else:
            return []

# b_traffic_T.01_aggdur_100@m_traffic_tgk_all^2022.5.16_3:17
testNameRegexPattern = re.compile(r"b_([^_]+)_([^_]+)_([^_]+)_([^@]+)@m_([^_]+)_([^_]+)_([^\^]+)\^([^_]+)_(.+)")


class TestNameInfo:
    def __init__(self, name):
        self.tn = name
        mall = re.findall(testNameRegexPattern, name)
        if len(mall) == 1:
            m = mall[0]
            assert m[0] == m[4]
            self.dataset = m[0]
            self.qtpsize = m[1]
            self.test = m[2]
            self.reqcnt = m[3]
            self.db = m[5]
            self.mtpsize = m[6]
            self.time = m[7] + ' ' + m[8]
        else:
            raise DataNotReadyErr('invalid testName: ' + name)

    def __str__(self):  # 这里就是异常的字符串信息
        return self.tn


def dur2str(timeCost):
    minute = int((timeCost % 3600) / 60)
    hour = int(timeCost / 3600)
    return '{:0>2}:{:0>2}'.format(hour, minute)
    # if timeCost > 3600 * 2:
    #     return '#####'
    # elif timeCost > 1800:
    #     return '****'
    # elif timeCost > 900:
    #     return '**'
    # elif timeCost > 300:
    #     return '*'
    # else:
    #     return '.'


def timeHumanRead(seconds):
    day = int(seconds / 3600 / 24)
    hour = int((seconds % (3600 * 24)) / 3600)
    minute = int((seconds % 3600) / 60)
    seconds = seconds % 60
    return str(day) + '天' + str(hour) + '时' + str(minute) + '分'


import psycopg2


class QueryCorrectInfo:
    def __init__(self):
        self.conn = psycopg2.connect(database="test_case", user="postgres", password="langduhua", host="127.0.0.1",
                                     port="5432")
        self.cursor = self.conn.cursor()
        self.sql = """select last_status from test_case where id=%s"""

    def isCorrect(self, tid):
        self.cursor.execute(self.sql, (tid,))
        # print(self.cursor.query)
        row = self.cursor.fetchone()
        # print(row)
        if row[0] is None:
            return False
        else:
            return row[0].startswith('success')

    # conn.commit()
    def close(self):
        self.cursor.close()
        self.conn.close()



from psycopg2.extras import RealDictCursor

class TestDatabase:
    def __init__(self):
        self.conn = psycopg2.connect(database="test_case", user="postgres", password="langduhua", host="127.0.0.1", port="5432")
        self.cursor = self.conn.cursor(cursor_factory=RealDictCursor)

    def getDeploy(self):
        self.cursor.execute('SELECT * FROM "Jenkins-Deploy" order by created_at DESC')
        rows = self.cursor.fetchall()
        return rows

    def getBuild(self):
        self.cursor.execute('SELECT * FROM "Jenkins-Build" order by created_at DESC')
        rows = self.cursor.fetchall()
        return rows

    def getTest(self):
        self.cursor.execute('SELECT * FROM "Jenkins-Test" order by created_at DESC')
        rows = self.cursor.fetchall()
        return rows

    def getTestDetail(self, jid):  # vc.result, err_msg,
        self.cursor.execute('''SELECT tc.id test_id, execute_time, TRIM(vc.status) status, create_t, result_size, 
        tc.st,tc.et,jenkins_id_trial jenkins_id,mark,jt.db,test,tc.dataset,"mTpSize" mtp,"maxCon" maxcon,vc.code_version 
FROM verified_case vc, "Jenkins-Test" jt, test_case tc 
where vc.jenkins_id_trial=jt."ID" and tc.id=vc.id and jt.device='data' and jt."ID"=%s order by create_t asc''', (int(jid),))
        rows = self.cursor.fetchall()
        return rows

    def getTestListDetail(self, jidList):  # vc.result, err_msg,
        jArr = tuple(list(map(lambda f: str(int(f)), jidList)))
        self.cursor.execute('''SELECT tc.id test_id, execute_time, TRIM(vc.status) status, create_t, created_at, result_size, 
        tc.st,tc.et,jenkins_id_trial jenkins_id,mark,jt.db,test,tc.dataset,"mTpSize" mtp,"maxCon" maxcon,vc.code_version 
FROM verified_case vc, "Jenkins-Test" jt, test_case tc 
where vc.jenkins_id_trial=jt."ID" and tc.id=vc.id and jt.device='data' and jenkins_id in %s order by create_t asc''',
                            (jArr,))
        rows = self.cursor.fetchall()
        return rows

    def execute(self, sql, args=()):
        self.cursor.execute(sql, args)
        rows = self.cursor.fetchall()
        return rows

    def commit(self, sql, args=()):
        self.cursor.execute(sql, args)
        self.conn.commit()

    def close(self):
        self.cursor.close()
        self.conn.close()


class ResultValidateInfo:
    def __init__(self):
        self.conn = psycopg2.connect(database="test_case", user="postgres", password="langduhua", host="127.0.0.1",
                                     port="5432")
        self.cursor = self.conn.cursor()
        self.sql = """select success_rate from test_trial where jenkins_id=%s"""

    def successRatio(self, tid):
        self.cursor.execute(self.sql, (tid,))
        # print(self.cursor.query)
        row = self.cursor.fetchone()
        if row is None or row[0] is None:
            return None
        else:
            # print(row)
            return row[0]

    # conn.commit()
    def close(self):
        self.cursor.close()
        self.conn.close()


def recentJenkinsJob():
    return {
        'deploy': jenkins.job_info('milestone-deploy'),
        'build': jenkins.job_info('milestone-build-master'),
        'test': jenkins.job_info('test'),
        'validate': jenkins.job_info('validate')
    }


def jenkinsNodeRunningBuilds():
    d = {}
    for node in ['data', 'ssworkstation', 'zhworkstation']:
        jNode = jenkins.j.nodes.get(node)
        d[node] = (jNode.api_json(), [b.api_json() for b in jNode.iter_builds()])
    return d


@st.cache_data
def benchmark():
    root = 'D:\\tgraph\\jenkins'
    files = os.listdir(root)
    results = []
    for f in files:
        path = root + os.sep + f
        benchmarkFilePath = path + os.sep + 'benchmark.json'
        if os.path.isdir(path) and f.startswith('b_') and os.path.exists(benchmarkFilePath):
            mtime = os.path.getmtime(benchmarkFilePath)
            fsize = os.path.getsize(benchmarkFilePath)
            if fsize > 0:
                with open(benchmarkFilePath.encode('utf-8'), 'r') as fp:
                    for count, line in enumerate(fp):
                        pass
                lineCnt = count + 1
                results.append([f, mtime, fsize, lineCnt])

    def lmd(row):
        arr = row[0].split('_')
        return {
            'dataset': arr[1],
            'tp_size': arr[2][1:],
            'test': arr[3],
            'qcnt': int(arr[4]),
            'ctime': datetime.fromtimestamp(row[1]).strftime("%m-%d %H:%M:%S"),
            'fsize': row[2],
            'f.l.cnt': row[3]
        }

    bList = list(map(lmd, results))
    bList.sort(key=lambda i: i['ctime'], reverse=True)
    return bList



@st.cache_data
def lcRWTest(test, meta):
    query = leancloud.Query('TGraphTest')
    query.contains('TestName', test)
    query.equal_to('device', 'data')
    query.add_descending('createdAt')
    query.limit(999)
    rows = query.find()

    def timeReformat(tStr):
        t0 = tStr.split(' ')
        t1 = t0[0].split('.')
        t2 = t0[1].split(':')
        month = int(t1[1])
        day = int(t1[2])
        hour = int(t2[0])
        minute = int(t2[1])
        return '{:02d}-{:02d} {:02d}:{:02d}'.format(month, day, hour, minute)

    def rowRefactor(obj):
        ti = TestNameInfo(obj.get('TestName'))
        return {
            'test': ti.test,
            'db': ti.db,
            'dataset': ti.dataset,
            'mtpSize': ti.mtpsize,
            'qtpSize': ti.qtpsize,
            'maxCon': str(obj.get('maxCon')),
            'reqcnt': ti.reqcnt,
            'tname': ti.tn,
            'ct': timeReformat(ti.time),
            'duration': obj.get('duration'),
            'jid': obj.get('jenkinsId'),
            'status': obj.get('status')
        }

    results = list(map(rowRefactor, rows))
    cache = HashResult(results, ['test', 'db', 'dataset', 'mtpSize', 'maxCon'])

    def testMetaInfo(row):
        dataset = row['dataset']
        db = row['db']
        tp_size = row['tp_size']
        maxCon = row['max_con']
        if row['test'] == test and row['valid']:
            testInfo = cache.get(test, db.lower(), dataset, tp_size[1:], str(maxCon))
            # st.write(testInfo)
            if len(testInfo) > 0:
                historyTestT = list(map(lambda obj: obj['duration'], testInfo))
                medianBuildTime = np.median(historyTestT)
                m = testInfo[0]
                # return m[3]
                return [m['ct'], m['duration'], len(historyTestT), medianBuildTime, m['tname']]
            else:
                # return None
                return [None, None, None, None, None]
        else:
            return [g(row, 'lct_ct'), g(row, 'lct.dur_t'), g(row, 'lct.his.cnt'), g(row, 'lct.his.median'),
                    g(row, 'lct.tn')]

    meta[['lct' + '_ct',
          'lct' + '.dur_t',
          'lct' + '.his.cnt',
          'lct' + '.his.median',
          'lct' + '.tn'
          ]] = meta.apply(testMetaInfo, axis=1, result_type="expand")
    # meta['lc_' + test + '_ct']
    return meta


@st.cache_data
def vkRWTest(test, meta):
    testInfo = vika.datasheet("dstkUjX9kfeAuSumZj", field_key="name")
    records = testInfo.records.filter(test=test)
    recordList = list(map(lambda x: x.json(), records))
    recordList.sort(key=lambda i: i['创建时间'], reverse=True)
    # print(test, len(recordList))
    cache = HashResult(recordList, ['test', 'dataset', 'db', 'mTpSize'])

    # st.write(cache._cache)

    def extractT(row):
        dataset = row['dataset']
        tp_size = row['tp_size']
        db = row['db']
        if row['test'] == test and row['valid']:
            cache_item = cache.get(test, dataset, db.lower(), tp_size[1:])
            if len(cache_item) > 0:
                t = cache_item[0]
                ctime = datetime.fromtimestamp(int(t['创建时间'] / 1000)).strftime("%m-%d %H:%M")
                status = t['status'] if 'status' in t else t['phase']
                # st.write(test, dataset, db.lower(), tp_size[1:], ctime, status)
                mark = t['mark'] if 'mark' in t else '-'
                # if ('mark' in t) and t['mark'] is not None:
                #     print(t['mark'], type(t['mark']))
                return [ctime, status, mark, t['ID']]
            else:
                return [None, None, '-', None]
        else:
            return [g(row, 'vkt_ct'), g(row, 'vkt.status'), g(row, 'mark'), g(row, 't.jid')]

    meta[['vkt_ct', 'vkt.status', 'mark', 't.jid']] = meta.apply(extractT, axis=1, result_type="expand")
    return meta

@st.cache_data
def aliRWTest(meta):
    def getRawData(row):
        testFullName = row['lct.tn']
        if type(testFullName) == str:
            try:
                # st.write(testFullName)
                raw = fetchLog(testFullName)
                qCnt = len(raw)
                if qCnt > 0:  # missingCnt += 1
                    raw = extractParam2Col(raw, ['id'])
                    # if 'tgc' in testFullName and row['dataset']=='traffic' and row['tp_size']=='.1':
                    #     st.table(raw['txSuccess'])
                    succReq = raw.loc[raw['txSuccess'].astype('bool')]
                    succCnt = len(succReq)
                    if succCnt > 0:
                        beginTime = succReq['sendTime'].min()
                        endTime = (succReq['exeTime'] + succReq['sendTime']).max()
                        dur = endTime - beginTime
                        thrput = succCnt * 60 * 1000 / dur
                        # if 'once' not in st.session_state:
                        #     st.dataframe(succReq)
                        #     st.session_state['once'] = True
                        avgT = np.mean(succReq['exeTime'])
                        l90 = succReq['exeTime'].quantile(.9)
                        std = np.std(succReq['exeTime'])
                        return [succCnt, avgT, int(l90), dur, thrput, qCnt, list(zip(list(succReq['id']), list(succReq['exeTime']))),
                            "{:.0f}±{:.0f}({:.0f})".format(avgT, std, succCnt)]
            except DataNotReadyErr:
                pass
        return [None, None, None, None, None, 0, [], None]

    meta[['al_succ_cnt',
          'al_avg_t',
          'al_l90_t',
          'al_dur_t',
          'al_throughput',
          'al_qcnt',
          'al.raw',
          'al.content'
          ]] = meta.apply(getRawData, axis=1, result_type="expand")
    return meta
