# -*- coding: utf-8 -*-
"""
Created on 2024/11/24

@author: Song
"""
from DBTestAnalysisLib import *
import streamlit as st
import seaborn as sns
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib as mpl
from matplotlib.patches import Patch

# st.set_page_config(page_title="数据绘图", page_icon="📈", layout="wide")
st.title("S1论文数据绘图页", help="by Sjh")

plt.style.use('seaborn-v0_8-paper')
# plt.rcParams['font.sans-serif'] = ['SimHei']  # 中文显示
plt.rcParams['font.sans-serif'] = ['Arial']  # 中文显示
plt.rcParams['axes.unicode_minus'] = False  # 用来正常显示负号
plt.rcParams['mathtext.fontset'] = 'cm'



def milestones(dbs, datasets, sizes):
    metaList = []
    for db in dbs:  # 'TGC', 'TGK', 'TGKI', 'N2',
        for dataset in datasets:  #
            for mSize in sizes:  # '.01', '.5', '.9','.1',
                metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize})

    meta = pd.DataFrame(metaList)
    return meta
    # toPrint1 = meta.pivot(index=['dataset', 'tp_size'], columns='system', values='cell_content')
    # st.dataframe(toPrint1.style.highlight_null(), use_container_width=True)


@st.cache_data
def latestSpaceData():
    tdb = TestDatabase()
    meta = milestones(dbs=['TGS', 'N1', 'PG', 'MA'], datasets=['energy', 'traffic', 'syn'], sizes=['.all'])

    def extract(r):
        # st.write(r)
        s = calcSpace(r['dataset'], r['db'], 'T'+r['tp_size'], tdb)
        return s['sData'], s['sIndex'], s['tData'], s['tIndex'], s['sData'] + s['sIndex'] + s['tData'] + s['tIndex']

    meta[['sd', 'si', 'td', 'ti', 'total']] = meta.apply(extract, axis=1, result_type="expand")
    tdb.close()
    return meta

