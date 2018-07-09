### 前言
这篇开始将分析 React Native 是在 Android 端的启动过程是怎么样。

用过 React Native 的小伙伴都知道，React Native 是可以使用 JS 来编写一份代码，并可以同时跑在 Android 和 iOS 两个平台上。那其实核心思想就是 JS 跟 Android 之间建立起一种通信机制。

对于 Android 调用 JS 代码的方法有以下两种:
- 通过 `WebView` 的 `loadUrl()` 函数
- 通过 `WebView` 的 `evaluateJavascript()` 函数

对于 JS 调用 Android 代码的方法有以下三种:
- 通过 `WebView` 的 `addJavascriptInterface()`进行对象映射
- 通过 `WebViewClient` 的 `shouldOverrideUrlLoading()` 方法回调拦截 url
- 通过 `WebChromeClient` 的 `onJsAlert()`、`onJsConfirm()`、`onJsPrompt()` 方法回调拦截 JS 对话框 `alert()`、`confirm()`、`prompt()` 消息

但是以上都是要基于 `WebView` 才可以实现，但是 React Native 并没有使用 `WebView` 来实现 JS 和 Android 间的通信，而是采用 JavaScriptCore 来实现 JS 的解析。

那分析 React Native 的启动过程，也就是分析 React Native 是如何让 Android 与 JavaScriptCore 进行关联的

### 从 RNTester App Demo 出发
查看 React Native 项目 RNTester 下的 Android Demo。

> 文中所有代码实例都只会截取核心代码，想查看完整代码请直接查看源码。

```Java
public class RNTesterApplication extends Application implements ReactApplication {
  private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
    @Override
    public String getJSMainModuleName() {
      // js 入口文件地址
      return "RNTester/js/RNTesterApp.android";
    }

    @Override
    public @Nullable String getBundleAssetName() {
        // js bundle打包后 放在 asset 目录下的文件名称
      return "RNTesterApp.android.bundle";
    }

    ...
  };

  @Override
  public ReactNativeHost getReactNativeHost() {
    return mReactNativeHost;
  }
};
```

再看 `RNTesterActivity.java` 的代码

```Java
public class RNTesterActivity extends ReactActivity {

    ...

  @Override
  protected String getMainComponentName() {
  // 用来返回要显示的js端的组件的名称，这个要和 js 端注册的 Component 名称一一对应
    return "RNTesterApp";
  }
}
```

有点 React Native 的小伙伴知道，上面的 `getJSMainModuleName`，`getMainComponentName`，必须要跟 JS 代码保持一直，否则运行程序会找不到对应的 JS 代码，但是为什么要保持一直，这个疑问我们先记下，继续往后面看。

可以看到 `RNTesterActivity` 是集成自 `ReactActivity`。 `ReactActivity` 是 rn 中页面显示的入口，负责页面的显示。

进入 `ReactActivity`的 `onCreate` 中发现 `ReactActivity` 只是一个空壳子，所有的逻辑都交给 `ReactActivityDelegate` 类实现，这是典型的代理模式，这样做的好处：
- 实现和接口分开
- 可以在 `FragmentActivity` 也同样可以使用，不用维护两套逻辑

查看 `ReactActivityDelegate` 中 `onCreate` 发现最终是调用了 `loadApp` 函数

```Java
  protected void loadApp(String appKey) {
    ...

    mReactRootView = createRootView();

    // rn 启动入口
    mReactRootView.startReactApplication(
      getReactNativeHost().getReactInstanceManager(),
      appKey,
      getLaunchOptions());

    // ReactRootView 作为 ReactActivity 的根 View
    getPlainActivity().setContentView(mReactRootView);
  }
```

这个函数主要实现两个功能：
- 创建 `ReactRootView`，并将这个 view 设置为当前 Activity 的根 view
- 调用 ReactRootView 的 startReactApplication 方法，启动 rn 流

> ReactRootView 继承 FrameLayout，它主要负责 native 端事件（键盘事件、touch事件、页面大小变化等）的监听并将结果传递给 js 端以及负责页面元素的重新绘制。涉及东西将多，之后会专门进行分析。

通过 `startReactApplication` 方法名也可知，这里才是 RN 启动的入口处。

### RN 关键类登场
这里开始 RN 中的关键类就会陆续登场了。这里我们先简单进行一下相关介绍，让读者有个印象。这里大部分的核心类都是采用面向接口编程的思想，括号中是接口对应的实现类。

