import os, sys

SRC_DIR = 'src/main/java/com/oculus/jj/ui'
for f in os.listdir(SRC_DIR):
    if not f.endswith('.java'): continue
    full = os.path.join(SRC_DIR, f)
    with open(full, encoding='utf-8') as fp:
        for i, line in enumerate(fp.read().split('\n'), 1):
            if 'createGeneralPane' in line or 'createDatabasePane' in line:
                print("L%d of %s:" % (i, f), repr(line.strip()[:100]))
