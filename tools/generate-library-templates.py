"""Rebuild read-only sync masks from the pinned MIT-licensed GPMC checkout.
Requires blackboxprotobuf; never imports or calls an authenticated API client."""
import ast, sys, pathlib, importlib.util, argparse, subprocess
parser=argparse.ArgumentParser()
parser.add_argument('checkout',type=pathlib.Path)
args=parser.parse_args()
checkout=args.checkout.resolve()
revision=subprocess.check_output(['git','-C',str(checkout),'rev-parse','HEAD'],text=True).strip()
assert revision=='94b1b267ca6819da542d4330a7dd216747eb0437','Unexpected upstream revision'
sys.path.insert(0,str(checkout))
from blackboxprotobuf import encode_message
spec=importlib.util.spec_from_file_location('mt',str(checkout/'gpmc/message_types.py')); m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
tree=ast.parse((checkout/'gpmc/api.py').read_text())
out=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets'
for method,typedef,name in [('get_library_state','GET_LIB_STATE','state'),('get_library_page_init','GET_LIB_PAGE_INIT','initial'),('get_library_page','GET_LIB_PAGE','delta')]:
 fn=next(n for n in ast.walk(tree) if isinstance(n,ast.FunctionDef) and n.name==method)
 expr=next(n.value for n in fn.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='proto_body' for t in n.targets))
 class Replace(ast.NodeTransformer):
  def visit_Name(self,node):
   assert node.id in ('sync_token','resume_token'); return ast.copy_location(ast.Constant(''),node)
 data=ast.literal_eval(Replace().visit(expr))
 # This upstream field contains a fixed account identifier, not a protocol capability.
 # Do not send another account identifier. Pagination cursors and auth scope identify our account.
 data['1'].get('22',{}).pop('2',None)
 blob=encode_message(data,getattr(m,typedef))
 (out/f'library-{name}.bin').write_bytes(blob)
 print(name,len(blob))
