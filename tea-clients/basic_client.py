import grpc
import application.core_util_pb2_grpc as app_grpc
import application.core_util_pb2 as app_msg

def run_analysis(filename, compile_flags, analyses, alarm_rels, need_rank, tests, core_addr, options):
    print("Querying core server [%s] for file \"%s\" with flag \"%s\", requiring analyses \"%s\", for %s alarms \"%s\", providing %d test cases, other options %s"%(core_addr, filename, " ".join(compile_flags), ",".join(analyses), "ranked" if need_rank else "unordered", ",".join(alarm_rels), len(tests), str(options)))
    req = app_msg.ApplicationRequest()
    for key in options:
        req.option[key] = options[key]
    req.source.source = filename
    for flag in compile_flags:
        req.source.flag.append(flag)
    for analysis in analyses:
        req.analysis.append(analysis)
    for alarm in alarm_rels:
        req.alarm_rel.append(alarm)
    if need_rank:
        req.need_rank = True
        for i in range(len(tests)):
            test = app_msg.Test(arg=tests[i], test_id=str(i))
            req.test_suite.append(test)
    channel = grpc.insecure_channel(core_addr)
    core_stub = app_grpc.CoreServiceStub(channel)
    for resp in core_stub.RunAnalyses(req, wait_for_ready=True):
        if resp.msg is not None:
            print(resp.msg)
        for alarm in resp.alarm:
            print(alarm)

import sys
import configparser

if len(sys.argv) != 4:
    print("Usage: ./basic_client.py <config_file> <source_file> <compile_flags>")
    sys.exit(-1)

compile_flags = sys.argv[3].split()
filename = sys.argv[2]
config_file = sys.argv[1]
config = configparser.ConfigParser()
config.read(config_file)
addr = "localhost:" + config["core"]["port"].strip('\"')
analyses = config["project"]["analyses"].strip('\"').split(",")
alarms = config["project"]["alarms"].strip('\"').split(",")

if "rank" in config["project"]:
    need_rank = config["project"]["rank"].casefold() == "true".casefold()
else:
    need_rank = False

tests = []
if "tests" in config["project"]:
    input_file = config["project"]["tests"].strip('\"')
    with open(input_file) as f:
        for line in f:
            tests.append(line.split())

reserved_keys = ["analyses", "alarms", "rank", "tests"]
options = {key: config["project"][key] for key in config["project"] if not key in reserved_keys}

run_analysis(filename, compile_flags, analyses, alarms, need_rank, tests, addr, options)