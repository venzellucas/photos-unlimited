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
# Text files also get a UTF-8 check and an email check (secret patterns above scan every file).
text_suffixes={'.java','.gradle','.xml','.md','.py','.sh','.yml','.yaml','.json','.properties',
 '.html','.css','.js','.svg','.txt','.pro','.kts',''}
# The look-behind skips URLs such as https://user@lh3.googleusercontent.com/a.
email=re.compile(r'(?<![\w/@.:-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+',re.IGNORECASE)
# mailto: links are found separately: the look-behind above skips anything after ":".
mailto=re.compile(r'mailto:([^\s"\'<>?]+)',re.IGNORECASE)
def allowed(address):
 a=address.lower()
 return a.endswith('@users.noreply.github.com') or a.endswith('@example.com') or a.startswith('noreply@')
findings=[]
for p in root.rglob('*'):
 if not p.is_file() or skip.intersection(p.relative_to(root).parts) or p==Path(__file__).resolve():continue
 if p.name in {'.env','local.properties'} or p.suffix in {'.pem','.key','.jks','.keystore','.p12'}:
  findings.append((str(p.relative_to(root)),'private file type')); continue
 if p.suffix.lower() in text_suffixes:
  try: text=p.read_text(encoding='utf-8')
  except UnicodeDecodeError:
   findings.append((str(p.relative_to(root)),'not valid UTF-8')); continue
  except OSError:continue
 else:
  try: text=p.read_text()
  except (UnicodeError,OSError):continue
 addresses=[m.group(0) for m in email.finditer(text)]+[m.group(1) for m in mailto.finditer(text)]
 if any(not allowed(a) for a in addresses):
  findings.append((str(p.relative_to(root)),'email address'))
 for label,pattern in patterns.items():
  if re.search(pattern,text):findings.append((str(p.relative_to(root)),label))
for path,label in findings:print(f'{path}: {label}')
if findings:sys.exit(1)
print('Public source checks passed. This is a targeted check, not a complete secret audit.')
