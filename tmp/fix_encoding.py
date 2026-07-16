import subprocess
files = ['src/main/java/com/oculus/jj/ui/DCMView.java', 'src/main/java/com/oculus/jj/ui/BrowserToolbar.java']
for f in files:
    result = subprocess.run(['git', 'show', f'HEAD:{f}'], capture_output=True)
    with open(f, 'wb') as fh:
        fh.write(result.stdout)
    print(f'{f}: {len(result.stdout)} bytes')
