# -*- coding: utf-8 -*-
"""
Created on 2022/2/24

@author: Song
"""
import pandas as pd
import plotnine as G
import altair as alt
import streamlit as st
from st_aggrid import *

import jenkins
from DBTestAnalysisLib import *

st.set_page_config(page_title="数据库状态&测试历史", page_icon="📈", layout="wide")


def milestones():
    metaList = []
    for dataset in ['energy', 'traffic', 'syn']:  #
        for db in ['TGS', 'N1', 'PG', 'MA', 'TGKI', 'TGSB']:  # 'TGC', 'TGK', 'N2',
            for mSize in ['.1', '.all']:  # '.01', '.5', '.9',
                metaList.append({'db': db, 'dataset': dataset, 'tp_size': mSize})
            if db == 'N1' or db == 'N2':
                metaList.append({'db': db, 'dataset': dataset, 'tp_size': '.0'})
    meta = pd.DataFrame(metaList)
    return meta


def lastWriteTest(meta):
    tdb = TestDatabase()
    recordList = tdb.getTest()
    tdb.close()
    results = list(filter(lambda t: t['test'] in ['update', 'append', 'oltp', 'olap', 'htap'], recordList))
    tc = HashResult(results, ['dataset', 'db', 'mTpSize'])

    def extractor(rowDict):
        writeTests = tc.get(rowDict['dataset'], rowDict['db'].lower(), rowDict['tp_size'][1:])
        if len(writeTests) > 0:
            lastWT = writeTests[0]['created_at']
            # st.write(type(lastWT))
            # st.stop()
            return [lastWT]
        else:
            return [None]

    meta[['L1.tw_ct']] = meta.apply(extractor, axis=1, result_type="expand")
    return meta


def milestoneBuild(meta):
    tdb = TestDatabase()
    records = tdb.getBuild()
    tdb.close()
    cache = HashResult(records, ['dataset', 'db', 'tpSize'])

    def extractor(rowDict):
        builds = cache.get(rowDict['dataset'], rowDict['db'].upper(), 'T' + rowDict['tp_size'])
        if len(builds) > 0:
            item = builds[0]
            ct = item['created_at']  # .strftime("%Y-%m-%d %H:%M")
            status = item['status'] if 'status' in item else item['phase']
            device = item['device']
            tdiff = (datetime.now() - ct).total_seconds() / 3600 / 24
            if len(builds) > 1:
                pre = builds[1]
                pct = pre['created_at']  # .strftime("%Y-%m-%d %H:%M")
                pstatus = pre['status'] if 'status' in pre else pre['phase']
            else:
                pct = None
                pstatus = None
            return [ct, f'{tdiff:.1f}天前', status, device, pct, pstatus]
        else:
            return [None, None, None, None, None, None]

    meta[['L1.m_ct', 'L1.m_ct2now', 'L1.m_status', 'L1.m_device', 'L2.m_ct', 'L2.m_status']] = meta.apply(extractor,
                                                                                                          axis=1,
                                                                                                          result_type="expand")
    return meta


def milestoneDeploy(meta):
    tdb = TestDatabase()
    records = tdb.getDeploy()
    tdb.close()
    cache = HashResult(filter(lambda r: r['target'] == 'data', records), ['dataset', 'db', 'tpSize'])

    def extractor(rowDict):
        deploys = cache.get(rowDict['dataset'], rowDict['db'].lower(), rowDict['tp_size'][1:])
        if len(deploys) > 0:
            item = deploys[0]
            ctObj = item['created_at']
            # ct = ctObj.strftime("%Y-%m-%d %H:%M")
            status = item['status'] if 'status' in item else item['phase']
            tdiff = (datetime.now() - ctObj).total_seconds() / 3600 / 24
            return [ctObj, status, f'{tdiff:.1f}天前']
        else:
            return [None, None, None]

    meta[['L1.d_ct', 'L1.d_status', 'L1.d_ct2now']] = meta.apply(extractor, axis=1, result_type="expand")
    return meta


