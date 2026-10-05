import subprocess, pathlib, json, re, hashlib
ROOT = pathlib.Path(subprocess.check_output(['git','rev-parse','--show-toplevel']).decode().strip())

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode('utf-8')

old = 'f22849be9a900a21099779178a448b968ada27bc'
main = '821f75206f38e8d0f8c90390bca65063e3e6efaf'
base = git('merge-base', old, main).strip()
paths = git('diff', '--name-only', base, old).splitlines()

def changed_lines(start, end, path):
    lines = git('diff', '--no-ext-diff', '--unified=0', start, end, '--', path).splitlines()
    return [line for line in lines if line.startswith(('+', '-')) and not line.startswith(('+++', '---'))]

rows = []
for path in paths:
    before = changed_lines(base, old, path)
    after = changed_lines(main, '', path) if False else [line for line in git('diff', '--no-ext-diff', '--unified=0', main, '--', path).splitlines() if line.startswith(('+', '-')) and not line.startswith(('+++', '---'))]
    rows.append({'path': path, 'old_changed_lines': len(before), 'rebased_changed_lines': len(after), 'changed_lines_identical': before == after})
loop_path = 'android/core-agent/src/main/java/ai/eqo/core/agent/ActionLoop.kt'
main_loop = git('show', main + ':' + loop_path)
new_loop = (ROOT / loop_path).read_text()
def function(text, name):
    start = text.index('    ' + name)
    brace = text.index('{', start)
    depth = 0
    for index in range(brace, len(text)):
        depth += (text[index] == '{') - (text[index] == '}')
        if depth == 0:
            return text[start:index+1]
    raise AssertionError(name)
serialization = {}
for name in ['fun resume(', 'private fun applyCommand(', 'private fun terminalize(', 'private fun emitPlanStatus(', 'private fun submit(']:
    before = function(main_loop, name)
    after = function(new_loop, name)
    after = re.sub(r'^\s*onDiagnostic\([^\n]*\)\n', '', after, flags=re.M)
    serialization[name] = before == after
assert all(serialization.values()), serialization
stress = 'android/core-agent/src/test/java/ai/eqo/core/agent/ActionLoopDispatchTraceTest.kt'
assert (ROOT/stress).read_bytes() == subprocess.check_output(['git','show',main+':'+stress],cwd=ROOT)
result = {'source': old, 'main': main, 'source_merge_base': base, 'changed_lines': rows, 'mechanical_file_count': sum(row['changed_lines_identical'] for row in rows), 'compared_file_count': len(rows), 'serialization_equal_after_removing_only_diagnostic_lines': serialization, 'ordered_stress_test_byte_identical_to_main': True}
output=pathlib.Path(__file__).with_name('task-076-mechanical-comparison.json')
output.write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps(result, indent=2))