# 如果转成KM后取整（四舍五入），若整数部分<10，则加一位小数，
def human_format(num, suffix=True, aThousand=1000.0):
    import math
    suffixes = ['', 'K', 'M', 'G', 'T', 'P']
    try:
        m = int(math.log10(num) // 3)
    except ValueError as e:
        st.write(num)
        return 'Err(F)'
    zheng = num / aThousand ** m
    if zheng < 10:
        return f'{zheng:.1f}{suffixes[m] if suffix else ""}'
    else:
        return f'{zheng:.0f}{suffixes[m] if suffix else ""}'


def calcHumanNote(num, suffix=True):
    if num == 0.0 or num == 0:
        return '0'
    else:
        return human_format(num, suffix, aThousand=1024.0)


def figPrintBeauty(data):
    dbMap = {
        'TGS': 'TGraph',
        'N1': 'Neo4j',
        'PG': 'PostgresSQL',
        'MA': 'MariaDB'
    }
    datasetMap = {
        'energy': 'Energy',
        'traffic': 'Traffic',
        'syn': 'SYN',
    }
    return data.replace(dbMap | datasetMap)



def spaceFigure(data):
    hatch_dict = {'TGraph': 'xxx', 'PostgresSQL': 'o', 'MariaDB': '|||', 'Neo4j': '\\/', 'Raw data': '...'} #'PG': 'o', 'MA': '|||', 'Neo4j': '\\/', 'Raw data': '...'}
    palette = sns.color_palette('muted')
    print('palette', palette)
    # 创建柱状图
    barplot = sns.barplot(x="Dataset", y="Disk Space Usage", hue="System", data=data, palette=palette)
    # 设置纵轴坐标数字
    barplot.set_yticklabels([calcHumanNote(v) for v in barplot.get_yticks()])
    # plt.xticks([])
    # plt.yticks([])
    # 设置柱状图上方文字显示
    for i, p in enumerate(barplot.patches):
        _x = p.get_x() + p.get_width() / 2
        _y = p.get_y() + p.get_height()
        if i >= len(data): break
        barplot.text(_x, _y, data["txt"].iloc[i % len(data)], ha="center", fontsize=10)
    # 添加 hatch
    for i, bar in enumerate(barplot.patches):
        print('bar', bar)
        if i > len(data): break
        hatch = hatch_dict[data['System'].iloc[i % len(data)]]
        print('hatch', hatch)
        bar.set_hatch(hatch)

    legendPatch = []
    categories = data['System'].drop_duplicates().reset_index(drop=True)
    for i, label in categories.items():
        hatch = hatch_dict[label]
        legendPatch.append(Patch(facecolor=palette[i], hatch=hatch, label=label))

    plt.legend(handles=legendPatch)

    mpl.rcParams['hatch.linewidth'] = 0.5
    # 创建自定义图例
    # handles = [mpatches.Patch(facecolor='gray', hatch=hatch, label=label) for label, hatch in hatch_dict.items()]
    # plt.legend(handles=handles)

    # 添加标签
    plt.xlabel('Database', fontweight='bold', fontsize=12)
    plt.ylabel('Disk Space Usage (Bytes)', fontweight='bold', fontsize=12)



st.header("空间占用对比图")
st.subheader("总体")
st.subheader("详情")
spaceMeta = latestSpaceData()
with st.expander("空间占用对比图", expanded=True, icon=":material/thumb_up:"):
    # 创建数据框
    data = spaceMeta[['db', 'dataset', 'total']].pivot(index='dataset', columns='db', values='total').reset_index()
    data['Raw data'] = pd.Series([3_961_371_341, 113_591_091_052, 44_137_999_125])
    st.dataframe(data, use_container_width=True)
    df = data[['Raw data', 'MA', 'PG', 'N1', 'TGS', 'dataset']]
    df.rename(columns={'dataset': 'Dataset', 'N1': 'Neo4j', 'TGS': 'TGraph'}, inplace=True)
    # 转化为长格式
    spaceData4fig = figPrintBeauty(df.melt('Dataset', var_name='System', value_name='Disk Space Usage'))
    spaceData4fig['txt'] = spaceData4fig.apply(lambda r: calcHumanNote(r['Disk Space Usage'], False), axis=1)
    col_category = spaceData4fig[['Dataset']].drop_duplicates().reset_index(drop=True)
    # 最终版数据表格
    st.dataframe(spaceData4fig, use_container_width=True)

    # 创建数据图
    plt.figure(figsize=(7, 3))
    spaceFigure(spaceData4fig)
    st.pyplot(plt)

    # 保存到磁盘
    pathDir = "E:\\Projects\\TGraph\\source-code\\paper-tgraph-vldb-2025\\figures"
    plt.savefig(f"{pathDir}\\space-compare.pdf", bbox_inches='tight')
    plt.close()


'''
这里展示了`读写测试`中所有测试项的状态和进展。每个测试项由db、dataset、tp_size、test唯一标识。
每个测试都需要
1. 构建milestone（含有一定数据的基础数据库）
2. 部署1中的milestone到测试机（）
3. 生成benchmark（请求列表）
4. 运行test（启动2中数据库并把3中查询请求发送获得结果）
5. 校验validate（把相同请求在不同系统上得到请求结果进行对比，判断正确性）
'''


def allTests(dbs, datasets, tests, sizes):
    metaList = []
    for db in dbs:  #
        for dataset in datasets:  #
            for mSize in sizes:  # '.01', '.5', '.9',
                for test in tests:  # , 'w5r5', 'w1r3','w3r1'
                    if (test == 'reachable' and (dataset == 'energy' or db == 'TGL')) or (test == 'ehistory' and db == 'TGL'):
                        continue
                    else:
                        metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize, 'test': test})

    for db in ['TGKI', 'TGLI']:
        for dataset in datasets:  #
            for mSize in ['.1', '.all']:  # '.01', '.5', '.9',
                for test in ['aggmax', 'etpc', 'update']:
                    metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize, 'test': test})

    meta = pd.DataFrame(metaList)
    meta['dataset'] = pd.Categorical(meta['dataset'], ["energy", "traffic", "syn"])
    return meta

def latestTestData():
    tdb = TestDatabase()
    meta = allTests(datasets=['energy', 'traffic', 'syn'], sizes=['.all'],
                    dbs=['TGC', 'TGK', 'TGL', 'TGLI', 'TGKI', 'TGS', 'N1', 'N2', 'PG', 'MA'],
                    tests=['ehistory', 'snapshot', 'aggmax', 'etpc', 'reachable', 'update', 'append'])

    def extract(r):
        # st.write(r)
        s = calcSpace(r['dataset'], r['db'], 'T'+r['tp_size'], tdb)
        return s['sData'], s['sIndex'], s['tData'], s['tIndex'], s['sData'] + s['sIndex'] + s['tData'] + s['tIndex']

    meta[['sd', 'si', 'td', 'ti', 'total']] = meta.apply(extract, axis=1, result_type="expand")
    tdb.close()
    return meta



st.header("并发死锁对比")

def hashDefaultGetFunc(item, key):
    return item[key]


def g(row, key):
    return row[key] if key in row else None


class HashResult:
    def __init__(self, data, keys, getFunc=hashDefaultGetFunc):
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


