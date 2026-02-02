#!/bin/sh

base_dir=`dirname $0`/../
build_dir=$base_dir/build
zstack_dir=`find $build_dir -name zstack`
jar_dir=$zstack_dir/lib
conf_dir=$zstack_dir/conf
classpath=
is_suspend="$1"

# 加载Java 17 JVM参数
jvm_options_file="$conf_dir/jvm-options-java17.conf"
jvm_options=""
if [ -f "$jvm_options_file" ]; then
    jvm_options=$(cat "$jvm_options_file" | grep -v '^#' | tr '\n' ' ')
fi

if [ x"$is_suspend" == x"true" ]; then
    java_optitons="$jvm_options -Xdebug -Xms256m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=y"
else
    java_optitons="$jvm_options -Xdebug -Xms256m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=n"
fi


error_exit() {
    echo $@ && exit1
}

build_classpath() {
    jar_list=`ls $jar_dir`
    for jar in $jar_list
    do
        jar_path=$jar_dir/$jar
        classpath=$classpath:$jar_path
    done
    classpath=$classpath:$conf_dir
    echo "CLASSPATH: $classpath"
}

debug() {
    java $java_optitons -cp $classpath com.zstack.server.Main
}

main() {
    [ x"$zstack_dir" == x"" ] && error_exit "Cannot find zstack dir under $build_dir, please run 'mvn package' before 'mvn exec:exec -Ddebug'"
    build_classpath
    debug
}

main