#### ReactInstanceManager
rn 的 java 端的控制器，它主要的功能是创建和管理 CatalystInstance，ReactContext 实例并和 ReactActivity 的生命周期保持一致
#### CatalystInstance (CatalystInstanceImpl)
jsc 桥梁接口类，为 java 和 js 相互通信提供环境。在 c++ 也有其对应的实现类
#### JSCJavaScriptExecutor
是 JavaScriptExecutor 的子类，是 js 执行器
#### JSBundleLoader
bundle.js 文件加载器，在 rn 中有三种加载方式：1、加载本地文件；2、加载网络文件，并将文件缓存；3、加载网络文件，用于 debug 调试。前面 Demo Applicatoin 类中的 `getBundleAssetName` 最终也是会转化为 JSBundleLoader
#### ModuleSpec
NativeModule的包装类，主要是为了实现 module 的懒加载，由于 rn 中 native module 比较多，为了节省成本，rn 中采用时懒加载的策略，只有相应的 module 使用时才进行创建。
#### JavaScriptModule
接口类，用于 java 调用 js 的接口，在 rn 中没有实现类，具体如何使用后面再介绍
#### JavaScriptModuleRegistry
JavaScriptModule 的注册表。
#### NativeModuleRegistry
NativeModule 的注册表，用于管理 NativeModule 列表。
#### NativeModule
java 暴露给 js 调用的 api 接口，如果想创建自己的 module，需要继承这个接口。
#### ReactPackage
组件配置接口类，通过 createNativeModules、createJSModules 和 createViewManagers 等API去创建本地模块，JS 模块及视图组件等。 ReactPackage 分为 rn 核心的 CoreModulesPackage 和业务方可选的基础 MainReactPackage 类，其中 CoreModulesPackage 封装了大部分通信功能。
#### ReactContext
整个启动流程重要创建实例之一就是ReactContext, ReactContext继承于ContextWrapper，是ReactNative应用的上下文，通过getContext()去获得，通过它可以访问ReactNative核心类的实现。

#### 继续分析源码
以上就是 RN 中反复出现的关键类。接着跟着源码走

```Java
// ReactRootView.java
public void startReactApplication(
      ReactInstanceManager reactInstanceManager,
      String moduleName,
      @Nullable Bundle initialProperties) {
        ...

      if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
        //
        mReactInstanceManager.createReactContextInBackground();
      }

      attachToReactInstanceManager();

        ...
  }
```

我们这次先来看 `attachToReactInstanceManager` 函数，看代码这个函数会在 ReactContext 创建之后才会调用。继续深入发现最终到了 ReactInstanceManager 的调用

```Java
// ReactInstanceManager.java

  private void attachRootViewToInstance(
      final ReactRootView rootView,
      CatalystInstance catalystInstance) {
    ...
    // 最终调用 AppRegistry.js 的 runApplication 方法
    rootView.invokeJSEntryPoint();
    ...
  }
```

发现最终又回到了 ReactRootView 中

```Java
// ReactRootView.java
  private void defaultJSEntryPoint() {
        ...
        ReactContext reactContext = mReactInstanceManager.getCurrentReactContext();
        if (reactContext == null) {
          return;
        }

        CatalystInstance catalystInstance = reactContext.getCatalystInstance();

        ...

        String jsAppModuleName = getJSModuleName();

        // 调用 js module
        catalystInstance.getJSModule(AppRegistry.class).runApplication(jsAppModuleName, appParams);
  }
```
在这里我们发现了一个非常眼熟的类名 `AppRegistry.class`, 这个不就是在所有入口 js 中都要写的一行代码中的 `AppRegistry`

```javascript
AppRegistry.registerComponent('RNTesterApp', () => RNTesterApp);
```
所以 `defaultJSEntryPoint` 函数最终调起了 js 的入口。 再看 `defaultJSEntryPoint` 函数，发现里面同时用到了 `ReactContext`， `CatalystInstance`，`ReactInstanceManager`。所以验证了他们三者相互之间的重要关系。也说明了 React Native 的启动重要的就是要创建 `ReactContext`。至于 Java 是如何调用的 JS，我们稍后再分析，现在我们来分析 `ReactContext` 是如何创建的。

沿着 `createReactContextInBackground` 的函数流，会发现最终不管是采用加载网络还是本地的 bundle 文件最终都是会到达 `runCreateReactContextOnNewThread` 方法中

