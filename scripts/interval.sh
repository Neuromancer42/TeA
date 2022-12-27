#!/bin/bash

usage() { echo "Usage: $0 [-c configfile] [-f sourcefile] [-a compileflags] [-o outdir]" 1>&2; exit 1; }

echo "Run in TEA_HOME: $TEA_HOME"
pushd "$TEA_HOME" || exit 1

config_file="../scripts/interval.ini"
outdir="test-out"
compile_flags=""

while getopts c:f:a:o: flag
do
  case "${flag}" in
    c) config_file=${OPTARG};;
    f) source_file=${OPTARG};;
    a) compile_flags=${OPTARG};;
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

if [[ ! -d ${outdir} ]]; then
  mkdir ${outdir};
fi

for java_package in tea-cdt-codemanager tea-jsouffle tea-absdomain
do
  nohup ../${java_package}/build/install/${java_package}/bin/${java_package} ${config_file} ${outdir} > ${outdir}/${java_package}.log 2>&1 &
done

nohup ../tea-core/build/install/tea-core/bin/tea-core ${config_file} ${outdir} > ${outdir}/tea-core.log 2>&1 &

python3 ../tea-clients/basic_client.py ${config_file} ${source_file} "${compile_flags}"

popd || exit 1