def normalizeJob(p):
    if 'benchmark_name' in p:
        a = p['milestone'].split('_')
        b = p['benchmark_name'].split("_")
        return {
            'job': 'test',
            'dataset': a[1],
            'db': a[2].upper(),
            'tp_size': '.' + a[3],
            'test': b[3],
            'q_cnt': b[4],
            'max_con': p['max_connection']
        }
    elif 'dbtype' in p:
        arr = p['milestone'].split('_')
        return {
            'job': 'deploy',
            'dataset': arr[1],
            'db': arr[2].upper() if p['dbtype'] != 'tcypher' else arr[2].upper().replace('TGK', 'TGL'),
            'tp_size': '.' + arr[3]
        }
    elif 'dataset' in p:
        return {
            'job': 'build',
            'dataset': p['dataset'],
            'db': p['db_name'].upper(),
            'tp_size': p['data_size'][1:],
            'device': p['device']
        }
    else:
        st.warning('正在运行未识别的任务')
        st.stop()


def calcAction(meta, waitingJobs):
    def calculator(row):
        db = row['db']
        dataset = row['dataset']
        tp_size = row['tp_size']
        jList = waitingJobs.get(dataset, db, tp_size)
        if len(jList) > 0:
            return [jList[0]['status'] + '-' + ','.join(
                [j['job'][0].upper() + (f"({j['test']})" if j['job'] == 'test' else "")
                 for j in jList])]

        lastBuildTime = row['L1.m_ct']
        lastDeployTime = row['L1.d_ct']
        lastWriteTestTime = row['L1.tw_ct']
        buildDevice = row['L1.m_device']
        if (pd.isna(lastBuildTime) or row['L1.m_status'] != 'SUCCESS') and db != 'TGSB':
            return ['build']
        if pd.isna(lastDeployTime) or row['L1.d_status'] != 'SUCCESS':
            return ['test'] if buildDevice == 'data' else ['deploy']
        if pd.isna(lastWriteTestTime):
            return ['test']

        if buildDevice == 'data' and lastDeployTime < lastBuildTime:
            lastDeployTime = lastBuildTime
        if lastWriteTestTime > lastDeployTime:
            action = 'deploy'
        else:
            action = 'test'
        if action == 'deploy' and db.upper() == 'MA':
            action = 'build'
        return [action]

    meta[['action']] = meta.apply(calculator, axis=1, result_type="expand")
    return meta


def calcHistory(db, dataset, tp_size):
    tdb = TestDatabase()
    builds = tdb.execute(
        '''SELECT 'Build' as action, '#'||"ID" as id, phase, status, device, duration, created_at, updated_at
        FROM "Jenkins-Build" WHERE db=%s AND dataset=%s AND "tpSize"=%s''',
        (db.upper(), dataset, 'T' + tp_size))
    deploys = tdb.execute(
        '''SELECT 'Deploy' as action, '#'||"ID" as id, phase, status, target as device, duration, created_at, updated_at
        FROM "Jenkins-Deploy" WHERE db=%s AND dataset=%s AND "tpSize"=%s''',
        (db.lower(), dataset, tp_size[1:]))
    tests = tdb.execute(
        '''SELECT 'Test(' || test || ')' as action, '#'||"ID" as id, phase, status, device, duration, created_at, updated_at, mark, reqcnt, "bTpRange", "maxCon", "hasResult"
        FROM "Jenkins-Test" WHERE db=%s AND dataset=%s AND "mTpSize"=%s''',
        (db.lower(), dataset, tp_size[1:]))
    tdb.close()
    st.markdown(
        f'历史上对该测试项共进行{len(builds) + len(deploys) + len(tests)}次操作，其中构建{len(builds)}次，部署{len(deploys)}次，测试{len(tests)}次。')
    allDf = pd.DataFrame(builds + deploys + tests)
    allDf.sort_values('created_at', ascending=False, inplace=True)
    return allDf.reset_index(drop=True)


