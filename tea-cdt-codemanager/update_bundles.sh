#!/bin/bash
while read -r bundle; do
echo $bundle
bnd repo -w repo.bndrun get -o p2-compile-bundles $bundle
done < compile-bundles.list

while read -r bundle; do
echo $bundle
bnd repo -w repo.bndrun get -o p2-runtime-bundles $bundle
done < runtime-bundles.list