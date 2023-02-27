#!/bin/bash

usage() { echo "Usage: $0 [-c configfile] [-f sourcefile] [-a compile_cmd] [-o outdir]" 1>&2; exit 1; }

echo "Run in TEA_HOME: $TEA_HOME"
pushd "$TEA_HOME" || exit 1

config_file="../scripts/interval.ini"
outdir="test-out"
compile_cmd=""

while getopts c:f:a:o: flag
do
  case "${flag}" in
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
  compile_cmd="gcc ${source_file} -o test"
  echo "use default compile_comd: ${compile_cmd}" 1>&2;
fi

if [[ ! -d ${outdir} ]]; then
  mkdir ${outdir};
fi

nohup_pids=()
for java_package in tea-cdt-codemanager tea-jsouffle tea-absdomain
do
  nohup ../${java_package}/build/install/${java_package}/bin/${java_package} ${config_file} ${outdir} > ${outdir}/${java_package}.log 2>&1 &
  nohup_pids=($! "${nohup_pids[@]}")
  echo "start ${java_package}, pid: ${nohup_pids[0]}"
done

nohup ../tea-core/build/install/tea-core/bin/tea-core ${config_file} ${outdir} > ${outdir}/tea-core.log 2>&1 &
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
python3 ../tea-clients/basic_client.py ${config_file} ${source_file} "${compile_cmd}"

finish_all