def actionRow(device, action, db, dataset, tp_size, test, qCnt, maxCon, saveResult):
    try:
        if action == 'build':
            b = jenkins.build(action, {
                'dataset': dataset,
                'data_size': 'T' + tp_size,
                'db_name': db.upper(),
                'device': device
            })
            info = ("Build " + db + '@' + dataset + tp_size + ' on ' + device)
        elif action == 'deploy':
            dbType = 'postgresql' if db.lower() == 'pg' else 'neo4j'
            if 'tgl' in db.lower():
                dbType = 'tcypher'
                db = db.lower().replace('tgl', 'tgk')
            else:
                db = db.lower()
            b = jenkins.build(action, {
                'target_host': device,
                'milestone': 'm_' + dataset + '_' + db + '_' + tp_size[1:],
                'dbtype': dbType
            })
            info = ("Deploy " + db + '@' + dataset + tp_size)
        elif action == 'test':
            needResult = 'true' if saveResult else 'false'
            b = jenkins.build(action, {
                'db_host': 'data',
                'milestone': 'm_' + dataset + '_' + db.lower() + '_' + tp_size[1:],
                'benchmark_name': 'b_' + dataset + '_T' + tp_size + '_' + test + '_' + str(int(qCnt)),
                'max_connection': str(maxCon),
                'need_result': needResult,
                'debug': 'false',
                'timeout': 3600,
                'timeout_p': 1800,
            })
            info = ("Test " + test + ':' + db + '@' + dataset + tp_size + ' on ' + device + (
                '(no result)' if needResult == 'false' else ''))
        elif action == 'validate':
            b = jenkins.build(action, {
                'db_host': device,
                'db_list': ','.join(db),
                'dataset': dataset,
                'test': test,
                'tp_range': 'T' + tp_size,
                'req_cnt': qCnt,
                'skip_sample': 'true',
                'skip_test': 'true',
                'skip_validate': 'false',
                'max_connection': 1,
                'timeout': 1800,
                'failfast': 'false',
            })
            info = ("Validate " + ','.join(db) + '@' + dataset + tp_size)
        else:
            from Abstract import NotReadyErr
            raise NotReadyErr(action, 'unknown action')
    except Exception as e:
        st.sidebar.error(e)
    st.snow()

def setMark(action, id, mark):
    tdb = TestDatabase()
    tdb.commit(f'''UPDATE "Jenkins-{action}" SET mark=%s WHERE "ID"=%s''', (mark, id))
    tdb.close()
    st.info('已设置，请刷新页面')

metaList = []
knownCnt = 1  # 已知项个数
missingCnt = 0  # 缺失项个数：缺失项总时间估计=missingCnt*已知总时间/已知cnt
completeTime = 0  # 无需rebuild项的总时间
rebuildTime = 0  # 需要rebuild项的总时间

with st.spinner('更新测试集群状态，请稍等...'):
    nodeList = ['data', 'ssworkstation', 'zhworkstation']
    cols = st.columns([3, 2, 2])
    runningJobs = {}
    for i in range(0, 3):
        node = nodeList[i]
        nodeName = node.replace('workstation', '').upper()
        nodeObj = jenkins.j.nodes.get(node)
        jNode = nodeObj.api_json()
        if jNode['offline']:
            cols[i].error(f"{nodeName}: 掉线")
        elif jNode['idle']:
            cols[i].success(f"{nodeName}: 空闲")
        else:
            for build in nodeObj.iter_builds():
                job = build.api_json()
                # st.write(job)
                # st.stop()
                params = normalizeJob(jenkins.param2dic(build.get_parameters()))
                params['ct'] = datetime.fromtimestamp(int(job['timestamp'] / 1000))
                params['status'] = 'running'
                params['jid'] = job['id']
                runningJobs[job['id']] = params
                # jName = job['fullDisplayName'].replace('milestone-build-master', 'build').replace('milestone-deploy', 'deploy')
                cols[i].info(f"{nodeName}: #{job['id']} {job['description']}")

    waitingList = [runningJobs[jid] for jid in runningJobs]
    for qi in jenkins.j.queue.api_json()['items']:
        if 'name' in qi['task']:
            job = normalizeJob(jenkins.strParams2dic(qi['params']))
            job['ct'] = datetime.fromtimestamp(int(qi['inQueueSince'] / 1000))
            job['status'] = 'wait'
            waitingList.append(job)

    waitingList.sort(key=lambda j: j['ct'])
    queuedJobs = HashResult(waitingList, ['dataset', 'db', 'tp_size'])

