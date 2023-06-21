FROM ubuntu:20.04 as base
ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get clean && apt-get update && \
    apt-get -y install --no-install-recommends \
    autoconf \
    automake \
    bash-completion \
    binutils \
    bison \
    build-essential \
    ca-certificates \
    clang \
    cmake \
    curl \
    doxygen \
    flex \
    g++ \
    git \
    gnupg \
    gnutls-bin \
    libffi-dev \
    libgmp-dev \
    libncurses5-dev \
    libboost-dev \
    libboost-program-options-dev \
    libtool \
    libsqlite3-dev \
    libstdc++-7-dev \
    make \
    mcpp \
    ninja-build \
    pkg-config \
    python \
    python3 \
    python3-pip \
    sqlite3 \
    subversion \
    sudo \
    swig \
    unzip \
    wget \
    zip \
    zlib1g \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=ibm-semeru-runtimes:open-17-jdk-focal $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ARG cmake_version=3.25.3
RUN wget -qO- https://github.com/Kitware/CMake/releases/download/v${cmake_version}/cmake-${cmake_version}-linux-x86_64.tar.gz \
    | tar -xz -C/opt/
ENV CMAKE_HOME=/opt/cmake-${cmake_version}-linux-x86_64
ENV PATH="${CMAKE_HOME}/bin:${PATH}"

FROM base AS souffle-builder
RUN wget -qO- https://github.com/souffle-lang/souffle/archive/refs/tags/2.3.tar.gz | tar -xz
WORKDIR souffle-2.3
RUN cmake -S . -B build -DSOUFFLE_SWIG_JAVA=ON -DCMAKE_INSTALL_PREFIX=/opt/souffle
RUN cmake --build build --target install --parallel "$(nproc)"

FROM base AS gradle-builder
WORKDIR /opt
RUN wget https://services.gradle.org/distributions/gradle-7.6.1-bin.zip
RUN mkdir /opt/gradle && unzip -d /opt/gradle gradle-7.6.1-bin.zip

FROM base AS llvm-builder

ARG llvm_version=15.0.7

RUN wget -qO- "https://github.com/llvm/llvm-project/releases/download/llvmorg-${llvm_version}/llvm-project-${llvm_version}.src.tar.xz" \
    | tar -xJ -C/opt

WORKDIR /tmp/llvm-project-${llvm_version}.build
RUN cmake /opt/llvm-project-${llvm_version}.src/llvm/ \
    -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DLLVM_ENABLE_PROJECTS="clang;lldb;lld" \
    -DLLVM_ENABLE_RUNTIMES="libcxx;libcxxabi" \
    -DCMAKE_INSTALL_PREFIX=/opt/llvm-project-${llvm_version}.obj/ \
    && cmake --build . --parallel "$(nproc)"\
    && cmake --build . --target install

FROM base AS grpc-builder

ARG grpc_version=v1.54.0
WORKDIR /tmp
#RUN git config --global http.proxy $HTTP_PROXY && git config --global https.proxy $HTTPS_PROXY
RUN git clone -b ${grpc_version} https://github.com/grpc/grpc --recurse-submodules
WORKDIR grpc/cmake/build/
RUN cmake ../.. -GNinja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX=/opt/grpc-${grpc_version} \
    -DgRPC_INSTALL=ON \
    -DgRPC_BUILD_TESTS=OFF \
    -DgRPC_ZLIB_PROVIDER="package" \
    && cmake --build . --parallel "$(nproc)"\
    && cmake --build . --target install

FROM base AS tea-env

ENV SOUFFLE_HOME=/opt/souffle
COPY --from=souffle-builder $SOUFFLE_HOME $SOUFFLE_HOME
ENV PATH="${SOUFFLE_HOME}/bin:${PATH}"

ENV GRADLE_HOME=/opt/gradle/gradle-7.6.1
COPY --from=gradle-builder ${GRADLE_HOME} ${GRADLE_HOME}
ENV PATH="${GRADLE_HOME}/bin:${PATH}"

ARG llvm_version=15.0.7
ENV LLVM_DIR=/opt/llvm-project-${llvm_version}.obj
COPY --from=llvm-builder ${LLVM_DIR} ${LLVM_DIR}
ENV PATH=${LLVM_DIR}/bin:$PATH

ARG grpc_version=v1.54.0
ENV GRPC_DIR=/opt/grpc-${grpc_version}
ENV PROTOBUF_DIR=${GRPC_DIR}
COPY --from=grpc-builder ${GRPC_DIR} ${GRPC_DIR}
ENV PATH=${GRPC_DIR}/bin:$PATH

WORKDIR /ws

FROM tea-env AS tea-devenv
WORKDIR /tea
COPY gradle/ ./gradle
COPY gradlew gradlew.bat ./
COPY settings.gradle build.gradle ./
COPY ir ./ir
COPY proto ./proto
COPY tea-commons ./tea-commons
COPY tea-jlibdai ./tea-jlibdai
COPY tea-core ./tea-core

COPY tea-jsouffle ./tea-jsouffle
COPY tea-absdomain ./tea-absdomain
#COPY tea-cdt-codemanager ./tea-cdt-codemanager

COPY tea-llvm-codemanager/ ./tea-llvm-codemanager

CMD bash

FROM tea-devenv AS tea-build

ENV TEA_HOME=/tea
WORKDIR /tea/
RUN ./gradlew installDist
ENV TEA_CORE=${TEA_HOME}/tea-core/build/install/tea-core/bin/tea-core
ENV TEA_ABSDOMAIN=${TEA_HOME}/tea-absdomain/build/install/tea-absdomain/bin/tea-absdomain
ENV TEA_JSOUFFLE=${TEA_HOME}/tea-jsouffle/build/install/tea-jsouffle/bin/tea-jsouffle
ENV TEA_RULES=${TEA_HOME}/tea-jsouffle/build/install/tea-jsouffle/etc/rules

WORKDIR tea-llvm-codemanager
RUN cmake -S . -B cmake-build-debug -GNinja -DCMAKE_BUILD_TYPE=Debug && cmake --build cmake-build-debug --parallel "$(nproc)"
ENV TEA_LLVM=${TEA_HOME}/tea-llvm-codemanager/cmake-build-debug/irmanager_server

WORKDIR ..
COPY tea-clients/ ./tea-clients
WORKDIR tea-clients
RUN ./update_proto.sh
ENV TEA_CLIENT=${TEA_HOME}/tea-clients/basic_client.py

WORKDIR ..
COPY scripts/ ./scripts

RUN pip3 install wllvm
ENV LLVM_COMPILER=clang

WORKDIR ${TEA_HOME}
RUN echo "built successfully"
CMD ["bash"]