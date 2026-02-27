
from flask import Flask, Response

app = Flask(__name__, static_folder="js", static_url_path="/js")

@app.route('/')
def index():
    import pathlib
    file_path = pathlib.Path(__file__).parent / 'index.html'
    f = open(file_path, encoding='utf-8')
    report = f.read()
    f.close()
    return Response(report)

#@app.route('/<file_name>')
#def get_report(file_name):
#    """根据文件名获取测试报告。"""
    

app.run(debug=True)