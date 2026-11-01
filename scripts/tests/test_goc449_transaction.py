"""Exercise installer control flow with isolated filesystem/process/service mocks.
Does not mount or execute any firmware binary. Not a device/boot validation.
"""
import tempfile, pathlib, shutil, subprocess, os, hashlib
B=pathlib.Path(__file__).resolve().parent
# Synthetic bytes keep this test independent of proprietary firmware.
stock_bytes=b"synthetic stock firmware"
patch_bytes=b"synthetic patched firmware"
H=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
STOCK=hashlib.sha256(stock_bytes).hexdigest()
PATCH=hashlib.sha256(patch_bytes).hexdigest()
installer=B.parents[1]/'shared/src/main/assets/goc449/manage-system-goc.sh'
with tempfile.TemporaryDirectory(prefix='goc449-txn-') as temp:
 r=pathlib.Path(temp);(r/'system/bin').mkdir(parents=True);(r/'data/local').mkdir(parents=True);(r/'proc/net').mkdir(parents=True);(r/'tools').mkdir();(r/'payload').mkdir()
 (r/'system/bin/gocsdk').write_bytes(stock_bytes);(r/'payload/gocsdk-449-iap2-test1').write_bytes(patch_bytes)
 (r/'proc/mounts').write_text('mock '+str(r/'system')+' ext4 ro,relatime 0 0\n')
 (r/'proc/net/unix').write_text('')
 code=installer.read_text().replace('52596a2ba3e1be85dc09efd20290b2d6b36aef9e569acd1d86014bcb991fe15e',STOCK).replace('882afd622fe0d7a154e4cf0531d122c3fc54ba87c93cf47f90db258131502c0f',PATCH).replace('/system',str(r/'system')).replace('/data/local',str(r/'data/local')).replace('/data/data',str(r/'data/data')).replace('/proc/',str(r/'proc')+'/')
 script=r/'payload/manage.sh';script.write_text(code)
 tool='''#!/usr/bin/env python3
import os,sys,pathlib,subprocess,shutil,hashlib
r=pathlib.Path(os.environ['MOCK_ROOT']);name=pathlib.Path(sys.argv[0]).name;a=sys.argv[1:]
if name=='busybox':
 name=a.pop(0)
 if name=='id':print('0');sys.exit()
 if name=='sha256sum':
  try: print(hashlib.sha256(pathlib.Path(a[0]).read_bytes()).hexdigest()+'  '+a[0]);sys.exit()
  except OSError: sys.exit(1)
 if name=='stat':print('0:0:755');sys.exit()
 if name=='readlink':
  try:print(os.readlink(a[0]));sys.exit()
  except OSError:sys.exit(1)
 if name=='cp':
  try:shutil.copy2(a[-2],a[-1]);sys.exit()
  except OSError:sys.exit(1)
 if name=='printf':sys.stdout.write(a[0].replace('\\\\n','\\n')%tuple(a[1:]));sys.exit()
 os.execvp(name,[name]+a)
if name=='getprop':
 print({'ro.build.version.sdk':'22','ro.product.device':'xea406m','init.svc.gocsdk':(r/'state').read_text().strip()}.get(a[0],''));sys.exit()
if name=='getenforce':print('Disabled');sys.exit()
if name=='mount':
 with (r/'mount-ops').open('a') as f:f.write(' '.join(a)+'\\n')
 sys.exit()
if name in ('sleep','sync','chown'):sys.exit()
if name=='ls':sys.exit(1)
if name=='stop':
 shutil.rmtree(r/'proc/123',ignore_errors=True);(r/'state').write_text('stopped');(r/'proc/net/unix').write_text('');sys.exit()
if name=='start':
 h=hashlib.sha256((r/'system/bin/gocsdk').read_bytes()).hexdigest()
 if (r/'fail-candidate').exists() and h==os.environ['PATCH_HASH']:sys.exit()
 if (r/'fail-stock').exists() and h!=os.environ['PATCH_HASH']:sys.exit()
 (r/'proc/123').mkdir(exist_ok=True)
 e=r/'proc/123/exe'
 if not e.exists():e.symlink_to(r/'system/bin/gocsdk')
 (r/'state').write_text('running')
 (r/'proc/net/unix').write_text('a /dev/socket/goc_spp\\n' if h==os.environ['PATCH_HASH'] else '')
 sys.exit()
'''
 for name in ['busybox','getprop','getenforce','mount','sleep','sync','chown','ls','start','stop']:
  p=(r/'system/bin' if name in ('start','stop') else r/'tools')/name;p.write_text(tool);p.chmod(0o755)
 env=dict(os.environ,MOCK_ROOT=str(r),PATCH_HASH=PATCH,PATH=str(r/'tools')+':'+os.environ['PATH'])
 subprocess.run([str(r/'system/bin/start'),'gocsdk'],env=env,check=True)
 def run(action,success=True):
  p=subprocess.run(['/bin/sh',str(script),action],env=env,capture_output=True,text=True)
  if (p.returncode==0)!=success:raise AssertionError((action,p.returncode,p.stdout,p.stderr))
  return p
 stage=r/'system/bin/.gocsdk-449-stage'
 stage.write_bytes(b'previous interrupted operation')
 run('install',False);assert stage.read_bytes()==b'previous interrupted operation'
 stage.unlink()
 run('check');run('install');assert H(r/'system/bin/gocsdk')==PATCH;assert H(r/'data/local/goc449-system-backup/gocsdk.stock')==STOCK
 run('install');run('rollback');assert H(r/'system/bin/gocsdk')==STOCK
 (r/'fail-candidate').touch();p=run('install',False);assert H(r/'system/bin/gocsdk')==STOCK;assert (r/'state').read_text()=='running';assert 'Pre-change service restored.' in p.stdout
 (r/'fail-candidate').unlink()
 run('install');subprocess.run([str(r/'system/bin/stop'),'gocsdk'],env=env,check=True);run('rollback');assert H(r/'system/bin/gocsdk')==STOCK
 run('install');(r/'fail-stock').touch();run('rollback',False);assert H(r/'system/bin/gocsdk')==STOCK;assert 'restored-awaiting-service' in (r/'data/local/goc449-system-backup/result').read_text();(r/'fail-stock').unlink();run('rollback')
 (r/'payload/gocsdk-449-iap2-test1').write_bytes(b'bad');run('install',False);assert H(r/'system/bin/gocsdk')==STOCK
 assert 'remount,ro' in (r/'mount-ops').read_text()
 print('PASS: check, install, repeat install, rollback, failed startup auto-restore, invalid input rejection, rollback from stopped service, stock retained on recovery startup failure, remount restoration (simulated)')
