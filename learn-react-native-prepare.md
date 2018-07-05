### 前言

React Native 是目前最流行的跨平台框架，并且是 Facebook 团队开源的项目。架构及实现技术上都有很高的研究价值，本系列就来分析一下 React Native 的一些核心代码。

> 此系列文章针对的是有过 React Native 开发经验的小伙伴，没有 React Native 开发基础的小伙伴，可以先根据[官方文档](https://facebook.github.io/react-native/docs/building-from-source)进行学习后，再来阅读此系列文章

### 环境
以下是笔者研究 React Native 源码时的环境

- OS: MacOS 10.13.5
- **Node: v10.5.0**
- Yarn: 1.7.0
- npm:  6.1.0
- Android Studio: Version 3.1.3
- **NDK: android-ndk-r10e**
- React Native: 0.56-stable 分支

> 具体的环境可以因人而异，但是请保证 Node 的版本需在 8 及以上， NDK 的版本一定要保证是 r10e, 具体的描述可参考[官方文档](https://facebook.github.io/react-native/docs/building-from-source)
### 源码目录分析
本系列将基于最新的 [0.56-stable](https://github.com/facebook/react-native/tree/0.56-stable) 进行分析，以下是源码核心目录示意列表

```
. // react native 根目录
+-- Libraries // JS 层的实现，包括 JS 队列的封装及 UI 组件的封装
+-- local-cli // react native cli 的 JS 实现
+-- React // React Native iOS 层实现
+-- ReactAndroid // Android 层实现，本系列主要分析的地方
|   +-- src
|       +-- main
|           +-- java // Java 层实现
|           +-- jni // JNI 层的实现
+-- ReactCommon // React Native 核心代码实现目录，为了跨平台均使用 c++ 编写
+-- ReactTester // Demo 入口
+-- README.md
```

### 源码编译
将源码下载到本地之后，可以使用 Android Studio 直接打开 react native 的根目录，`RNTester` 目录下的 Android 实例项目可以作为我们分析源码的 Demo 程序。

在保证 Node 版本等于 8 及以上之后，可以直接在 react native 源码的根目录下运行

```bash
$ npm install && npm run start
```
将会下载相关依赖并且开启 react native 的本地服务来，此时 Android Studio 运行项目将会安装 RNTester App 安装到模拟器或真机上。

> 注意，如果是使用真机运行，需要 **adb reverse tcp:8081 tcp:8081** 来讲端口进行绑定

以上就是前期需要进行的准备，下面一起来分析一下 React Native 在 Android 上的启动过程