# readWriteTests = ['htap']  #'oltp', 'olap', , 'r100'
dbList = ['TGS', 'TGSB']  # 'PG', 'MA', 'TGC', 'TGK', 'TGKI', 'TGL', 'TGLI', 'N1', 'N2'
datasetList = ['energy', 'traffic']  # , 'syn'
maxCon = [1, 2, 4, 8, 16]  # 6, 10, 12, 14,

# 2024-2-16
readWriteTests = ['oltp']


def allTests():
    metaList = []
    for db in dbList:  #
        for dataset in datasetList:  #
            for mSize in ['.all']:  # '.01', '.5', '.9','.1',
                for test in readWriteTests:  # , 'w5r5', 'w1r3','w3r1'
                    for mCon in maxCon:
                        metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize, 'test': test, 'max_con': mCon})

    def isValid(row):
        db = row['db']
        dataset = row['dataset']
        test = row['test']
        if dataset == 'energy' and test == 'reachable':  # energy数据集无法定义reachable查询（
            return False
        if db.startswith('TGL') and test in ('ehistory', 'reachable'):  # TCypher不支持ehistory的返回值，不支持reachable
            return False
        if db.endswith('I') and test in ['snapshot', 'ehistory', 'reachable']:  # 索引无需查这几项，与无索引同
            return False
        return True

    meta = pd.DataFrame(metaList)
    meta['dataset'] = pd.Categorical(meta['dataset'], ["energy", "traffic", "syn"])
    meta['valid'] = meta.apply(isValid, axis=1)
    meta['req_cnt'] = meta.apply(lambda r: 28000 if r['dataset'] == 'syn' else 100000, axis=1)
    # st.write(len(meta[~meta['valid']]))
    return meta


def addTimeoutMsg(meta):
    def extractT(row):
        tmp = []
        if row['mark'] == 'repair':
            tmp.append('x')
        elif row['mark'] == 'timeout':
            tmp.append('t')
        else:
            tmp.append('-')
        if row['vkt.status'] == 'SUCCESS':
            tmp.append('-')
        elif row['vkt.status'] == 'FAILURE':
            tmp.append('x')
        else:
            tmp.append('?')
        if np.isnan(row['al_l90_t']):
            tmp.append('n')
        else:
            tmp.append('-')
            tmp.append(" {:.0f}".format(row['al_l90_t']))
        ret = ''.join(tmp)
        return ret

    meta[['display_content']] = meta.apply(extractT, axis=1, result_type="expand")
    return meta


def mark2val(row):
    if not pd.isna(row['al_l90_t']):
        lt = human_format(row['al_throughput'])
    else:
        lt = 'none'
    if not pd.isna(row['al_throughput']):
        tp = human_format(row['al_throughput'])
    else:
        tp = 'none'
    return [lt, tp]


def fetchLatestJID(tdb, p__p):
    pp = [0]
    sql = '''SELECT "ID" jid, created_at ct, duration FROM "Jenkins-Test" jt WHERE jt.device='data' and 
        db=%s and dataset=%s and "mTpSize"=%s and test=%s and "maxCon"=%s and reqcnt=%s
        ORDER BY jid DESC LIMIT 10'''

    def hhhh(row):
        resultArr = tdb.execute(sql, (
        row['db'].lower(), row['dataset'], row['tp_size'][1:], row['test'], row['max_con'], row['req_cnt']))
        p__p.progress(pp[0] % 100,
                      text=f":smile: IDing... {row['db']}@{row['dataset']}{row['tp_size']}^{row['max_con']}")
        pp[0] += 1

        if len(resultArr) > 0:
            xxx = [str(tmp['jid']) for tmp in resultArr]
            tmp = resultArr[0]
            return ','.join(xxx), tmp['ct'], tmp['duration']
        else:
            return None, None, None

    return hhhh


def fetchTestDetail(tdb, p__p):
    pp = [0]

    def hhhh(row):
        idArr = tuple(row['jid'].split(','))
        resultArr = tdb.execute('''SELECT SUM(CASE status WHEN 'ok' THEN 1 ELSE 0 END) AS correct_cnt, COUNT(status) cnt,
MAX(EXTRACT(EPOCH FROM create_t)*1000 + execute_time) - MIN(EXTRACT(EPOCH FROM create_t)*1000) AS dur_min,
PERCENTILE_CONT(ARRAY[0, 0.5, 0.9, 0.95, 0.99, 1]) WITHIN GROUP (ORDER BY execute_time) AS exe_t_ql
FROM verified_case WHERE jenkins_id_trial IN %s GROUP BY jenkins_id_trial ORDER BY jenkins_id_trial DESC''', (idArr,))
        p__p.progress(pp[0] % 100,
                      text=f":relieved: loading {row['db']}@{row['dataset']}{row['tp_size']}^{row['max_con']}")
        pp[0] += 2
        if len(resultArr) > 0:
            cntArr = [tmp['cnt'] for tmp in resultArr]
            corrCntArr = [tmp['correct_cnt'] for tmp in resultArr]
            durArr = [tmp['dur_min'] for tmp in resultArr]
            return corrCntArr, cntArr, durArr, resultArr[0]['exe_t_ql']
        else:
            return None, None, None, None

    return hhhh


