# -*- coding: utf-8 -*-
"""
Created on 2022/2/24

@author: Song
"""
import numpy as np
import pandas as pd

from DBTestAnalysisLib import *
import streamlit as st

from st_aggrid import *
from streamlit_autorefresh import st_autorefresh
from jenkins import NEED_BUILD, NEED_REPAIR, GOOD

# import plotly.express as px
# import plotly.graph_objects as go
import seaborn as sns


st.set_page_config(page_title="测试概要", page_icon="📈", layout="wide")
lastRefreshTime = datetime.now().strftime("%m-%d %H:%M")
count = st_autorefresh(interval=600000, limit=400, key="counter")
st.sidebar.write(f"刷新页面: {count + 1}/400 {lastRefreshTime}")


def milestones():
    metaList = []
    for db in ['TGS', 'N1', 'PG', 'MA']:  # 'TGC', 'TGK', 'TGKI', 'N2',
        for dataset in ['energy', 'traffic', 'syn']:  #
            for mSize in ['.all']:  # '.01', '.5', '.9','.1',
                metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize})

    meta = pd.DataFrame(metaList)
    return meta
    # toPrint1 = meta.pivot(index=['dataset', 'tp_size'], columns='system', values='cell_content')
    # st.dataframe(toPrint1.style.highlight_null(), use_container_width=True)


readWriteTests = ['ehistory', 'snapshot', 'aggmax', 'etpc', 'reachable', 'update', 'append']  #
dbList = ['TGC', 'TGK', 'TGL', 'TGLI', 'TGKI', 'TGS', 'N1', 'N2', 'PG', 'MA']
datasetList = ['energy', 'traffic', 'syn']

def allTests():
    metaList = []
    for db in ['TGC', 'TGK', 'TGL', 'TGS', 'N1', 'N2', 'PG', 'MA']:  #
        for dataset in datasetList:  #
            for mSize in ['.1', '.all']:  # '.01', '.5', '.9',
                for test in readWriteTests:  # , 'w5r5', 'w1r3','w3r1'
                    if (test == 'reachable' and (dataset == 'energy' or db == 'TGL')) or (test == 'ehistory' and db == 'TGL'):
                        continue
                    else:
                        metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize, 'test': test})

    for db in ['TGKI', 'TGLI']:
        for dataset in datasetList:  #
            for mSize in ['.1', '.all']:  # '.01', '.5', '.9',
                for test in ['aggmax', 'etpc', 'update']:
                    metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize, 'test': test})

    meta = pd.DataFrame(metaList)
    meta['dataset'] = pd.Categorical(meta['dataset'], ["energy", "traffic", "syn"])

    return meta


def latestSpaceData():
    tdb = TestDatabase()
    meta = milestones()

    def extract(r):
        # st.write(r)
        s = calcSpace(r['dataset'], r['db'], 'T'+r['tp_size'], tdb)
        return s['sData'], s['sIndex'], s['tData'], s['tIndex'], s['sData'] + s['sIndex'] + s['tData'] + s['tIndex']

    meta[['sd', 'si', 'td', 'ti', 'total']] = meta.apply(extract, axis=1, result_type="expand")
    tdb.close()
    return meta




def matplotChart():
    import matplotlib.pyplot as plt
    import numpy as np

    # 数据
    databases = ['Raw data size', 'PG', 'MA', 'Neo4j', 'TGraph']
    energy = [3.7, 5.1, 3.6, 7.8, 3.2]
    traffic = [47, 119, 141, 118, 15]
    syn = [105, 328, 383, 418, 42]

    # 创建柱状图
    barWidth = 0.25
    r = np.arange(len(databases))

    fig, axs = plt.subplots(1, 3, figsize=(20, 6))

    # Energy
    axs[0].bar(r, energy, color='grey', width=barWidth, edgecolor='black', hatch='...')
    axs[0].set_title('Energy')
    axs[0].set_xticks(r)
    axs[0].set_xticklabels(databases)

    # Traffic
    axs[1].bar(r, traffic, color='white', width=barWidth, edgecolor='black', hatch='\\\\')
    axs[1].set_title('Traffic')
    axs[1].set_xticks(r)
    axs[1].set_xticklabels(databases)

    # SYN
    axs[2].bar(r, syn, color='grey', width=barWidth, edgecolor='black', hatch='///')
    axs[2].set_title('SYN')
    axs[2].set_xticks(r)
    axs[2].set_xticklabels(databases)

    # 添加标签
    plt.tight_layout()

    # 保存为 PDF
    plt.savefig("output.pdf")
    st.pyplot(plt)
    plt.close()