```Java
// ReactInstanceManager.java
  private void runCreateReactContextOnNewThread(final ReactContextInitParams initParams) {
    ...
    mCreateReactContextThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                    ...
                  // 核心部分，创建 ReactContext
                  final ReactApplicationContext reactApplicationContext =
                      createReactContext(
                          initParams.getJsExecutorFactory().create(),
                          initParams.getJsBundleLoader());

                  mCreateReactContextThread = null;
                  ReactMarker.logMarker(PRE_SETUP_REACT_CONTEXT_START);
                    ...
                  Runnable setupReactContextRunnable =
                      new Runnable() {
                        @Override
                        public void run() {
                          try {
                            // 最终会调用 attachRootViewToInstance 即调用 defaultJSEntryPoint 启动 js 入口
                            setupReactContext(reactApplicationContext);
                          } catch (Exception e) {
                            mDevSupportManager.handleException(e);
                          }
                        }
                      };

                    ...
                  UiThreadUtil.runOnUiThread(maybeRecreateReactContextRunnable);
              }
            });
    // 启动新线程
    mCreateReactContextThread.start();
  }
```
这个函数主要做了以下几件事:
1. 如果已存在 ReactContext，将其进行销毁
2. 开启一个新的线程用于创建 ReactContext
3. ReactContext 创建成功之后，在 UI 线程中最终函数流会调用 `defaultJSEntryPoint` 方法来启动 js 的入口程序

### 硬骨头 createReactContext
接下来才是整个 RN 的硬骨头，只要将其啃下，对 RN 底层的实现也就理解了。因为这里主要涉及到了 C++ 层面的核心实现，且代码实现较长，故不会将代码都贴出来，还是希望读者可以结合着源码和这篇文章来分析这块。

先上`createReactContext` 函数代码
```Java
  private ReactApplicationContext createReactContext(
      JavaScriptExecutor jsExecutor,
      JSBundleLoader jsBundleLoader) {
        ...
    // 将核心 CoreModulesPackage 和 业务基础 MainReactPackage 中的 NativeModule 添加到注册表中
    NativeModuleRegistry nativeModuleRegistry = processPackages(reactContext, mPackages, false);

    CatalystInstanceImpl.Builder catalystInstanceBuilder = new CatalystInstanceImpl.Builder()
      .setReactQueueConfigurationSpec(ReactQueueConfigurationSpec.createDefault()) // 初始化 native 线程队列及 js 线程队列
      .setJSExecutor(jsExecutor) // js 执行器
      .setRegistry(nativeModuleRegistry) // 本地提供给 JS 调用的 NativeModule 注册表
      .setJSBundleLoader(jsBundleLoader) // bundle 信息类
      .setNativeModuleCallExceptionHandler(exceptionHandler);
        ...
    final CatalystInstance catalystInstance;
        ...
      catalystInstance = catalystInstanceBuilder.build();
        ...
    // 加载 js bundle 文件
    catalystInstance.runJSBundle();

    reactContext.initializeWithInstance(catalystInstance);

    return reactContext;
  }
```
这个函数主要做了以下功能:
1. 根据 ReactPackage 列表生成 Native Module 的注册表
2. 创建 Native 及 JS 的线程队列
3. 创建 CatalystInstance 实例
4. 加载 bundle 文件
5. 将 UI，Native 及 JS 的线程队列赋予 ReactContext

CatalystInstanceImpl 作为整个 RN 中举足轻重的角色，来看一下它的构造函数。

```Java
  private CatalystInstanceImpl(
      final ReactQueueConfigurationSpec reactQueueConfigurationSpec,
      final JavaScriptExecutor jsExecutor,
      final NativeModuleRegistry nativeModuleRegistry,
      final JSBundleLoader jsBundleLoader,
      NativeModuleCallExceptionHandler nativeModuleCallExceptionHandler) {
    // 找到于java相对应的c++类并调用其构造方法生成对象
    mHybridData = initHybrid();

    mReactQueueConfiguration = ReactQueueConfigurationImpl.create(
        reactQueueConfigurationSpec,
        new NativeExceptionHandler());
    mBridgeIdleListeners = new CopyOnWriteArrayList<>();
    mNativeModuleRegistry = nativeModuleRegistry;
    mJSModuleRegistry = new JavaScriptModuleRegistry();
    mJSBundleLoader = jsBundleLoader;
    mNativeModuleCallExceptionHandler = nativeModuleCallExceptionHandler;
    mNativeModulesQueueThread = mReactQueueConfiguration.getNativeModulesQueueThread();
    mTraceListener = new JSProfilerTraceListener(this);

    // 调用的是 c++ 中对应的实现
    initializeBridge(
      new BridgeCallback(this),
      jsExecutor,
      mReactQueueConfiguration.getJSQueueThread(),
      mNativeModulesQueueThread,
      mNativeModuleRegistry.getJavaModules(this),
      mNativeModuleRegistry.getCxxModules());

    // JSGlobalContextRef 的内存地址
    mJavaScriptContextHolder = new JavaScriptContextHolder(getJavaScriptContext());
  }
```

