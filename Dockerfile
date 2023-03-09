FROM ubuntu:20.04 as base
ARG DEBIAN_FRONTEND=noninteractive

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:17 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get clean && apt-get update && \
	apt-get -y install \
	bash-completion \
	sudo \
	autoconf \
	automake \
	bison \
	build-essential \
	clang \
	doxygen \
	flex \
	g++ \
	git \
	libffi-dev \
	libncurses5-dev \
	libtool \
	libsqlite3-dev \
	make \
	mcpp \
	python \
	sqlite \
	zlib1g-dev \
	cmake \
	wget \
	swig \
	libboost-dev \
	libboost-program-options-dev \
	libgmp-dev \
    unzip \
    && rm -rf /var/lib/apt/lists/*

FROM base AS souffle-builder
RUN wget https://github.com/souffle-lang/souffle/archive/refs/tags/2.3.tar.gz -O souffle-2.3.tar.gz
RUN tar xf souffle-2.3.tar.gz
WORKDIR souffle-2.3
RUN cmake -S . -B build -DSOUFFLE_SWIG_JAVA=ON -DCMAKE_INSTALL_PREFIX=/opt/souffle
RUN cmake --build build --target install --parallel "$(nproc)"

FROM base AS cmake-builder
WORKDIR /opt
RUN wget https://github.com/Kitware/CMake/releases/download/v3.25.2/cmake-3.25.2-linux-x86_64.sh
RUN mkdir cmake
RUN bash cmake-3.25.2-linux-x86_64.sh --prefix=/opt/cmake --skip-license

FROM base AS gradle-builder
WORKDIR /opt
RUN wget https://services.gradle.org/distributions/gradle-7.6.1-bin.zip
RUN mkdir /opt/gradle && unzip -d /opt/gradle gradle-7.6.1-bin.zip

FROM base AS tea-env

ENV SOUFFLE_HOME=/opt/souffle
COPY --from=souffle-builder $SOUFFLE_HOME $SOUFFLE_HOME
ENV PATH="${SOUFFLE_HOME}/bin:${PATH}"

ENV CMAKE_HOME=/opt/cmake
COPY --from=cmake-builder $CMAKE_HOME $CMAKE_HOME
ENV PATH="${CMAKE_HOME}/bin:${PATH}"

ENV GRADLE_HOME=/opt/gradle/gradle-7.6.1
COPY --from=gradle-builder ${GRADLE_HOME} ${GRADLE_HOME}
ENV PATH="${GRADLE_HOME}/bin:${PATH}"

FROM tea-env AS tea-debug
WORKDIR /tea
ADD . .

FROM tea-debug AS tea-build
RUN ./gradlew installDist

FROM tea-env AS tea-release
ENV TEA_HOME=/opt/tea
WORKDIR $TEA_HOME
COPY --from=tea-build /tea/tea-cdt-codemanager/build/install/tea-cdt-codemanager .
COPY --from=tea-build /tea/tea-absdomain/build/install/tea-absdomain .
COPY --from=tea-build /tea/tea-jsouffle/build/install/tea-jsouffle .
COPY --from=tea-build /tea/tea-core/build/install/tea-core .
WORKDIR /ws

FROM tea-debug