# -*- coding: utf-8 -*-
"""
Created on 2022/2/24

@author: Song
"""

from flask import Flask, request  # 导入Flask类
import psycopg2
import datetime
import requests
import json


def send2Feishu(title):
    result = requests.post(
        'https://api.hiflow.tencent.com/engine/webhook/31/1576135637549260801',
        json.dumps({"title": title}).encode('utf-8'),
        headers={'Content-Type': 'application/json'}
    )
    return result.text

class TestDatabase:
    def __init__(self):
        self.conn = psycopg2.connect(database="test_case", user="postgres", password="langduhua", host="127.0.0.1", port="5432")
        self.cursor = self.conn.cursor()

    def putTest(self, jid, j):
        self.cursor.execute('SELECT created_at, updated_at, phase FROM "Jenkins-Test" WHERE "ID"=%s', (jid,))

        row = self.cursor.fetchone()
        print(row)
        if row is None:
            self.cursor.execute('''INSERT INTO "Jenkins-Test"
("ID", phase, status, mark, test, dataset, db, "mTpSize", "bTpRange", "maxCon", "hasResult", device, duration, reqcnt)
VALUES(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);
''', (jid, j['phase'], j['status'], j['mark'], j['test'], j['dataset'], j['db'], j['mTpSize'], j['bTpRange'], j['maxCon'], j['hasResult'], j['device'], 0, j['reqCnt']))
            print(self.cursor.query)
            diff = 0
        else:
            beginT = row[1] if row[2] == 'STARTED' else row[0]
            diff = (datetime.datetime.now()-beginT).total_seconds()
            self.cursor.execute('''UPDATE "Jenkins-Test" SET phase=%s, status=%s, mark=%s, duration=%s WHERE "ID"=%s''',
                                (j['phase'], j['status'], j['mark'], diff, jid))
            print(self.cursor.query)
            print(row[0])
            if j['phase'] == 'FINALIZED':
                send2Feishu(f"{j['test']} {j['status']}！{j['db']}<{j['maxCon']}>@{j['dataset']}.{j['mTpSize']}({j['reqCnt']}) J{jid}")
        self.conn.commit()
        return diff

    def putDeploy(self, jid, j):
        self.cursor.execute('SELECT created_at, updated_at, phase FROM "Jenkins-Deploy" WHERE "ID"=%s', (jid,))
        row = self.cursor.fetchone()
        print(row)
        if row is None:
            self.cursor.execute('''INSERT INTO "Jenkins-Deploy" ("ID", phase, status, dataset, db, "tpSize", target, duration) VALUES(%s, %s, %s, %s, %s, %s, %s, %s);
''', (jid, j['phase'], j['status'], j['dataset'], j['db'], j['tpSize'], j['device'], 0))
            print(self.cursor.query)
            self.conn.commit()
            return 0
        else:
            beginT = row[1] if row[2] == 'STARTED' else row[0]
            diff = (datetime.datetime.now()-beginT).total_seconds()
            self.cursor.execute(
                '''UPDATE "Jenkins-Deploy" SET phase=%s, status=%s, duration=%s WHERE "ID"=%s''',
                (j['phase'], j['status'], diff, jid))
            print(self.cursor.query)
            print(row[0])
            self.conn.commit()
            if j['phase'] == 'FINALIZED':
                send2Feishu(f"部署{j['status']}！{j['db']}@{j['dataset']}.{j['tpSize']} to {j['device']} J{jid}")
            return diff

    def putBuild(self, jid, j):
        self.cursor.execute('SELECT created_at, updated_at, phase FROM "Jenkins-Build" WHERE "ID"=%s', (jid,))
        row = self.cursor.fetchone()
        print(row)
        if row is None:
            self.cursor.execute('''INSERT INTO "Jenkins-Build"
("ID", phase, status, dataset, db, "tpSize", device, duration)
VALUES(%s, %s, %s, %s, %s, %s, %s, %s);
''', (jid, j['phase'], j['status'], j['dataset'], j['db'], j['tpSize'], j['device'], 0))
            print(self.cursor.query)
            diff = 0
        else:
            beginT = row[1] if row[2] == 'STARTED' else row[0]
            diff = (datetime.datetime.now() - beginT).total_seconds()
            self.cursor.execute(
                '''UPDATE "Jenkins-Build" SET phase=%s, status=%s, duration=%s WHERE "ID"=%s''',
                (j['phase'], j['status'], diff, jid))
            print(self.cursor.query)
            print(row[0])
            if j['phase'] == 'FINALIZED':
                send2Feishu(f"构建{j['status']}！{j['db']}@{j['dataset']}{j['tpSize'][1:]} J{jid}")
        self.conn.commit()
        return diff

    def close(self):
        self.cursor.close()
        self.conn.close()


app = Flask(__name__)  # 实例化并命名为app实例


@app.route('/jenkins/build', methods=['POST'])
def putBuild():
    build = request.json['build']
    p = build['parameters']
    j = {
        'phase': build['phase'],
        'status': build['status'] if 'status' in build else '',
        'dataset': p['dataset'],
        'db': p['db_name'],
        "tpSize": p['data_size'],
        'device': p['device'],
    }
    t = TestDatabase()
    diff = t.putBuild(build['number'], j)
    t.close()
    return 'time diff: {}s'.format(diff)


@app.route('/jenkins/deploy', methods=['POST'])
def putDeploy():
    build = request.json['build']
    print(build)
    type = build['parameters']['dbtype']
    m = build['parameters']['milestone']
    t0 = m.split('_')
    db = t0[2]
    if 'tgk' in db and 'tcypher' in type:
        db = db.replace('tgk', 'tgl')
    j = {
        'phase': build['phase'],
        'status': build['status'] if 'status' in build else '',
        'dataset': t0[1],
        'db': db,
        "tpSize": t0[3],
        'device': build['parameters']['target_host'],
    }
    t = TestDatabase()
    diff = t.putDeploy(build['number'], j)
    t.close()
    return 'time diff: {}s'.format(diff)


@app.route('/jenkins/test', methods=['POST'])
def putTest():
    build = request.json['build']
    m = build['parameters']['milestone']
    b = build['parameters']['benchmark_name']
    t0 = m.split('_')
    t1 = b.split('_')
    j = {
        'phase': build['phase'],
        'status': build['status'] if 'status' in build else '',
        'mark': None,  # 'db-test-tmp0',
        'test': t1[3],
        'dataset': t0[1],
        'db': t0[2],
        'reqCnt': int(t1[4]),
        "mTpSize": t0[3],
        "bTpRange": t1[2][2:],
        "maxCon": build['parameters']['max_connection'],
        "hasResult": build['parameters']['need_result'],
        'device': build['parameters']['db_host'],
        'duration': 0,
    }
    t = TestDatabase()
    diff = t.putTest(build['number'], j)
    t.close()
    return 'time diff: {}s'.format(diff)


if __name__ == "__main__":
    # send2Feishu('飞书打卡功能恢复')
    j = {
        'phase': 'COMPLETED',
        'status': 'SUCCESS',
        'mark': 'db-test-tmp0',
        'test': 'ehistory',
        'dataset': 'energy',
        'db': 'pg',
        "mTpSize": '01',
        "bTpRange": '01',
        "maxCon": 1,
        "hasResult": True,
        'device': 'data',
        'duration': 0,
    }
    app.run(port=8999, host="0.0.0.0", debug=False)