构造函数中有两个需要关注的地方
1. HybridData 对象在 rn 中会反复的出现，它的主要作用是找到于 java 相对应的 c++ 类并调用其构造方法生成对象，把 new 出来对象的地址放到 java 的 HybridData 对象中
2. ReactQueueConfigurationSpec：用于配置消息线程，在 rn 中有三个消息线程：UI 线程、JS 线程、Native 线程，其中 native 调用 js 的代码会 JS 线程运行，JS 调用 native 的代码会在 Native 线程中执行

### C++ 层核心类登场
在继续之前需要讲解一下 Native 层的核心类

#### NativeToJsBridge
NativeToJsBridge是Java调用JS的桥梁，用来调用JS Module，回调Java

#### JsToNativeBridge
JsToNativeBridge是JS调用Java的桥梁，用来调用Java Module

#### JSCExecutor
native 层的 js 执行器

#### CatalystInstanceImpl
CatalystInstanceImpl 在 c++ 层对应的实现

#### Instance
可以看作是 NativeToJsBridge 的代理类，最终的处理都是交由了 NativeToJsBridge

### 分析 Native 层
从这里开始将会讨论 Native 层对应的操作，上面提到 `createReactContext` 函数里进行了 `CatalystInstanceImpl` 的初始化，而最终的核心其实是在 Native 进行的。看一下 Native 层中 `CatalystInstanceImpl` 的 `initializeBridge` 函数

```c++
// CatalystInstanceImpl.cpp
void CatalystInstanceImpl::initializeBridge(
    jni::alias_ref<ReactCallback::javaobject> callback,
    // This executor is actually a factory holder.
    JavaScriptExecutorHolder* jseh,
    jni::alias_ref<JavaMessageQueueThread::javaobject> jsQueue,
    jni::alias_ref<JavaMessageQueueThread::javaobject> nativeModulesQueue,
    jni::alias_ref<jni::JCollection<JavaModuleWrapper::javaobject>::javaobject> javaModules,
    jni::alias_ref<jni::JCollection<ModuleHolder::javaobject>::javaobject> cxxModules) {

  moduleMessageQueue_ = std::make_shared<JMessageQueueThread>(nativeModulesQueue);

    // 在 native 层将 JavaNativeModule 和 CxxNativeModule 保存到 注册表里
  moduleRegistry_ = std::make_shared<ModuleRegistry>(
    buildNativeModuleList(
       std::weak_ptr<Instance>(instance_),
       javaModules,
       cxxModules,
       moduleMessageQueue_));

  instance_->initializeBridge(
    folly::make_unique<JInstanceCallback>(
    callback,
    moduleMessageQueue_),
    jseh->getExecutorFactory(),
    folly::make_unique<JMessageQueueThread>(jsQueue),
    moduleRegistry_);
}
```

这个函数主要做了以下几个功能:
1. 将 java 层的对象分装成 JavaNativeModule 和 CxxNativeModule 对象，并将生成的对象注册到 ModuleRegistry 对象中，ModuleRegistry和上面提高的 NativeModuleRegistry 功能相似，是 C++ 端 module 的注册表, 并且将 java 的 MessageQueueThread 也传入每个 NativeModule 中以便之后可以直接调用
2. 跳转到 `Instance.cpp` 的 initializeBridge 方法中

```c++
// Instance.cpp
void Instance::initializeBridge(
    std::unique_ptr<InstanceCallback> callback,
    std::shared_ptr<JSExecutorFactory> jsef,
    std::shared_ptr<MessageQueueThread> jsQueue,
    std::shared_ptr<ModuleRegistry> moduleRegistry) {
  callback_ = std::move(callback); // 含有
  moduleRegistry_ = std::move(moduleRegistry);

    // 在 js 线程队列中初始化 NativeToJsBridge
  jsQueue->runOnQueueSync([this, &jsef, jsQueue]() mutable {
      // 初始化 NativeToJsBridge
    nativeToJsBridge_ = folly::make_unique<NativeToJsBridge>(
        jsef.get(), moduleRegistry_, jsQueue, callback_);

    std::lock_guard<std::mutex> lock(m_syncMutex);
    m_syncReady = true;
    m_syncCV.notify_all();
  });
    ...
}
```
这个函数主要就是在 js 的线程队列中初始化 `NativeToJsBridge`，上面也提高了 **`NativeToJsBridge` 类是 Java 调用 JS 的桥梁，用来调用 JS Module，回调 Java**

