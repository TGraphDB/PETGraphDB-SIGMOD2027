import time
from api4jenkins import Jenkins

j = Jenkins('http://master:8843/', auth=('adminsong', '112fcf4ba70204deda67cbf82a99f02b9a'))


def param2dic(paramters):
    params = {}
    # print(paramters)
    for p in paramters:
        params[p.name] = p.value
    return params


def job_info(jobName):
    job = j.get_job(jobName)
    parameters = job.get_parameters()
    builds = []
    for build in job.iter_builds():
        res = param2dic(build.get_parameters())
        res['jid'] = build.id
        res['duration'] = build.duration
        res['createdAt'] = int(build.timestamp / 1000)
        res['building'] = build.building
        # res['json'] = build.api_json()
        res['result'] = build.result
        builds.append(res)
    ret = {
        'params': parameters,
        'recent_builds': builds
    }
    return ret


def build(name, args_dict):
    mapping = {
        'build': 'milestone-build-master',
        'test': 'test',
        'deploy': 'milestone-deploy',
        'validate': 'validate'
    }
    jobName = mapping[name]
    # print(name, args_dict)
    item = j.build_job(jobName, **args_dict)
    buildObj = item.get_build()
    cnt = 0
    while buildObj is None:
        time.sleep(1)
        cnt += 1
        if cnt >= 3:
            raise Exception('queued')
        buildObj = item.get_build()
    return buildObj  # .api_json()


GOOD = 'good'
NEED_REPAIR = 'need-repair'
NEED_BUILD = 'need-build'


def strParams2dic(paramStr):
    paramsTmp = paramStr.split('\n')
    params = [p.split('=') for p in paramsTmp if p != '']
    return {kv[0]: kv[1] for kv in params}


if __name__ == '__main__':
    import json
    # print(json.dumps(job_info('milestone-deploy')))
    print(json.dumps(j.queue.api_json()))
    x = [(qi['task']['name'], strParams2dic(qi['params'])) for qi in j.queue.api_json()['items'] if qi['blocked']]
    print(json.dumps(x))
