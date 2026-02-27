# -*- coding: utf-8 -*-
"""
Created on 2022/9/29

@author: Song
"""

import numpy as np
import pandas as pd
import math
from DBTestAnalysisLib import *
import plotnine as G
import seaborn as sns
import matplotlib.pyplot as plt
import altair as alt
import plotly.express as px
import streamlit as st
from st_aggrid import *
import plotly.graph_objects as go

st.set_page_config(page_title="读写测试", page_icon="📈", layout="wide")


# empty = st.empty()
# pppp = 0


readWriteTests = ['ehistory', 'snapshot', 'aggmax', 'etpc', 'reachable', 'update', 'append']  #
dbList = ['TGS', 'PG', 'MA', 'N1']  # 'TGC', 'TGL', 'TGLI', 'TGK', 'TGKI', , 'N2'
datasetList = ['energy', 'traffic', 'syn']


def allTests():
    metaList = []
    for db in dbList:  #
        for dataset in datasetList:  #
            for mSize in ['.1', '.all']:  # '.01', '.5', '.9',
                for test in readWriteTests:  # , 'w5r5', 'w1r3','w3r1'
                    metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize, 'test': test})

    def isValid(row):
        db = row['db']
        dataset = row['dataset']
        test = row['test']
        if dataset == 'energy' and test == 'reachable':  # energy数据集无法定义reachable查询
            return False
        if db.startswith('TGL') and test in ('ehistory', 'reachable'):  # TCypher不支持ehistory的返回值，不支持reachable
            return False
        if db.endswith('I') and test in ['snapshot', 'ehistory', 'reachable']:  # 索引无需查这几项，与无索引同
            return False
        return True

    meta = pd.DataFrame(metaList)
    meta['dataset'] = pd.Categorical(meta['dataset'], ["energy", "traffic", "syn"])
    meta['valid'] = meta.apply(isValid, axis=1)
    # st.write(len(meta[~meta['valid']]))
    return meta.loc[meta['valid'] & (meta['tp_size'] == '.all'), ]


def jTests():
    tdb = TestDatabase()
    recordList = tdb.getTest()
    tdb.close()
    records = list(filter(lambda r: r['maxCon'] == 1 and r['mark'] != 'skip', recordList))
    return records


def jRWTest(meta, testList):
    cache = HashResult(testList, ['test', 'dataset', 'db', 'mTpSize'])

    def extractT(row):
        dataset = row['dataset']
        tp_size = row['tp_size']
        db = row['db']
        test = row['test']
        if test is not None and row['valid']:
            try:
                cache_item = cache.get(test, dataset, db.lower(), tp_size[1:])
            except TypeError as e:
                st.write(row)
                raise e

            if len(cache_item) > 0:
                t = cache_item[0]
                ctime = t['created_at'].strftime("%m-%d %H:%M")
                status = t['status'] if 'status' in t else t['phase']
                # st.write(test, dataset, db.lower(), tp_size[1:], ctime, status)
                mark = t['mark'] if 'mark' in t else '-'
                # if ('mark' in t) and t['mark'] is not None:
                #     print(t['mark'], type(t['mark']))
                return [ctime, status, mark, t['ID'], t['reqcnt']]
            else:
                return [None, None, '-', None, None]
        else:
            return [g(row, 'L1.ct'), g(row, 'L1.j_status'), g(row, 'L1.mark'), g(row, 'L1.jid'), g(row, 'L1.qcnt')]

    meta[['L1.ct', 'L1.j_status', 'L1.mark', 'L1.jid', 'L1.qcnt']] = meta.apply(extractT, axis=1, result_type="expand")
    return meta


def abstractTestInfo(meta):
    jidArr = tuple(list(map(lambda f: str(int(f)), meta[meta['L1.jid'] > 0]['L1.jid'])))
    # st.write(type(jidArr))
    tdb = TestDatabase()
    resultArr = tdb.execute('''SELECT "ID" as id, COUNT(vc.status) correct_cnt,
PERCENTILE_CONT(ARRAY[0, 0.5, 0.9, 0.95, 0.99, 1]) WITHIN GROUP (ORDER BY execute_time) AS exe_t_ql
FROM "Jenkins-Test" AS jt LEFT JOIN verified_case AS vc ON vc.jenkins_id_trial=jt."ID" 
WHERE jt.device='data' and jt."ID" IN %s GROUP BY "ID"''', (jidArr,))
    tdb.close()
    result = pd.DataFrame(resultArr)
    result.rename(columns={'id': 'L1.jid', 'exe_t_ql': 'L1.tql', 'correct_cnt': 'L1.correct_cnt'}, inplace=True)
    # st.dataframe(meta, use_container_width=True)
    merged = pd.merge(meta, result, how="left", on=["L1.jid"])  # , validate="one_to_one"
    # st.dataframe(merged, use_container_width=True)
    # split_df = pd.DataFrame(merged['exe_t_ql'].tolist()) #,columns=['l0min', 'l50half', 'l90', 'l95', 'l99', 'l100max'])
    # df = pd.concat([merged, split_df], axis=1)
    # st.dataframe(df, use_container_width=True)
    return merged