actionList = ('build', 'deploy', 'test')
action = False


def shouldSaveResult(test, dataset, maxCon):
    if dataset == 'syn':
        return False
    if maxCon > 1:
        return False
    if test in ['append', 'update', 'htap', 'oltp', 'olap']:
        return False
    return True


with st.spinner('计算中，请稍等...'):
    meta = milestones()
    meta = meta[meta['tp_size'] == '.all']
    meta = milestoneBuild(meta)
    meta = milestoneDeploy(meta)
    meta = lastWriteTest(meta)
    meta = calcAction(meta, queuedJobs)

    # st.markdown('## 最近一次构建时间')
    mainContent = st.sidebar.selectbox('表格单元格内容', ['action'] + list(meta.columns[3:-1]))
    toShow = meta.pivot(index=['dataset', 'tp_size'], columns='db', values=mainContent).reset_index()
    from st_table_select_cell import st_table_select_cell

    selected = st_table_select_cell(toShow)
    if selected:
        rowId = selected['rowId']
        colIndex = selected['colIndex']
        # st.write(rowId, colIndex)
        if colIndex > 1:
            row = toShow.iloc[int(rowId)].to_dict()
            db = toShow.columns[colIndex]
            dataset = row['dataset']
            tpSize = row['tp_size']
            selectedOrginalInfo = meta[
                (meta['db'] == db) & (meta['dataset'] == dataset) & (meta['tp_size'] == tpSize)].reset_index(drop=True)
            action = selectedOrginalInfo["action"][0]
            history = calcHistory(db, dataset, tpSize)
            if action not in actionList:
                txt2show = f'#### 请等待操作完成，还有'
                buildStr = f'{action.count("B")}项构建（Build）' if action.count('B') > 0 else ' '
                deployStr = f'{action.count("D")}项部署（Deploy）' if action.count('D') > 0 else ' '
                testStr = f'{action.count("T")}项测试（Test）' if action.count('T') > 0 else ' '
                st.markdown(f'{txt2show}{buildStr}{deployStr}{testStr}操作。')
            elif history['status'][0] == '':
                st.markdown(f'#### 似乎有操作尚未完成。请人工复核后再进行操作！')
            else:
                st.markdown(f'#### 当前建议：{"可" if action == "test" else "应"}进行{action}操作。请人工复核！')
            st.dataframe(selectedOrginalInfo)

            # st.dataframe(history, use_container_width=True)
            gd = GridOptionsBuilder.from_dataframe(history)
            gd.configure_selection(selection_mode='single', use_checkbox=True)
            gd.configure_pagination(paginationAutoPageSize=False, paginationPageSize=12)
            table = AgGrid(history,
                           gridOptions=gd.build(),
                           allow_unsafe_jscode=True,
                           width='100%',
                           reload_data=False,
                           fit_columns_on_grid_load=True,
                           enable_enterprise_modules=True,
                           update_mode=GridUpdateMode.SELECTION_CHANGED)

            if len(table['selected_rows']) > 0:
                # st.table(table['selected_rows'])
                slr = table['selected_rows'][0]
                if st.button(f"设置{slr['action']}{slr['id']}为“无效”（{db}@{dataset}{tpSize}）", type='primary'):
                    setMark('Test' if slr['action'].startswith('Test') else slr['action'], int(slr['id'][1:]), 'skip')

            if action in actionList:
                device = st.sidebar.selectbox('测试节点', ('data', 'ss', 'zh'))
                action = st.sidebar.selectbox('操作', actionList, index=actionList.index(action))
                if action == 'test':
                    test = st.sidebar.selectbox('测试项', (
                        'ehistory', 'snapshot', 'aggmax', 'etpc', 'reachable', 'update', 'append', 'oltp', 'olap',
                        'htap'))
                    bCache = HashResult(benchmark(), ['dataset', 'tp_size', 'test'])
                    # items = filter(lambda i: i['qcnt'] == i['f.l.cnt'], bCache.get(dataset, '.all', test))
                    items = bCache.get(dataset, '.all', test)
                    qCnt = st.sidebar.selectbox('请求个数', tuple(map(lambda i: i['qcnt'], items)))
                    maxCon = st.sidebar.slider('最大连接数', 1, 32, 1)
                    needResult = False  # st.sidebar.checkbox('保存查询结果', shouldSaveResult(test, dataset, maxCon))
                    testParamStr = f'{test}({qCnt})<{maxCon}>'
                    if st.sidebar.button(
                            f"{action} {db} {dataset}{tpSize} on {device} " + (
                                    testParamStr if action == 'test' else ''),
                            type='primary'):
                        actionRow(device, action, db, dataset, tpSize, test, qCnt, maxCon, needResult)
                elif action == 'build' or action == 'deploy':
                    if st.sidebar.button(f"{action} {db} {dataset}{tpSize} on {device} ", type='primary'):
                        actionRow(device, action, db, dataset, tpSize, None, None, None, None)

    if action not in actionList:
        waitingList.sort(key=lambda j: j['ct'], reverse=True)
        for j in waitingList:
            content = f"{j['db']}@{j['dataset']}{j['tp_size']}"
            content += f" ({j['device']})" if j['job'] == 'build' else ''
            content += f" {j['test']}({j['q_cnt']})x{j['max_con']}" if j['job'] == 'test' else ''
            if j['status'] == 'running':
                st.sidebar.info(f"{j['job'].capitalize()}#{j['jid']} {content}")
            else:
                st.sidebar.warning(f"{j['job'].capitalize()} {content}")
    st.stop()
    # st.dataframe(toPrint1, use_container_width=True, height=350)
    # st.dataframe(meta.loc[:, meta.columns.isin(['db', 'dataset', 'tp_size', 'f.mct_to_now', 'f.mbt', 'lc_m_his_cnt'])])
    st.markdown('## 构建消耗时间')
    toPrint2 = meta.pivot(index=['dataset', 'tp_size'], columns='db', values='f.mbt')
    toPrint2 = toPrint2.style.format("{:.0f}s", na_rep="-").bar(color='#FFA500')  # .highlight_null(null_color='red')
    # st.write(toPrint2)
    # st.write(toPrint2.to_html(escape=False), unsafe_allow_html=True)
    st.dataframe(toPrint2, use_container_width=True, height=350)
    st.markdown('## 部署情况')
    meta = meta[meta['tp_size'] != '.0']
    deployStatus = meta.pivot(index=['dataset', 'tp_size'], columns='db',
                              values='need_deploy')  # .style.applymap(lambda x: )
    st.dataframe(deployStatus, use_container_width=True)
    # toPrint1.style.format({'点击率': "{:.2%}", "留资率": "{:.2%}"})
    # toPrint1 = toPrint1.style.applymap(colorCell)
    # cmap = sns.diverging_palette(10, 250, sep=50, as_cmap=True)
    # .format("{:.2%}", subset=['人口增幅', '世界占比'])
    #     .background_gradient(subset=['点击率', '留资率'], cmap=cmap)\
    #     .apply(lambda x: ['background-color:yellow' if v >= toPrint1.nlargest(8, '线索成本')['线索成本'].min() else '' for v in x],
    #     subset=pd.IndexSlice[:, '线索成本'])