```c++
// NativeToJsBridge.cpp
NativeToJsBridge::NativeToJsBridge(
    JSExecutorFactory* jsExecutorFactory,
    std::shared_ptr<ModuleRegistry> registry,
    std::shared_ptr<MessageQueueThread> jsQueue,
    std::shared_ptr<InstanceCallback> callback)
    : m_destroyed(std::make_shared<bool>(false))
    , m_delegate(std::make_shared<JsToNativeBridge>(registry, callback)) // 初始化 JsToNativeBrdige 打通 js 调用 native 的桥梁
    , m_executor(jsExecutorFactory->createJSExecutor(m_delegate, jsQueue)) // js 的执行器
    , m_executorMessageQueueThread(std::move(jsQueue)) // js 线程队列
    {}
```
`NativeToJsBridge` 的构造函数主要是初始化了通信所需的关键类

`m_delegate` 是 `JsToNativeBridge`，用于 JS 调用 Native 函数，和 NativeToJsBridge 一起作为连接 java 和 js 通信的桥梁

`m_executor` 是对应于 `JSCExecutor` 对象，JSCExecutor 构造函数中对 js 的执行环境进行初始化，并且向 JavaScriptCore 中注册了几个 c++ 的方法供 js 端调用

到这里 initializeBridge 整个函数就全部介绍完毕了, 总结一句就是在 `ReactInstanceManager` 的 `createReactContext` 函数里初始化 `CatalystInstanceImpl` 时，会通过 JNI 在 Native 层中初始化 Java 与 JS 通信所需的关键类，将通信环境搭建完成。

### 加载 JS Bundle
继续查看 `createReactContext` 的源码，看到在 `CatalystInstanceImpl` 初始化之后会接着调用 `CatalystInstanceImpl` 的 `runJSBundle` 方法。通过函数的名字可以直接这个方法将会真正的去加载 JS Bundle 文件。

查看 `runJSBundle` 的源码发现最终是走到了 `JSBundleLoader` 的 `loadScript` 函数里。 `JSBundleLoader` 上面介绍过，是来管理 JS Bundle 的加载方式，不管采用哪种加载方式，最终都是回到`CatalystInstanceImpl` 进行处理。

```Java
private native void jniLoadScriptFromAssets(AssetManager assetManager, String assetURL, boolean loadSynchronously);
private native void jniLoadScriptFromFile(String fileName, String sourceURL, boolean loadSynchronously);
private native void jniLoadScriptFromDeltaBundle(String sourceURL, NativeDeltaClient deltaClient, boolean loadSynchronously);
```

可以看到 `CatalystInstanceImpl` 中对应上面提到的三种加载 JS Bundle 的方式，而都是在 Native 层进行处理。

仅需进行代码追踪，会发现三种方法都是在 `NativeToJsBridge` 中进行了统一处理

```c++
// NativeToJsBridge.cpp
void NativeToJsBridge::loadApplication(
    std::unique_ptr<RAMBundleRegistry> bundleRegistry,
    std::unique_ptr<const JSBigString> startupScript,
    std::string startupScriptSourceURL) {
  runOnExecutorQueue( // js 线程队列
      [bundleRegistryWrap=folly::makeMoveWrapper(std::move(bundleRegistry)),
       startupScript=folly::makeMoveWrapper(std::move(startupScript)),
       startupScriptSourceURL=std::move(startupScriptSourceURL)]
        (JSExecutor* executor) mutable {
    auto bundleRegistry = bundleRegistryWrap.move();
    if (bundleRegistry) {
      executor->setBundleRegistry(std::move(bundleRegistry));
    }
    executor->loadApplicationScript(std::move(*startupScript),
                                    std::move(startupScriptSourceURL));
  });
}
```
executor 对应的 `JSCExecutor` 在上面已经说过, 是 JS 的执行器。

```c++
// JSCExecutor.cpp
void JSCExecutor::loadApplicationScript(std::unique_ptr<const JSBigString> script, std::string sourceURL) {
    ...
    //JavaScriptCore函数，执行js代码
    evaluateScript(m_context, jsScript, jsSourceURL);

    ...
    flush();
    ...
  }
}
```
`loadApplicationScript` 函数代码较多，但是最核心的就是 `flush` 函数