# expandData = meta.explode('al.raw').reset_index()
# st.write(meta.columns)
# 如果转成KM后取整（四舍五入），若整数部分<10，则加一位小数，
def human_format(num):
    suffixes = ['', 'K', 'M', 'G', 'T', 'P']
    m = int(math.log10(num) // 3)
    zheng = num / 1000.0 ** m
    if zheng < 10:
        return f'{zheng:.1f}{suffixes[m]}'
    else:
        return f'{zheng:.0f}{suffixes[m]}'


def mark2val(row):
    if not row['valid']:
        return [1_800_000, 'IGNORE', 'skip']
    if row['L1.mark'] == 'timeout':
        return [1_800_000, 'TIMEOUT', 'timeout']
    if row['L1.mark'] == 'repair':
        return [1_800_000, 'ERROR', 'err']
    else:
        t = row['L1.tql']
        if type(t) == list:
            return [t[2], human_format(t[2]), '-']
        else:
            return [None, 'None', '-']

# def alTest(row):
#     p.progress(pp[0] / totalCnt)
#     pp[0] += 1
#     testFullName = row['tname']
#     if type(testFullName) == str:
#         try:
#             # st.write(testFullName)
#             raw = fetchLog(testFullName)
#             qCnt = len(raw)
#             if qCnt > 0:  # missingCnt += 1
#                 raw = extractParam2Col(raw, ['id'])
#                 succReq = raw.loc[raw['txSuccess'].astype('bool')]
#                 succCnt = len(succReq)
#                 avgT = np.mean(succReq['exeTime'])
#                 l90 = succReq['exeTime'].quantile(.9)
#                 std = np.std(succReq['exeTime'])
#                 return [qCnt, succCnt, avgT, l90,
#                         list(zip(list(succReq['id']), list(succReq['sendTime']), list(succReq['exeTime']))),
#                         "{:.0f}±{:.0f}({:.0f})".format(avgT, std, succCnt)]
#         except DataNotReadyErr:
#             pass
#     return [None, None, None, 0, [], None]
#
# records[['qcnt', 'succ_cnt', 'avg_t', 'l90_t', 'raw', 'content']] = records.apply(alTest, axis=1,
#                                                                                   result_type="expand")
# # st.dataframe(records, use_container_width=True)
# data = records.explode('raw').reset_index()
# data[['txid', 'send_time', 'latency']] = data.apply(lambda row: row['raw'], axis=1, result_type="expand")
# data = data.sort_values('send_time', ascending=True)
# data = data.reset_index(drop=True)
# st.dataframe(data, use_container_width=True)

def plotlyChart(meta, logY=True, title=''):
    ffg = px.bar(meta, y="al_l90_t", x="db", color="db", pattern_shape="status",
                 facet_row="test",
                 facet_col="dataset",
                 facet_col_spacing=0.05,
                 facet_row_spacing=0.08,
                 log_y=logY,
                 text="label2show",
                 hover_data=["db", "tp_size", "lct_ct", "al_l90_t", "al_succ_cnt", "al_qcnt"],
                 title=title,
                 labels={
                     'al_l90_t': 'request 90% latency (ms)',
                 },
                 color_discrete_map={
                     'TGC': '#0081cf',
                     'TGK': '#4e78cf',
                     'TGKI': '#00a4de',
                     'TGL': '#00c2d0',
                     'TGLI': '#00dbad',
                     'TGS': '#96ee86',
                     'N1': '#ffaa6c',
                     'N2': '#ee8683',
                     'PG': '#c27294',
                     'MA': '#cba451'
                 }
                 # color_discrete_sequence=['#0081cf', '#4e78cf', '#00a4de', '#00c2d0', '#00dbad', '#96ee86','#ffaa6c', '#ee8683', '#c27294', '#cba451'],
                 )
    ffg.update_traces(width=1.0, textfont_size=12, textangle=0, textposition="outside", cliponaxis=False)
    ffg.update_layout(barmode='group', bargap=0.0, bargroupgap=0.0)
    fgg = ffg.update_yaxes(matches=None, showticklabels=False)
    fgg.for_each_annotation(lambda a: a.update(text=a.text.split("=")[-1]))

    def removeAxisTitle(fig, yTitle):
        for axis in fig.layout:
            if type(fig.layout[axis]) == go.layout.YAxis:
                fig.layout[axis].title.text = ''
            if type(fig.layout[axis]) == go.layout.XAxis:
                fig.layout[axis].title.text = ''
        fig.update_layout(
            # keep the original annotations and add a list of new annotations:
            annotations=list(fig.layout.annotations) + [
                go.layout.Annotation(
                    x=-0.02,
                    y=0.5,
                    font=dict(
                        size=14
                    ),
                    showarrow=False,
                    text=yTitle,
                    textangle=-90,
                    xref="paper",
                    yref="paper"
                )
            ]
        )
        return fig

    cht = removeAxisTitle(fgg, '90%请求的执行延迟(毫秒)')
    st.plotly_chart(cht)  # , use_container_width=True
    return cht


def showAgTable(meta):
    gd = GridOptionsBuilder.from_dataframe(meta)
    gd.configure_default_column(groupable=True, sorteable=True, enableRowGroup=True, enablePivot=True, enableValue=True,
                                allowedAggFuncs=['sum', 'avg', 'count', 'min', 'max', 'first', 'last'])
    gd.configure_columns(['al.raw'], cellRenderer='agSparklineCellRenderer', cellRendererParams={
        'sparklineOptions': {
            'type': 'column'
        }
    })
    gd.configure_columns(['al_succ_rate'], cellRenderer='agSparklineCellRenderer', cellRendererParams={
        'sparklineOptions': {
            'type': 'bar',
            # 'label': {
            #     'enabled': True,
            #     'formatter': JsCode('function({value}){return `{value}%`;}').js_code,
            # },
        }
    })
    # gd.configure_columns(['al_l90_t'], cellRenderer='agSparklineCellRenderer', cellRendererParams={
    #     'sparklineOptions': {
    #         'type': 'bar',
    #         'label': {
    #             'enabled': True,
    #             'formatter': JsCode('function({value}){return `{value}`;}').js_code,
    #         },
    #     }
    # })
    gd.configure_columns(['his_l90', 'his_qcnt', 'his_succ_rate'], cellRenderer='agSparklineCellRenderer',
                         cellRendererParams={
                             'sparklineOptions': {
                                 'type': 'column',
                                 # 'label': {
                                 #     'enabled': True,
                                 # }
                             }
                         })
    gd.configure_grid_options(
        getRowId=JsCode(
            '''function(params){var d=params.data;return d.db+'_'+d.dataset+'_'+d.tp_size+'_'+d.test;}''').js_code,
    )
    table = AgGrid(meta,
                   gridOptions=gd.build(),
                   allow_unsafe_jscode=True,
                   width='100%',
                   reload_data=False,
                   fit_columns_on_grid_load=True,
                   enable_enterprise_modules=True,
                   data_return_mode=DataReturnMode.FILTERED_AND_SORTED,
                   update_mode=GridUpdateMode.NO_UPDATE)
    return table


def testHistorySuccRate(testInDB, test, db, dataset, tp_size):
    st.info(db + '@' + dataset + tp_size + ' ' + test)
    st.snow()
    meta = pd.DataFrame(testInDB)
    # st.dataframe(meta, use_container_width=True)
    records = meta[(meta['test'] == test) & (meta['db'] == db.lower()) & (meta['dataset'] == dataset) & (
            meta['mTpSize'] == tp_size[1:]) & (meta['maxCon'] == 1)]
    st.dataframe(records.set_index('ID'), use_container_width=True)

    jidList = list(records['ID'])
    # st.write(jidList)
    if len(jidList) == 0:
        st.warning('未找到历史记录')
        return
    tdb = TestDatabase()
    recordList = tdb.getTestListDetail(jidList)
    tdb.close()
    if len(recordList) == 0:
        st.warning(f'有{len(jidList)}条测试记录，但未找到详细数据')
        return
    data = pd.DataFrame(recordList)
    data = data.sort_values('create_t', ascending=True)
    data.reset_index(drop=True, inplace=True)
    # st.dataframe(data, use_container_width=True)
    # st.stop()
    # import matplotlib.pyplot as plt
    # g = sns.FacetGrid(data, col='jenkins_id')  # 按行绘制col='class'
    # g.map(plt.bar, 'create_t', 'execute_time')  # 设置绘图模式, color='#C21F30'
    # g.fig.set_size_inches(12, 6)
    # sns.set(style='whitegrid', font_scale=1.5)
    # st.pyplot(plt)
    # st.stop()
    ffg = px.bar(data, y="execute_time", x="create_t", color="status",
                 # facet_col_wrap=5, # pattern_shape="mtpSize", "send_time"
                 # facet_row="jenkins_id",
                 facet_col="jenkins_id",
                 # log_y=True,
                 # text="label2show",
                 # hover_data=["db", "lct_ct", "al_l90_t", "al_succ_cnt", "al_qcnt"],
                 # color_discrete_sequence=['#ffaa6c', '#ee8683', '#c27294', '#cba451', '#0081cf', '#4e78cf', '#00a4de',
                 #                          '#00c2d0', '#00dbad', '#96ee86'],
                 )
    ffg.update_traces(width=1.0, textfont_size=12, textangle=0, textposition="outside", cliponaxis=False)
    ffg.update_layout(barmode='group', bargap=0.0, bargroupgap=0.0)
    # fgg = ffg.update_yaxes(matches=None, showticklabels=True)
    fgg = ffg.update_xaxes(matches=None, showticklabels=True)
    st.plotly_chart(fgg)
    # ffg.write_image(f'images/rw-history/{db}_{dataset}_{tp_size}_{test}.pdf')
    st.balloons()


def availableBenchmark(metaPivoted):
    bCache = HashResult(benchmark(), ['dataset', 'tp_size', 'test'])

    def set2row(row):
        items = bCache.get(row['dataset'], row['tp_size'], row['test'])
        return ','.join(map(lambda i: f"{i['qcnt']}" if i['qcnt'] == i['f.l.cnt'] else f"{i['qcnt']}({i['f.l.cnt']})", items))

    metaPivoted['qCnt'] = metaPivoted.apply(set2row, axis=1)
    return metaPivoted


empty = st.empty()
p__p = empty.progress(0, text="检索全部测试")
testInMetaDB = jTests()
p__p.progress(20, text="排序、过滤中")
meta = allTests()
meta = jRWTest(meta, testInMetaDB)
p__p.progress(40, text="统计最后一次测试（L0）信息")
meta = abstractTestInfo(meta)
p__p.progress(60, text="检索全部测试")

meta[['L1.t90', 'L1.label2show', 'L1.status']] = meta.apply(mark2val, axis=1, result_type="expand")
# meta = meta[~pd.isna(meta['al_l90_t'])]

# showRunning = st.sidebar.checkbox('显示正在运行', False)
columnList = list(meta.columns[5:-1])
columnList.reverse()
mainContent = st.sidebar.selectbox('主要内容', columnList)
# extraContent = st.sidebar.selectbox('次要内容', ('.all', '.1'))
# selectedDataset = st.sidebar.multiselect('Dataset', datasetList, datasetList)
# selectedDBs = st.sidebar.multiselect('DB', dbList, ['TGS', 'PG', 'MA', 'N1'])
# selectedTests = st.sidebar.multiselect('Test', readWriteTests, readWriteTests)
# dataSize = st.sidebar.selectbox('Data size', ('.all', '.1'))

# st.dataframe(meta, use_container_width=True)
toShow = meta.pivot(index=['dataset', 'test', 'tp_size'], columns='db', values=mainContent).reset_index()
p__p.progress(65, text="查询可用的测试请求集合")
toShow = availableBenchmark(toShow)
p__p.progress(100, text="制作表格中")
# st.dataframe(toShow, use_container_width=True)

from st_table_select_cell import st_table_select_cell

selected = st_table_select_cell(toShow)
if selected:
    rowId = selected['rowId']
    colIndex = selected['colIndex']
    # st.write(rowId, colIndex)
    if colIndex > 1:
        row = toShow.iloc[int(rowId)].to_dict()
        db = toShow.columns[colIndex]
        # st.write(, )
        # st.write(data.iat[int(rowId), colIndex])
        testHistorySuccRate(testInMetaDB, row['test'], db, row['dataset'], '.all')
else:
    st.write('no select')

empty.empty()