def calcThroughputPerMin(row):
    cntArr = row['correct_cnt']
    durArr = row['dur_min']
    assert len(cntArr) == len(durArr)
    results = []
    for i in range(0, len(cntArr)):
        results.append(cntArr[i] * 1000 * 60 / durArr[i])
    return results


def calcErrorCntTx(row):
    goodArr = row['correct_cnt']
    totalArr = row['cnt']
    assert len(goodArr) == len(totalArr)
    results = []
    for i in range(0, len(goodArr)):
        results.append(totalArr[i] - goodArr[i])
    return results


def moss550w():
    empty = st.empty()
    p__p = empty.progress(0)
    meta = allTests()
    # 从数据库中查找出最近1000次测试的ID，并根据条件填充给每行（每行只保留最后一次测试的ID）
    tdb = TestDatabase()
    meta[['jid', 'ct', 'duration']] = meta.apply(fetchLatestJID(tdb, p__p), axis=1, result_type="expand")
    tdb.close()
    # st.dataframe(meta, use_container_width=True)
    # 根据测试项ID获取测试详情：运行时长，完成请求数，吞吐量
    tdb = TestDatabase()
    p__p.progress(0, text=':smile: loading test id')
    meta[['correct_cnt', 'cnt', 'dur_min', 'exe_t_ql']] = meta.apply(fetchTestDetail(tdb, p__p), axis=1,
                                                                     result_type="expand")
    tdb.close()
    # 画图
    meta['throughput'] = meta.apply(calcThroughputPerMin, axis=1)
    meta['error_cnt'] = meta.apply(calcErrorCntTx, axis=1)
    st.dataframe(meta, use_container_width=True)
    empty.empty()
    return meta

from itertools import cycle

def simpleDist(data, y, legend=False):
    # 定义填充样式与数据库的对应关系
    hatches = cycle(['x', '///'])
    # fig = sns.stripplot(data=data, x='max_con', y=y, hue='db', jitter=True)
    # fig = sns.boxplot(data=data, x='max_con', y=y, hue='db')
    # sns.lineplot(data=data, x='max_con', y=y, hue='db')
    fig = sns.barplot(data=data, x='max_con', y=y, hue='db', estimator=np.mean)  # , errorbar=('pi', 90), err_kws={'linewidth':1}
    num_locations = len(data['max_con'].unique())
    for j, bar in enumerate(fig.patches):
        if j % num_locations == 0:
            hatch = next(hatches)
        bar.set_hatch(hatch)
    fig.legend(loc='best')
    if not legend:
        plt.legend([], [], frameon=False)


def throughputPlot(meta):
    expandData = meta[['max_con', 'throughput', 'dataset', 'db']].explode('throughput').reset_index()
    fileRoot = "E:\\Projects\\TGraph\\source-code\\博士毕业论文"
    col_category = expandData['dataset'].drop_duplicates().reset_index(drop=True)
    cols = st.columns(len(col_category))
    # st.write(col_category)
    for i, category in col_category.items():
        # 获取特定的数据集的数据
        group = expandData[expandData['dataset'] == category].reset_index(drop=True)
        cols[i % len(col_category)].write(category)
        plt.figure(figsize=(4, 3))
        simpleDist(group, y='throughput', legend=(i % len(col_category) == 1))
        plt.savefig(f"{fileRoot}\\figure\\exp-par-self\\throughput_{category}.pdf")
        cols[i % len(col_category)].pyplot(plt)
        plt.close()


def errorCntPlot(meta):
    expandData = meta[['max_con', 'error_cnt', 'dataset', 'db']].explode('error_cnt').reset_index()
    fileRoot = "E:\\Projects\\TGraph\\source-code\\博士毕业论文"
    col_category = expandData['dataset'].drop_duplicates().reset_index(drop=True)
    cols = st.columns(len(col_category))
    for i, category in col_category.items():
        # 获取特定的数据集的数据
        group = expandData[expandData['dataset'] == category].reset_index(drop=True)
        cols[i % len(col_category)].write(category)
        plt.figure(figsize=(4, 3))
        simpleDist(group, y='error_cnt', legend=(i % len(col_category) == 0))
        plt.savefig(f"{fileRoot}\\figure\\exp-par-self\\error_cnt_{category}.pdf")
        cols[i % len(col_category)].pyplot(plt)
        plt.close()