#
# # 'MILESTONE[Space]: Available builds (not deployed) on device(data)'
# if content_to_show == '最近一次测试日期':
#     toPrint1 = toPrint1.style.applymap(colorCell).highlight_null(null_color='red')  # , axis=0
# elif content_to_show == '最近一次测试耗时':
#     toPrint1 = toPrint1.style.highlight_null(null_color='red')  # .
# else:
#     toPrint1 = toPrint1.style.highlight_null(null_color='red')
# st.dataframe(toPrint1)

# # print(missingCnt, knownCnt, completeTime, rebuildTime)
# cols = st.columns(4)
# cols[0].metric(label="已完成（浅绿）", value=timeHumanRead(completeTime))  # , delta="{}项".format(knownCnt)
# missingTime = missingCnt * (completeTime+rebuildTime) / knownCnt
# cols[1].metric(label="重构项（灰色）需时", value=timeHumanRead(rebuildTime))
# cols[2].metric(label="缺失项（红色）需时（估计）", value=timeHumanRead(missingTime), delta="{}项".format(missingCnt), delta_color="inverse")
# cols[3].metric(label="全部工作量（估计）", value=timeHumanRead(completeTime+rebuildTime+missingTime), delta="共{}项".format(missingCnt+knownCnt))
#


def spaceDetailData(meta):  # 总空间、空间明细
    query = leancloud.Query('TestMilestone')
    query.not_equal_to('extra', 'deploy')
    query.add_descending('createdAt')
    query.limit(999)
    objList = query.find()
    cache = HashResult(objList, ['DB', 'Dataset', 'MSize'], lambda item, key: item.get(key))

    # st.write(cache._cache)

    def getDetail(dataset, db, mSize):
        buildInfo = cache.get(db.upper(), dataset, 't' + mSize)
        if len(buildInfo) == 0:
            raise DataNotReadyErr('no db matched of {} {} {}'.format(dataset, db, mSize))
        return buildInfo[0].get('detail')

    def calcSpace(dataset, db, mSize):
        raw = getDetail(dataset, db, mSize)
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
            raw0 = getDetail(dataset, db, '.0')
            c0 = json.loads(raw0)
            # print(c0)
            result['sData'] = c0['data']
            result['sIndex'] = c0['index']
            result['tData'] = content['data'] - c0['data']
            result['tIndex'] = content['index'] - c0['index']
        else:
            raise DataNotReadyErr('no db matched of {} {} {}'.format(dataset, db, mSize))
        return result

    def mBuildSpaceInfo(row):
        dataset = row['dataset']
        db = row['db']
        tp_size = row['tp_size']
        try:
            spaceUsage = calcSpace(dataset, db, tp_size)
            return [spaceUsage['sData'], spaceUsage['sIndex'], spaceUsage['tData'], spaceUsage['tIndex'],
                    spaceUsage['sData'] + spaceUsage['sIndex'] + spaceUsage['tData'] + spaceUsage['tIndex']]
        except DataNotReadyErr as e:
            print('WARNING: ' + e.message)
            return [None, None, None, None, 0]

    meta[['lc_m.s.data',
          'lc_m.s.index',
          'lc_m.t.data',
          'lc_m.t.index',
          'lc_m.total_size']] = meta.apply(mBuildSpaceInfo, axis=1, result_type="expand")
    return meta


