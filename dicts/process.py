import json
import os
import sqlite3
import zipfile

def load_jmdict(zip, con):
  for name in zip.namelist():
    with zip.open(name) as f:
      print(name)
      if name == 'index.json' or name.startswith('tag_bank'):
        continue

      entries = json.load(f)
      to_insert = []
      for entry in entries:
        word, reading, kind, _, priority, translation = entry[:6]
        to_insert.append((word, reading, kind, '; '.join(translation), int(priority)))
        if len(to_insert) < 0 or word == '赴任':
          print(entry)
          print('    ', word, reading, translation)

      cur = con.cursor()
      cur.executemany('INSERT INTO translations VALUES (?, ?, ?, ?, ?)', 
                      to_insert)
      con.commit()

directory = os.path.dirname(__file__)
db_file = os.path.join(os.path.dirname(directory), "app/src/main/assets/dict.db")
os.remove(db_file)
con = sqlite3.connect(db_file)
cur = con.cursor()
cur.execute('''CREATE TABLE translations 
               (word text, reading text, kind text, english text, priority INTEGER)''')
con.commit()

with zipfile.ZipFile(os.path.join(directory, 'jmdict_english.zip')) as f:
  load_jmdict(f, con)