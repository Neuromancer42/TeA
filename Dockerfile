FROM ubuntu:20.04 as base
ARG DEBIAN_FRONTEND=noninteractive

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

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=ibm-semeru-runtimes:open-17-jdk-focal $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

FROM base AS souffle-builder
RUN wget -qO- https://github.com/souffle-lang/souffle/archive/refs/tags/2.3.tar.gz | tar -xz
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

FROM tea-env AS tea-java-devenv
WORKDIR /tea
COPY gradle/ ./gradle
COPY gradlew gradlew.bat ./
COPY settings.gradle build.gradle ./
COPY ir ./ir
COPY proto ./proto
COPY tea-commons ./tea-commons
COPY tea-jlibdai ./tea-jlibdai
COPY tea-jsouffle ./tea-jsouffle
COPY tea-absdomain ./tea-absdomain
#COPY tea-cdt-codemanager ./tea-cdt-codemanager
COPY tea-core ./tea-core

FROM tea-java-devenv AS tea-build
RUN ./gradlew installDist

#FROM tea-env AS tea-cdt-codemanager
#COPY --from=tea-build /tea/tea-cdt-codemanager/build/install/tea-cdt-codemanager /opt/tea/
#WORKDIR /ws

FROM tea-env AS tea-absdomain
COPY --from=tea-build /tea/tea-absdomain/build/install/tea-absdomain /opt/tea/
WORKDIR /ws

FROM tea-env AS tea-jsouffle
COPY --from=tea-build /tea/tea-jsouffle/build/install/tea-jsouffle /opt/tea/
WORKDIR /ws

FROM tea-env AS tea-core
COPY --from=tea-build /tea/tea-core/build/install/tea-core /opt/tea/
WORKDIR /ws

FROM tea-java-devenv