meta = spaceDetailData(meta)
meta = meta[meta['tp_size'] == '.all']
# st.dataframe(meta)
toPrint3 = meta.pivot(index=['dataset'], columns='db', values='lc_m.total_size')
toPrint3 = toPrint3.style.format(lambda s: "{:.1f}GB".format(s / 1024 / 1024 / 1024))
st.dataframe(toPrint3, use_container_width=True)
toPlot = meta[['db', 'dataset']]
toPlot['size'] = meta['lc_m.total_size'].astype(float)
toPlot['sd'] = meta['lc_m.s.data'].astype(float)
toPlot['si'] = meta['lc_m.s.index'].astype(float)
toPlot['td'] = meta['lc_m.t.data'].astype(float)
toPlot['ti'] = meta['lc_m.t.index'].astype(float)
# st.dataframe(toPlot)
# st.write(toPlot['lc_m.total_size']
# selection = alt.selection_multi(fields=['db'], bind='legend')
# alt.themes.enable('ggplot2')
alt.themes.enable('default')
bar = alt.Chart(toPlot).transform_fold(
    ['sd', 'si', 'td', 'ti'],
    as_=['category', 'size']
).mark_bar().encode(
    x=alt.X("db", title=None),
    y=alt.Y("sum(size):Q",
            stack='zero',
            axis=alt.Axis(format="~s"),
            title=None),
    color=alt.Color('category:N'),
    # tooltip=["db", "size", "lc_m.s.data", "lc_m.s.index", "lc_m.t.data", "lc_m.t.index"],
    # column="dataset",
    # opacity=alt.condition(selection, alt.value(1), alt.value(0.1))
).properties(
    width=250,
    height=460
)  # .add_selection(selection)
text = bar.mark_text(
    baseline='bottom',
).encode(
    text=alt.Text('label:N'),  # , format=',.2r')
    color=alt.value("#000")
).transform_joinaggregate(
    asize="sum(size):Q",
    groupby=["dataset", "db"]
).transform_calculate(
    label="format(datum.asize/1024/1024/1024, '.1f')"
)
c = alt.layer(bar, text).facet(
    facet='dataset',
    columns=3
).resolve_scale(
    y='independent'
)
st.altair_chart(c)


