#!/bin/bash

usage() { echo "Usage: $0 [-c configfile] [-f sourcefile] [-a compile_cmd] [-o outdir] [-b souffle_libdir] [-d dist_file] [-t pre_test] [-j jobs]" 1>&2; exit 1; }

echo "Run in TEA_HOME: $TEA_HOME"
pushd "$TEA_HOME" || exit 1

config_file="test-llvm.ini"
outdir="test-llvm"
compile_cmd=""
proj=`date +"p%b %d, %Y"`
souffle_libdir="souffle_cache"
jobs=8

while getopts p:c:f:a:o:b:d:t:j: flag
do
  case "${flag}" in
    p) proj=${OPTARG};;
    c) config_file=${OPTARG};;
    f) source_file=${OPTARG};;
    a) compile_cmd=${OPTARG};;
    b) souffle_libdir=${OPTARG};;
    d) dist_file=${OPTARG};;
    o) outdir=${OPTARG};;
    t) pretest=${OPTARG};;
    j) jobs=${OPTARG};;
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
  compile_cmd="";
fi
echo "use compile_cmd: ${compile_cmd}"

if [[ -z ${outdir} ]]; then
  outdir="/tmp";
fi
if [[ ! -d ${outdir} ]]; then
  mkdir -p ${outdir};
fi
echo "Dumping logs to: ${outdir}"

if [[ -z ${TEA_ABSDOMAIN} ]]; then
  TEA_ABSDOMAIN=../tea-absdomain/build/install/tea-absdomain/bin/tea-absdomain;
fi
echo "Using tea_absdomain: ${TEA_ABSDOMAIN}"

if [[ -z ${TEA_LLVM} ]]; then
  TEA_LLVM=../tea-llvm-codemanager/cmake-build-debug/irmanager_server;
fi
echo "Using tea_llvm: ${TEA_LLVM}"

if [[ -z ${TEA_JSOUFFLE} ]]; then
  TEA_JSOUFFLE=../tea-jsouffle/build/install/tea-jsouffle/bin/tea-jsouffle;
fi
echo "Using tea_jsouffle: ${TEA_JSOUFFLE}"

if [[ -z ${TEA_RULES} ]]; then
  TEA_RULES=../tea-jsouffle/src/main/dist/etc/rules
fi
echo "Using rules in: ${TEA_RULES}"
echo "Build souffle libs in: ${souffle_libdir}"

if [[ -z ${TEA_CORE} ]]; then
  TEA_CORE=../tea-core/build/install/tea-core/bin/tea-core;
fi
echo "Using tea_core: ${TEA_CORE}"
if [[ -z ${dist_file} || ! -f ${dist_file} ]]; then
  dist_file="empty.dists"
  touch ${dist_file}
fi
echo "Using prior distribution: ${dist_file}"

if [[ -z ${TEA_CLIENT} ]]; then
  TEA_CLIENT=../tea-clients/basic_client.py
fi
echo "Using tea_client: ${TEA_CLIENT}"

if [[ ! -z ${pretest} ]]; then
  echo "Prepare testing file: ${pretest}"
  ${pretest}
fi
nohup_pids=()

nohup ${TEA_ABSDOMAIN} -p 10004 -d ${outdir} > ${outdir}/tea-absdomain.log 2>&1 &
nohup_pids=($! "${nohup_pids[@]}")
echo "start tea-absdomain, pid: ${nohup_pids[0]}"

nohup ${TEA_LLVM} -p 10003 -d ${outdir} > ${outdir}/tea-llvm-codemanager.log 2>&1 &
nohup_pids=($! "${nohup_pids[@]}")
echo "start tea-llvm-codemanager, pid: ${nohup_pids[0]}"

nohup ${TEA_JSOUFFLE} -p 10002 -d ${outdir} -b ${souffle_libdir} \
  -A prept=${TEA_RULES}/flow_insensitive.dl \
  -A cg=${TEA_RULES}/cipa_cg.dl \
  -A fieldpt=${TEA_RULES}/field_pt.dl \
  -A simplify=${TEA_RULES}/simplify_llvm_cfg.dl \
  -A modref=${TEA_RULES}/modref.dl \
  -A itv=${TEA_RULES}/cwe_interval.dl \
  -A heaptype=${TEA_RULES}/heap_type.dl \
  > ${outdir}/tea-jsouffle.log 2>&1 &
nohup_pids=($! "${nohup_pids[@]}")
echo "start tea-jsouffle, pid: ${nohup_pids[0]}"

nohup ${TEA_CORE} -p 10001 -d ${outdir} -j ${jobs} \
  -Q souffle=localhost:10002 \
  -Q llvm=localhost:10003 \
  -Q absdomain=localhost:10004 \
  -t ${dist_file} \
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
${TEA_CLIENT} localhost:10001 ${proj} ${config_file} ${source_file} "${compile_cmd}"

finish_all


