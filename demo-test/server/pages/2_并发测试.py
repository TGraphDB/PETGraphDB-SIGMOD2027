# -*- coding: utf-8 -*-
"""
Created on 2022/9/29

@author: Song
"""
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import math
from DBTestAnalysisLib import *
# import plotly.express as px
import streamlit as st
from st_aggrid import *
import seaborn as sns
plt.style.use('seaborn-v0_8-paper')

st.set_page_config(page_title="并发测试", page_icon="📈", layout="wide")


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


# expandData = meta.explode('al.raw').reset_index()
# st.write(meta.columns)
# 如果转成KM后取整（四舍五入），若整数部分<10，则加一位小数，
def human_format(num):
    if num < 1:
        return f'{num:.1f}'
    suffixes = ['', 'K', 'M', 'G', 'T', 'P']
    m = int(math.log10(num) // 3)
    zheng = num / 1000.0 ** m
    if zheng < 10:
        return f'{zheng:.1f}{suffixes[m]}'
    else:
        return f'{zheng:.0f}{suffixes[m]}'


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


def plotlyChart(meta):
    expandData = meta[['max_con', 'throughput', 'dataset', 'db', 'ct', 'correct_cnt', 'dur_min']].explode(
        'throughput').reset_index()
    fgl = px.scatter(expandData, y="throughput", x="max_con", color="db",  # pattern_shape="status",
                     # facet_row="test",
                     # facet_col="dataset",
                     # log_y=True,
                     # text="throughput",
                     # hover_data=["db", "ct", "correct_cnt", 'dur_min'],
                     # color_discrete_map={
                     #     'TGC': '#0081cf',
                     #     'TGK': '#4e78cf',
                     #     'TGKI': '#00a4de',
                     #     'TGL': '#00c2d0',
                     #     'TGLI': '#00dbad',
                     #     'TGS': '#96ee86',
                     #     'N1': '#ffaa6c',
                     #     'N2': '#ee8683',
                     #     'PG': '#c27294',
                     #     'MA': '#cba451'
                     # }
                     )  # .update_yaxes(matches=None, showticklabels=False)
    fgl.update_traces(textfont_size=12, textposition="top center",
                      texttemplate="%{text:.0f}")  # width=1.0,, cliponaxis=Falsetextangle=0,
    # fgb = px.bar(meta, y="al_l90_t", x="max_con", facet_row="test", facet_col="dataset",
    #              text="labelL90",
    #              ).update_traces(marker_color="#cccccc", textfont_size=12, textposition="outside").update_yaxes(
    #     matches=None, showticklabels=False)
    # fgb.add_traces(fgl.data)
    # ffg.update_layout(barmode='group', bargap=0.0, bargroupgap=0.0)
    # fgg = ffg.update_yaxes(matches=None, showticklabels=True)
    fgg = fgl
    st.plotly_chart(fgg)  # , use_container_width=True
    # if st.button('导出'):
    #     fgg.write_image('')


def resultTable(meta):
    return meta.pivot(index=['dataset', 'max_con'], columns='db', values='al_succ_cnt')


meta = moss550w()
# # st.dataframe(meta.loc[:, ~meta.columns.str.contains('^lct.his')], use_container_width=True)
# selectedDBs = st.multiselect('DB', dbList, ['TGC', 'TGS', 'TGK', 'PG', 'MA', 'N1', 'N2'])
# selectedTests = st.multiselect('Test', readWriteTests, ['htap'])
# selectedX = st.multiselect('MaxCon', maxCon, [1, 16])
# # logY = st.sidebar.checkbox('Log-Y Axis', True)
# # showSkip = st.sidebar.checkbox('Show skip', False)
# meta = meta.loc[meta['db'].isin(selectedDBs) & meta['test'].isin(selectedTests) & meta['max_con'].isin(selectedX), ]
throughputPlot(meta)
errorCntPlot(meta)
# plotlyChart(meta)
st.stop()
# st.dataframe(resultTable(meta.loc[:, ['dataset', 'db', 'test', 'max_con', 'al_qcnt', 'al_succ_cnt']]))
# meta.sort_values(by=['tp_size', 'dataset', 'test'], inplace=True, ascending=True)
# meta = meta[['db', 'tp_size', 'dataset', 'test', 'lct_ct', 'al_l90_t']]
# tgk = meta[meta['db'] == 'TGK']
# tgk = tgk[['tp_size', 'dataset', 'test', 'al_l90_t']]
# joined = pd.merge(meta, tgk, how='left',
#                   left_on=['tp_size', 'dataset', 'test'],
#                   right_on=['tp_size', 'dataset', 'test'])
#
#
# def smallerThanTGK(df):
#     c1 = 'background-color: #0ff'
#     c2 = ''
#     mask = (df['al_l90_t_x'] < df['al_l90_t_y']) & df['db'].isin(['PG', 'MA', 'N1', 'N2'])
#     # DataFrame with same index and columns names as original filled empty strings
#     df1 = pd.DataFrame(c2, index=df.index, columns=df.columns)
#     # modify values of df1 column by boolean mask
#     df1.loc[mask, 'db'] = c1
#     return df1
#
#
# fj = joined.style.apply(smallerThanTGK, axis=None)
# st.dataframe(fj)
# # ret = AgGrid(joined, update_mode=GridUpdateMode.MANUAL)