def simpleSpaceBar(data, x, y):
    # 定义填充样式与数据库的对应关系
    hatch_dict = {'TGraph': 'xxx', 'PG': '*', 'MA': '..', 'Neo4j': '\\/', 'Raw data': '|||'}

    def calcHumanNote(num, suffix=True):
        if num == 0.0 or num == 0:
            return '0'
        else:
            return human_format(num, suffix, aThousand=1024.0)

    data['txt'] = data.apply(lambda r: calcHumanNote(r[y], False), axis=1)
    barplot = sns.barplot(x=x, y=y, data=data)  # hue='DBs', palette="white"
    # plt.yscale('log')
    barplot.set_yticklabels([calcHumanNote(v) for v in barplot.get_yticks()])
    # plt.xticks([])
    # plt.yticks([])
    for i, p in enumerate(barplot.patches):
        _x = p.get_x() + p.get_width() / 2
        _y = p.get_y() + p.get_height()
        barplot.text(_x, _y, data["txt"].iloc[i], ha="center", fontsize=10)

    for j, bar in enumerate(barplot.patches):
        # st.write(j)
        hatch = hatch_dict[data[x].iloc[j]]
        bar.set_hatch(hatch)
    # plt.legend(handles=[mpatches.Patch(facecolor='lightgray', hatch=hatch, label=label) for label, hatch in hatch_dict.items()]) #
    plt.legend([], [], frameon=False)
    # plt.bar(group['DBs'], group['Latency'], fill=False, hatch=hatch_patterns, edgecolor='black')
    barplot.set(xlabel=None)
    barplot.set(ylabel=None)
    # plt.xlabel("DBs")
    # plt.ylabel("Latency")
    # if pd.isna(group['Latency'].max()):
    # continue
    # plt.ylim(0, group['Latency'].max() * 1.1)  # 设置y轴的限制为最大值的110%


