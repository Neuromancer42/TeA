import grpc
import application.core_util_pb2_grpc as app_grpc
import application.core_util_pb2 as app_msg

def run_analysis(filename, compile_flags, analyses, alarm_rels, core_addr, options):
    print("Querying core server [%s] for file \"%s\" with flag \"%s\", requiring analyes \"%s\""%(core_addr, filename, " ".join(compile_flags), ",".join(analyses)))
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
reserved_keys = ["analyses", "alarms"]
options = {key: config["project"][key] for key in config["project"] if not key in reserved_keys}

run_analysis(filename, compile_flags, analyses, alarms, addr, options)