import grpc
import application.core_util_pb2_grpc as app_grpc
import application.core_util_pb2 as app_msg

def run_analysis(filename, analyses, alarm_rels, core_addr, options):
    print("Querying core server [%s] for file \"%s\", requiring analyes \"%s\""%(core_addr, filename, ",".join(analyses)))
    req = app_msg.ApplicationRequest()
    for key in options:
        req.option[key] = options[key]
    req.source.source = filename
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

if len(sys.argv) != 5:
    print("Usage: ./basic_client.py <filename> <analysis1;analysis2;...> <alarm1;alarm2;...> <config_file>")
    sys.exit(-1)

filename = sys.argv[1]
analyses = sys.argv[2].split(",")
alarms = sys.argv[3].split(",")
config_file = sys.argv[4]
config = configparser.ConfigParser()
config.read(config_file)
addr = "localhost:" + config["core"]["port"]
options = {key: config["project"][key] for key in config["project"]}

run_analysis(filename, analyses, alarms, addr, options)