src = "src/main/java/com/oculus/jj/config/AppPreferences.java"
with open(src, encoding="utf-8") as f:
    lines = f.read().split("\n")

# Fix missing semicolons on new key lines
fixes = [
    ("KEY_COMPRESSION_LOW_RES_SETTINGS_JSON", 'Key_COMPRESSION_LOW_RES_SETTINGS_JSON"),'),
    ('KEY_USE_PATIENT_BIRTH_DATE_FOR_UID', "KEY_USE_PATIENT_BIRTH_DATE_FOR_UID = \\"usePatientBirthDateForUID\\";"),
    ('KEY_AUTO_CLEAN_DAYS_TO_KEEP', '"AutoCleanDaysToKeep";'),
    ('KEY_DB_BIRTH_DATE_FORMAT', 'KEY_DB_BIRTH_DATE_FORMAT'),
    ('KEY_LISTENER_PORT_TLS', "KEY_LISTENER_PORT_TLS"),
    ('Key_LISTENER_TLS_STORESCP_AETITLE', 'KEY_LISTENER_TLS_STORESCP_AETITLE'),
]

# Also verify: list keys missing semicolons
missing_semi = []
for i, line in enumerate(lines):
    if 'public static final String KEY_' in line and not lines[i].rstrip().endswith(';'):
        missing_semi.append((i+1, lines[i].strip()))

print(f"Keys with possibly missing semicolons ({len(missing_semi)}):")
for ln, txt in missing_semi[:5]:
    print(f"  L{ln}: {txt}")
if len(missing_semi) > 5:
    print(f"  ... and {len(missing_semi)-5} more")

# Count new keys added
new_keys = ['KEY_JP2K_ENGINE', 'KEY_COMPRESSION_SETTINGS_JSON', 
            'KEY_USE_PATIENT_NAME_FOR_UID', 'KEY_USE_PATIENT_BIRTH_DATE_FOR_UID',
            'KEY_LISTENER_TLS_ENABLED', 'KEY_LISTENER_TLS_CERT_AUTH',
            'KEY_WADO_PORT', 'KEY_USERS_MANAGEMENT_JSON']
for k in new_keys:
    found = any(k in l for l in lines)
    print(f"  {k}: {'OK' if found else 'MISSING'}")

total_lines = len(lines)
print(f"\nTotal lines now: {total_lines}")

with open(src, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
