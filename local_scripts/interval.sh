#!/bin/bash

usage() { echo "Usage: $0 [-c configfile] [-f sourcefile] [-a compile_cmd] [-o outdir]" 1>&2; exit 1; }

echo "Run in TEA_HOME: $TEA_HOME"
pushd "$TEA_HOME" || exit 1

config_file="test-llvm.ini"
outdir="test-llvm"
compile_cmd=""
proj=`date +"p%b %d, %Y"`

while getopts p:c:f:a:o: flag
do
  case "${flag}" in
    p) proj=${OPTARG};;
    c) config_file=${OPTARG};;
    f) source_file=${OPTARG};;
    a) compile_cmd=${OPTARG};;
    o) outdir=${OPTARG};;
    *) usage;;
  esac
done

if [[ ! -f ${config_file} ]]; then
  echo "config ${config_file} does not exist" 1>&2;
  exit 1;
fi

if [[ ! -f ${source_file} ]]; then
  echo "source ${source_file} does not exist" 1>&2;
  exit 1;
fi

if [[ -z ${compile_cmd} ]]; then
  compile_cmd=""
  echo "use empty compile_comd: ${compile_cmd}" 1>&2;
fi

if [[ ! -d ${outdir} ]]; then
  mkdir -p ${outdir};
fi

nohup_pids=()

nohup ../tea-absdomain/build/install/tea-absdomain/bin/tea-absdomain -p 10004 -d ${outdir} > ${outdir}/tea-absdomain.log 2>&1 &
nohup_pids=($! "${nohup_pids[@]}")
echo "start tea-absdomain, pid: ${nohup_pids[0]}"

nohup ../tea-llvm-codemanager/cmake-build-debug/irmanager_server -p 10003 -d ${outdir} > ${outdir}/tea-llvm-codemanager.log 2>&1 &
nohup_pids=($! "${nohup_pids[@]}")
echo "start tea-llvm-codemanager, pid: ${nohup_pids[0]}"

nohup ../tea-jsouffle/build/install/tea-jsouffle/bin/tea-jsouffle -p 10002 -d ${outdir} -b souffle_itv_new \
  -A prept=../tea-jsouffle/src/main/dist/souffle-scripts/flow_insensitive.dl \
  -A cg=../tea-jsouffle/src/main/dist/souffle-scripts/cipa_cg.dl \
  -A fieldpt=../tea-jsouffle/src/main/dist/souffle-scripts/field_pt.dl \
  -A simplify=../tea-jsouffle/src/main/dist/souffle-scripts/simplify_llvm_cfg.dl \
  -A modref=../tea-jsouffle/src/main/dist/souffle-scripts/modref.dl \
  -A itv=../tea-jsouffle/src/main/dist/souffle-scripts/cwe_interval.dl \
  -A heaptype=../tea-jsouffle/src/main/dist/souffle-scripts/heap_type.dl \
  > ${outdir}/tea-jsouffle.log 2>&1 &
nohup_pids=($! "${nohup_pids[@]}")
echo "start tea-jsouffle, pid: ${nohup_pids[0]}"

nohup ../tea-core/build/install/tea-core/bin/tea-core -p 10001 -d ${outdir} \
  -Q souffle=localhost:10002 \
  -Q llvm=localhost:10003 \
  -Q absdomain=localhost:10004 \
  -t ../example_configs/interval.dists \
  > ${outdir}/tea-core.log 2>&1 &
nohup_pids=($! "${nohup_pids[@]}")
echo "start tea-core, pid: ${nohup_pids[0]}"

finish_all () {
  for pid in "${nohup_pids[@]}"
  do
    echo "killing pid: $pid"
    kill -9 $pid
  done
  popd || exit 1
  exit 0
}

trap finish_all SIGINT
python3 ../tea-clients/basic_client.py localhost:10001 ${proj} ${config_file} ${source_file} "${compile_cmd}"

finish_all