```c++
void JSCExecutor::flush() {
  SystraceSection s("JSCExecutor::flush");

  if (m_flushedQueueJS) {
    callNativeModules(m_flushedQueueJS->callAsFunction({}));
    return;
  }

  // When a native module is called from JS, BatchedBridge.enqueueNativeCall()
  // is invoked.  For that to work, require('BatchedBridge') has to be called,
  // and when that happens, __fbBatchedBridge is set as a side effect.
  auto global = Object::getGlobalObject(m_context);
  auto batchedBridgeValue = global.getProperty("__fbBatchedBridge");
  // So here, if __fbBatchedBridge doesn't exist, then we know no native calls
  // have happened, and we were able to determine this without forcing
  // BatchedBridge to be loaded as a side effect.
  if (!batchedBridgeValue.isUndefined()) {
    // If calls were made, we bind to the JS bridge methods, and use them to
    // get the pending queue of native calls.
    bindBridge();
    callNativeModules(m_flushedQueueJS->callAsFunction({}));
  } else if (m_delegate) {
    // If we have a delegate, we need to call it; we pass a null list to
    // callNativeModules, since we know there are no native calls, without
    // calling into JS again.  If no calls were made and there's no delegate,
    // nothing happens, which is correct.
    callNativeModules(Value::makeNull(m_context));
  }
}
```
`flush` 函数主要就是检查是否已经加载过 js bundle，建立了连接桥梁。如果没有就调用 `bindBridge` 进行连接。

```c++
void JSCExecutor::bindBridge() throw(JSException) {
  SystraceSection s("JSCExecutor::bindBridge");
  std::call_once(m_bindFlag, [this] {
      // 获取 js 中的 global 对象
    auto global = Object::getGlobalObject(m_context);

      // 获取存储在 global 中的 MessageQueue 对象
    auto batchedBridgeValue = global.getProperty("__fbBatchedBridge");
    if (batchedBridgeValue.isUndefined()) {
      auto requireBatchedBridge =
          global.getProperty("__fbRequireBatchedBridge");
      if (!requireBatchedBridge.isUndefined()) {
        batchedBridgeValue = requireBatchedBridge.asObject().callAsFunction({});
      }
      if (batchedBridgeValue.isUndefined()) {
        throw JSException(
            "Could not get BatchedBridge, make sure your bundle is packaged correctly");
      }
    }

      // 在 native 中保存 MessageQueue 关键的函数对象
    auto batchedBridge = batchedBridgeValue.asObject();
    m_callFunctionReturnFlushedQueueJS =
        batchedBridge.getProperty("callFunctionReturnFlushedQueue").asObject();
    m_invokeCallbackAndReturnFlushedQueueJS =
        batchedBridge.getProperty("invokeCallbackAndReturnFlushedQueue")
            .asObject();
    m_flushedQueueJS = batchedBridge.getProperty("flushedQueue").asObject();
    m_callFunctionReturnResultAndFlushedQueueJS =
        batchedBridge.getProperty("callFunctionReturnResultAndFlushedQueue")
            .asObject();
  });
}
```

这个函数主要实现以下功能:
1、从 js 执行环境中取出全局变量 fbBatchedBridge 放到 global 变量中
2、将 global 中某些特定的函数对象映射到 C++ 对象中，这样我们就可以通过 C++ 对象调用 js 的代码，假设我们想要调用 js 端 fbBatchedBridge 的 flushQueue 方法，在 C++ 中就可以使用 m_flushedQueueJS->callAsFunction() 就可以实现，那么 fbBatchedBridge 在 js 端到底是个什么东西那？

查看 `BatchBridge.js` 中可以找到 `__fbBatchedBridge` 的定义
```js
const MessageQueue = require('MessageQueue');

const BatchedBridge = new MessageQueue();

Object.defineProperty(global, '__fbBatchedBridge', {
  configurable: true,
  value: BatchedBridge,
});

module.exports = BatchedBridge;
```

可以看出 `__fbBatchedBridge` 指的就是 JS 中的 MessageQueue 对象，这样就实现了 Android 端的消息队列和 JS 端消息队列的连通。

### 总结
对上面的源码流程做了个核心方法的调用流程图

![](https://user-gold-cdn.xitu.io/2018/7/9/1647e3812eeeb897?w=1071&h=1387&f=png&s=41731)