def spaceChart(meta):
    import pandas as pd
    import matplotlib.pyplot as plt

    # 创建数据框
    data = meta[['db', 'dataset', 'total']].pivot(index='dataset', columns='db', values='total').reset_index()
    data['Raw data'] = pd.Series([3_961_371_341, 113_591_091_052, 44_137_999_125])
    df = data[['Raw data', 'MA', 'PG', 'N1', 'TGS', 'dataset']]
    df.rename(columns={'dataset': 'Dataset', 'N1': 'Neo4j', 'TGS': 'TGraph'}, inplace=True)
    # data = {'Database': ['Raw data', 'PG', 'MA', 'Neo4j', 'TGraph'],
    #         'Energy': [3.7, 5.1, 3.6, 7.8, 3.2],
    #         'Traffic': [47, 119, 141, 118, 15],
    #         'SYN': [105, 328, 383, 418, 42]}
    # df = pd.DataFrame(data)
    # 转化为长格式
    df_melt = df.melt('Dataset', var_name='Database', value_name='Disk Space Usage')

    col_category = df_melt[['Dataset']].drop_duplicates().reset_index(drop=True)
    # st.write(dataset_operations)
    cols = st.columns(3)
    for i, row in col_category.iterrows():
        # 获取特定的数据集和操作的数据
        group = df_melt[df_melt['Dataset'] == row['Dataset']].reset_index(drop=True)
        # cols[i%3].write(group)
        plt.figure(figsize=(4, 3))
        simpleSpaceBar(group, "Database", 'Disk Space Usage')
        # plt.title(f"{row['Operation']} [{row['Datasets']}]")
        pathDir = "E:\\Projects\\TGraph\\source-code\\博士毕业论文\\figure"
        plt.savefig(f"{pathDir}\\exp-db\\SPACE_{row['Dataset']}.png", bbox_inches='tight')
        cols[i % 3].pyplot(plt)
        plt.close()
    # # 创建柱状图
    # plt.figure(figsize=(7, 5))
    # barplot = sns.barplot(x="Database", y="Disk Space Usage", hue="Dataset", data=df_melt, palette="gray")
    #
    # # 添加 hatch
    # for i, bar in enumerate(barplot.patches):
    #     hatch = hatch_dict[df_melt['Dataset'].iloc[i]]
    #     bar.set_hatch(hatch)
    #
    # # 创建自定义图例
    # handles = [mpatches.Patch(facecolor='gray', hatch=hatch, label=label) for label, hatch in hatch_dict.items()]
    # plt.legend(handles=handles)
    #
    # # 添加标签
    # plt.xlabel('Database', fontweight='bold', fontsize=12)
    # plt.ylabel('Disk Space Usage (GB)', fontweight='bold', fontsize=12)
    # plt.title('Comparison of database disk space usage', fontweight='bold', fontsize=15)
    #
    # # 调整子图位置和间距
    # plt.subplots_adjust(left=0.1, right=0.99, top=0.95, bottom=0.1)
    #
    # # import matplotlib as mpl
    # # mpl.rcParams['hatch.linewidth'] = 0.5
    # plt.savefig("R:\\output.eps")
    # st.pyplot(plt)
    # plt.close()

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

def drawSimpleBar(data, x, y):
    hatch_dict = {'TGraph': '\\/', 'PG': '*', 'MA': '..', 'Neo4j': '|||'}
    def calcHumanNote(row):
        if pd.isna(row[y]) or row[y]>=1800000:
            return 'Timeout', 1, True
        else:
            return human_format(row[y]), row[y], False
    
    def calcValTicks(num):
        if num==0.0 or num==0:
            return '0'
        else:
            return human_format(num)
    
    data[['txt','draw_v', 'timeout']] = data.apply(calcHumanNote, axis=1, result_type="expand")
    hasTimeout = data['timeout'].any()
    # plt.yscale('log')
    barplot = sns.barplot(x=x, y='draw_v', data=data) #hue='DBs', palette="white"
    # if hasTimeout:
    #     barplot.axhline(1800000, ls='--', c='red')
    barplot.set_yticklabels([calcValTicks(v) for v in barplot.get_yticks()])
    # plt.xticks([])
    # plt.yticks([])
    for i, p in enumerate(barplot.patches):
        _x = p.get_x() + p.get_width() / 2
        _y = p.get_y() + p.get_height()
        barplot.text(_x, _y, data["txt"].iloc[i], ha="center", fontsize=10)
    
    for j, bar in enumerate(barplot.patches):
        # st.write(j)
        hatch = hatch_dict[data["DBs"].iloc[j]]
        bar.set_hatch(hatch)
    # plt.legend(handles=[mpatches.Patch(facecolor='lightgray', hatch=hatch, label=label) for label, hatch in hatch_dict.items()]) #
    plt.legend([],[], frameon=False)
    # plt.bar(group['DBs'], group['Latency'], fill=False, hatch=hatch_patterns, edgecolor='black')
    barplot.set(xlabel=None)
    barplot.set(ylabel=None)
    # plt.xlabel("DBs")
    # plt.ylabel("Latency")
    # if pd.isna(group['Latency'].max()):
        # continue
    # plt.ylim(0, group['Latency'].max() * 1.1)  # 设置y轴的限制为最大值的110%

