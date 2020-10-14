import os
import subprocess
import glob
import time
import sys
import shutil
import threading
from collections import deque, namedtuple
import xml.etree.ElementTree as ET
import xml.dom.minidom

oneDPL_ROOT = os.path.abspath('..')
BUILD_DIR = 'build'
TEST_TIMEOUT = 40

class TestResult(object):
    def __init__(self, test_name):
        self.test_name = test_name
        self.failed_phase = None
        self.output = ''
        self.ret = 0
    def __str__(self):
        return '{} : {}'.format(self.test_name, self.failed_phase)

def build_and_run_test(test):
    result = TestResult(test)
    result_path = os.path.splitext(os.path.join(BUILD_DIR, os.path.relpath(test, oneDPL_ROOT)))[0] + '.exe'
    os.makedirs(os.path.dirname(result_path), exist_ok=True)

    defines = ['/D', '_PSTL_USE_RANGES=1'] if 'ranges' in os.path.dirname(test) else []
    #/Od removed from command line becase cause multiple test crashes
    cmdline = 'dpcpp.exe /nologo /D _UNICODE /D UNICODE /Zi /WX- /EHsc /Fe{}'.format(result_path).split()
    cmdline.extend(defines)
    cmdline.extend([
        '/I{}/include'.format(oneDPL_ROOT),
        '/I{}/test/pstl_testsuite'.format(oneDPL_ROOT),
        test])
    #print('Running', subprocess.list2cmdline(cmdline))
    try:
        phase = 'build'
        print("compiling", test)
        result.output = subprocess.check_output(cmdline, stderr=subprocess.STDOUT, encoding='ascii')
        phase = 'run test'
        print("running", result_path)
        result.output += subprocess.check_output([result_path], stderr=subprocess.STDOUT, timeout=TEST_TIMEOUT, encoding='ascii')
    except subprocess.CalledProcessError as e:
        result.output += e.output
        result.failed_phase = phase
        result.ret = e.returncode
    except subprocess.TimeoutExpired as e:
        result.output += e.output + '\nTIMED OUT'
        result.failed_phase = phase
    return result

def worker(queue, results):
    while True:
        try:
            item = queue.popleft()
        except IndexError:
            print('Finishing thread')
            return
        results.append(build_and_run_test(item))

def main():
    started = time.time()
    shutil.rmtree(BUILD_DIR, ignore_errors=True)
    tests = glob.glob(os.path.join(oneDPL_ROOT, r'test\**\*.cpp'), recursive=True)
    tests = deque(tests)
    results = deque()
    worker_thread_count = 4
    worker_threads = [threading.Thread(target=worker, args=(tests, results)) for _ in range(worker_thread_count)]
    for x in worker_threads:
        x.start()
    for x in worker_threads:
        x.join()
    results = sorted(results, key=lambda x: x.test_name)
    for x in results:
        print(x)
    failed = 0
    total = len(results)
    for x in results:
        if x.failed_phase:
            failed = failed + 1
    print("Passed {}, Failed {}".format(total - failed, failed))
    dump_results(results, 'results.xml')
    print("Completed in {} seconds".format(time.time() - started))

def dump_results(results, path):
    root = ET.Element('testsuites')
    suite = ET.SubElement(root, 'testsuite')
    suite.set('name', 'default')
    for item in results:
        test = ET.SubElement(suite, 'testcase')
        test.set('name', item.test_name)
        out = ET.SubElement(test, 'system-out')
        out.text = item.output
        if item.failed_phase:
            fail = ET.SubElement(test, 'failure')
            fail.set('message', item.output)
            fail.text = item.failed_phase
    xml_string = ET.tostring(root)
    dom = xml.dom.minidom.parseString(xml_string)
    xml_string = dom.toprettyxml()
    with open(path, 'w') as f:
        f.write(xml_string)

if __name__ == '__main__':
    main()