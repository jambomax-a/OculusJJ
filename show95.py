import re
with open('C:/Users/jam11/OculusJJ/src/main/java/com/oculus/jj/ui/DCMView.java.bak', encoding='utf-8') as f:
    bak = f.read().splitlines()

# Show lines 955-970 in BAK - this is where the structure diverges from clean mousePressed logic
for i in range(954, 972):
    print(str(i+1) + ': ' + bak[i][:90])