def rwChart():
    import pandas as pd
    import numpy as np

    data = {
        "Datasets": ["Energy", "Energy", "Energy", "Energy",
                     "Traffic", "Traffic", "Traffic", "Traffic",
                     "SYN", "SYN", "SYN", "SYN"],
        # "Operations": ["OLTP Read", "OLTP Read", "OLTP Read", "OLTP Read",
        #                "OLTP Write", "OLTP Write", "OLTP Write", "OLTP Write",
        #                "OLAP", "OLAP", "OLAP", "OLAP"],
        "DBs": ["TGraph", "PG", "MA", "Neo4j", "TGraph", "PG", "MA", "Neo4j", "TGraph", "PG", "MA", "Neo4j"],
        "E-History": [5.0, 7.0, 91, 27e3, 142, 120, 497, np.nan, 53, 25e3, 28e3, np.nan],
        "Snapshot": [375, 3.6e3, 124e3, 26e3, 5.6e3, 523e3, 1.2e6, np.nan, 591e3, 1.8e6, 9.3e6, np.nan],
        "ATP(Max)": [397, 14e3, 123e3, 27e3, 8.5e3, 916e3, np.nan, np.nan, 522e3, 2.3e6, 3.0e6, np.nan],
        "ETPC": [455, 5.6e3, 12e3, 27e3, 4.9e3, 460e3, np.nan, np.nan, 472e3, 1.1e6, np.nan, np.nan],
        "Append": [16, 5.0, 16e3, np.nan, 19, 31, 29e3, np.nan, 244, 2.4e3, 172e3, np.nan],
        "Update": [5.0, 1.1e3, 11e3, np.nan, 36, 2.5e3, 22e3, np.nan, 48, 3.5e3, 1.1e6, np.nan],
        "Reachable Area": [np.nan, np.nan, np.nan, np.nan, 2.6e3, 63e3, 132e3, np.nan, 65, np.nan, np.nan, np.nan]
    }
    df = pd.DataFrame(data)
    st.dataframe(df, use_container_width=True)

    import matplotlib.pyplot as plt
    import matplotlib.ticker as ticker
    import matplotlib.patches as mpatches
    # from matplotlib.font_manager import fontManager, FontProperties
    # path = "path/to/Roboto-Black.ttf"
    # fontManager.addfont(path)
    # prop = FontProperties(fname=path)
    # sns.set(font=prop.get_name())
    sns.set(font="Times New Roman") # 等线
    # 使用melt函数将数据框重塑为长格式
    df_melt = df.melt(id_vars=["Datasets", "DBs"], var_name="Operation", value_name="Latency")
    # st.dataframe(df_melt, use_container_width=True)
    
    hatch_patterns = ['*', 'o', '.', 'O', "x", "/", "\\", "|", "-"]
    # 获取所有数据集和操作的唯一组合
    dataset_operations = df_melt[['Datasets', 'Operation']].drop_duplicates().reset_index(drop=True)
    # st.write(dataset_operations)
    cols = st.columns(3)
    for i, row in dataset_operations.iterrows():
        # 获取特定的数据集和操作的数据
        group = df_melt[(df_melt['Datasets'] == row['Datasets']) & (df_melt['Operation'] == row['Operation'])].reset_index(drop=True)
        # cols[i%3].write(group)
        plt.figure(figsize=(3.6, 1.5))
        if group['DBs'][0]=='Energy' and group['Operation'][0]=='Reachable Area':
            print('blank')        #
        else:
            drawSimpleBar(group, "DBs", "Latency")
        # plt.title(f"{row['Operation']} [{row['Datasets']}]")
        pathDir = "E:\\Projects\\TGraph\\source-code\\博士毕业论文\\figure"
        plt.savefig(f"{pathDir}\\exp-rw\\RW_{row['Datasets']}_{row['Operation']}.pdf", bbox_inches='tight')
        cols[i%3].pyplot(plt)
        plt.close()
    #
    # return plt



st.sidebar.markdown('# 最近状态')
meta = latestSpaceData()
st.dataframe(meta)
# spaceChart(meta)
matplotChart()
# rwChart()
# st.dataframe(rwChart(), use_container_width=True)
st.stop()