def spaceIncrement():  # 空间增长率分析
    raw = {
        '0.01': spaceDetailData(dataSize='.01'),
        '0.1': spaceDetailData(dataSize='.1'),
        # '0.5': spaceDetailData(dataSize='.5'),
        # '0.9': spaceDetailData(dataSize='.9'),
        '1': spaceDetailData(dataSize='.all')
    }
    df = dictKey2PdCol(raw, 'size')
    # print(df)
    tpData = df.loc[df['category'].isin(['tData', 'tIndex'])]
    # print(tpData)
    grp = tpData.groupby(['sys', 'dataset', 'size']).agg('sum')
    toPrint = pd.DataFrame(grp.reset_index())
    # print(toPrint)
    # toPrint['size'] = toPrint['size'].astype(float)
    # tpData = toPrint.loc[:,['sys', 'dataset', 'value', 'tSize']]
    # tpData
    # toPrint1 = toPrint.pivot(index=['sys', 'dataset'], columns='size', values='value')
    # toPrint2 = toPrint1.sort_values(by=['dataset', 'sys'])
    # toPrint2['inc_rate'] = toPrint2['.1'] / toPrint2['.01']
    # print(toPrint2)
    dodge_text = G.position_dodge(width=.9)
    g = G.ggplot(toPrint, G.aes('sys', 'value', fill='sys'))
    g = g + G.stat_summary(fun_y=np.mean, geom='bar', position=dodge_text, width=.9)
    # g += G.geom_bar()
    # g += G.geom_line()
    # g += G.geom_point()
    # + G.scale_x_datetime(breaks=date_breaks('1 month'))
    # g = g + G.ggtitle('Space cost of Comparable systems (Bytes)')
    g += G.scale_y_continuous(labels=lambda l: ["{:.1f} GB".format(v / 1024 / 1024 / 1024) for v in l])
    g += G.ylab('Database Size')
    # g += G.xlab('Systems')

    # g = G.ggplot(spaces, G.aes(x='type', y='exeTime', fill='sys'))
    # g = g + G.stat_summary(fun_y=np.mean, geom='bar', position=dodge_text, width=.9)
    # # g = g + stat_summary(fun_y=np.mean, geom=geom_text(, size=8))
    # g = g + G.geom_boxplot(width=.3, outlier_size=1, outlier_shape='.', position=dodge_text)
    # g = g + G.geom_text(G.aes(label=G.after_stat('y')), stat=G.stat_summary(fun_y=np.sum),
    #                     position=dodge_text, format_string='{:.1f}') #, va='bottom'
    # # g = g + geom_point()
    # # g = g + geom_jitter()
    # # g = g + lims(y=-2500)
    # g = g + G.facet_wrap('dataset', scales="free_y")
    g = g + G.facet_wrap('size + dataset', scales="free_y", ncol=3)
    # g = g + G.facet_grid('size ~ dataset + category', scales="free")
    # g = g + G.facet_grid('size ~ dataset', scales="free_y")  # row ~ column
    # g = g + G.ylim(0, 650)
    # # g = g + xlim(-1, 3700)
    # # g = g + G.scale_y_log10()
    g = g + G.theme(
        # axis_text_x=element_text(rotation=45, hjust=1),
        # legend_position='top',
        subplots_adjust={'wspace': 0.21},
        figure_size=(15, 6))
    # g = g + G.ggtitle('RW mix 0.1')
    return g.draw()

# st.pyplot(spaceIncrement())
