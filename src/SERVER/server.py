from flask import Flask, request as request, jsonify, render_template
import os
import json
import pymssql
import logging
import pyodbc

app = Flask(__name__)

server='tcp:roadkill-1.database.windows.net'
username='roadkillAdmin'
password='password'
database='roadkill-data'
driver='{ODBC Driver 17 for SQL Server}'




@app.route('/')
def index():
    return 'auto-repo'

@app.route('/test', methods=['GET','POST'])
def test():
    body = request.get_data()
    print(body)
    cnxn = pyodbc.connect('DRIVER='+driver+';SERVER='+server+';DATABASE='+database+';UID='+username+';PWD='+ password)
    cursor = cnxn.cursor()
    temp=str(body).split('&')
    animal=temp[0].split('=')[1]
    section=temp[1].split('=')[1]

    cursor.execute("INSERT INTO test(section, animal) VALUES (?, ?)",section,animal)
   
    cnxn.commit()
    cnxn.close()
    
    return body


# Save data in section1
@app.route('/save', methods=['POST','GET'])
def save_in_section1():

    cnxn = pyodbc.connect('DRIVER='+driver+';SERVER='+server+';DATABASE='+database+';UID='+username+';PWD='+ password)
    cursor = cnxn.cursor()
    section = body['section']
    animal = body['animal']
    
    cursor.execute("insert into Reportdata (section, animal) values (%s, %s)", (section, animal))

    
    
    return jsonify({"message": "success"})


if __name__ == '__main__':
    app.run(debug=True)