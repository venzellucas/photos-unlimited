#!/usr/bin/env python3
"""Check a public source snapshot without printing potential secrets."""
from pathlib import Path
import re, sys
root=Path(__file__).resolve().parents[1]
skip={'.git','.gradle','build','dist','__pycache__'}
patterns={
 'private network':r'\b(?:192\.168\.(?:\d{1,3}\.)\d{1,3}|10\.(?:\d{1,3}\.){2}\d{1,3})\b',
 'local workstation path':r'/(?:root|home/builder)/',
 'GitHub credential':r'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})',
 'private key':r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
 'credential assignment':r'(?:oauth_token|Token|Auth)\s*[=:]\s*[A-Za-z0-9_/-]{30,}',
}
findings=[]
for p in root.rglob('*'):
 if not p.is_file() or skip.intersection(p.relative_to(root).parts) or p==Path(__file__).resolve():continue
 if p.name in {'.env','local.properties'} or p.suffix in {'.pem','.key','.jks','.keystore','.p12'}:
  findings.append((str(p.relative_to(root)),'private file type')); continue
 try: text=p.read_text()
 except (UnicodeError,OSError):continue
 for label,pattern in patterns.items():
  if re.search(pattern,text):findings.append((str(p.relative_to(root)),label))
for path,label in findings:print(f'{path}: {label}')
if findings:sys.exit(1)
print('Public source checks passed. This is a targeted check, not a complete secret audit.')
